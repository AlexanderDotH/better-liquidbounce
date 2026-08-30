import type {BaritoneLogEntry, BaritoneSetting, BaritoneStateEvent, BaritoneTaskRequest, BaritoneWaypoint, BaritoneWaypointRequest} from "../../integration/baritone";
import type {BaritonePreviewState} from "./previewState";

export function updateTask(state: BaritonePreviewState, task: BaritoneTaskRequest): Response {
    if (state.snapshot.availability !== "AVAILABLE") {
        return error(503, "BARITONE_UNAVAILABLE", "Baritone is not available in this fixture.");
    }

    state.snapshot.status = "CALCULATING";
    state.snapshot.task = {type: task.type, label: taskLabel(task)};
    state.snapshot.progress = 0;
    state.snapshot.etaSeconds = null;
    state.snapshot.pauseReason = null;
    publishState(state);
    return json(state.snapshot);
}

export function updateControl(state: BaritonePreviewState, action: string | undefined): Response {
    if (state.snapshot.status === "NO_WORLD") {
        return error(409, "NO_WORLD", "Join a world before controlling Baritone.");
    }
    if (action === "PAUSE") {
        state.snapshot.status = "PAUSED";
        state.snapshot.pauseReason = "Paused from dashboard";
    } else if (action === "RESUME") {
        state.snapshot.status = state.snapshot.task ? "PATHING" : "IDLE";
        state.snapshot.pauseReason = null;
    } else if (action === "CANCEL") {
        state.snapshot.status = "IDLE";
        state.snapshot.task = null;
        state.snapshot.pauseReason = null;
        state.snapshot.progress = null;
        state.snapshot.etaSeconds = null;
        state.route = {revision: state.route.revision + 1, points: []};
        publishRoute(state);
    } else {
        return error(400, "INVALID_ACTION", "Expected PAUSE, RESUME, or CANCEL.", "action");
    }

    publishState(state);
    return json(state.snapshot);
}

export async function updateSetting(
    state: BaritonePreviewState,
    request: Request,
    settingName: string,
): Promise<Response> {
    const setting = state.snapshot.settings.find(candidate => candidate.name === settingName);
    if (!setting) {
        return error(400, "UNKNOWN_SETTING", `Unknown setting ${settingName}.`, "name");
    }
    if (request.method === "GET") {
        return json(setting);
    }
    if (!setting.mutable) {
        return error(409, "SETTING_LOCKED", `${settingName} is managed by LiquidBounce.`, "name");
    }
    if (request.method === "DELETE") {
        setting.value = setting.defaultValue;
        publishState(state);
        return json(setting);
    }
    if (request.method !== "PUT") {
        return error(405, "METHOD_NOT_ALLOWED", "Unsupported settings operation.");
    }

    const body = await request.json() as {value?: BaritoneSetting["value"]};
    if (body.value === undefined) {
        return error(400, "MISSING_VALUE", "A canonical setting value is required.", "value");
    }
    setting.value = body.value;
    publishState(state);
    return json(setting);
}

export function addWaypoint(state: BaritonePreviewState, request: BaritoneWaypointRequest): Response {
    if (!request.name.trim()) {
        return error(400, "INVALID_WAYPOINT", "Waypoint name is required.", "name");
    }

    const waypoint: BaritoneWaypoint = {
        id: `preview-${state.snapshot.waypoints.length + 1}`,
        name: request.name.trim(),
        tag: request.tag,
        position: {x: request.x, y: request.y, z: request.z},
    };
    state.snapshot.waypoints.push(waypoint);
    publishState(state);
    return json(waypoint, 201);
}

export function deleteWaypoint(state: BaritonePreviewState, waypointId: string): Response {
    const previousLength = state.snapshot.waypoints.length;
    state.snapshot.waypoints = state.snapshot.waypoints.filter(waypoint => waypoint.id !== waypointId);
    if (state.snapshot.waypoints.length === previousLength) {
        return error(400, "UNKNOWN_WAYPOINT", `Unknown waypoint ${waypointId}.`, "id");
    }
    publishState(state);
    return new Response(null, {status: 204});
}

export function runCommand(state: BaritonePreviewState, command: string): Response {
    if (!command.trim()) {
        return error(400, "EMPTY_COMMAND", "Enter a Baritone command.", "command");
    }

    const entry: BaritoneLogEntry = {
        revision: nextRevision(state),
        level: "INFO",
        message: `> ${command.trim()}`,
        timestamp: new Date(0).toISOString().slice(11, 19),
    };
    state.snapshot.logs.push(entry);
    state.mutations.push({name: "baritoneLog", event: {revision: entry.revision, entry}});
    return json({accepted: true, output: "Command accepted by preview."});
}

export function publishState(state: BaritonePreviewState): void {
    state.snapshot.revision = nextRevision(state);
    const event: BaritoneStateEvent = {
        revision: state.snapshot.revision,
        snapshot: structuredClone(state.snapshot),
    };
    state.mutations.push({name: "baritoneState", event});
}

function publishRoute(state: BaritonePreviewState): void {
    state.mutations.push({
        name: "baritoneRoute",
        event: {revision: state.route.revision, route: structuredClone(state.route)},
    });
}

function nextRevision(state: BaritonePreviewState): number {
    return Math.max(state.snapshot.revision, state.route.revision, ...state.snapshot.logs.map(log => log.revision)) + 1;
}

function taskLabel(task: BaritoneTaskRequest): string {
    const coordinates = [task.x, task.y, task.z].filter(value => value !== undefined).join(" ");
    const target = task.block ?? task.player ?? task.file ?? coordinates;
    return target ? `${task.type} ${target}` : task.type;
}

export function commandCompletions(input: string): string[] {
    const commands = ["goal", "goto", "mine", "follow", "farm", "explore", "build", "elytra", "pause", "resume", "cancel"];
    const normalized = input.trim().toLocaleLowerCase();
    return commands.filter(command => command.startsWith(normalized)).slice(0, 8);
}

export function json(value: unknown, status = 200): Response {
    return Response.json(value, {status});
}

export function error(status: number, code: string, message: string, field?: string): Response {
    return json({code, message, field}, status);
}
