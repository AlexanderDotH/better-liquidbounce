import type {
    BaritoneLogEntry,
    BaritoneRoute,
    BaritoneSetting,
    BaritoneSnapshot,
    BaritoneStateEvent,
    BaritoneTaskRequest,
    BaritoneWaypoint,
    BaritoneWaypointRequest,
} from "../../integration/baritone";

export type BaritonePreviewFixtureName = keyof typeof BARITONE_PREVIEW_FIXTURES;

export interface BaritonePreviewMutation {
    name: "baritoneState" | "baritoneRoute" | "baritoneLog";
    event: unknown;
}

export interface BaritonePreviewState {
    snapshot: BaritoneSnapshot;
    route: BaritoneRoute;
    clipboard: string;
    typing: boolean;
    mutations: BaritonePreviewMutation[];
}

const SETTINGS: BaritoneSetting[] = [
    {
        name: "allowSprint",
        type: "BOOLEAN",
        value: true,
        defaultValue: true,
        description: "Allow sprinting while following a path.",
        mutable: true,
    },
    {
        name: "maxFallHeightNoWater",
        type: "INTEGER",
        value: 3,
        defaultValue: 3,
        description: "Maximum safe fall distance without water.",
        mutable: true,
    },
    {
        name: "costHeuristic",
        type: "DOUBLE",
        value: 3.563,
        defaultValue: 3.563,
        description: "Heuristic multiplier used while calculating paths.",
        mutable: true,
    },
    {
        name: "chatControl",
        type: "BOOLEAN",
        value: false,
        defaultValue: false,
        description: "Locked by LiquidBounce; use the dashboard or .baritone.",
        mutable: false,
    },
    {
        name: "acceptableThrowawayItems",
        type: "STRING_LIST",
        value: ["minecraft:dirt", "minecraft:cobblestone"],
        defaultValue: ["minecraft:dirt", "minecraft:cobblestone"],
        description: "Block identifiers Baritone may place and leave behind.",
        mutable: true,
    },
    {
        name: "pathingMapLoadFailureBehavior",
        type: "ENUM",
        value: "CONTINUE",
        defaultValue: "CONTINUE",
        description: "Behavior when cached pathing data cannot be read.",
        options: ["CONTINUE", "CANCEL"],
        mutable: true,
    },
];

const WAYPOINTS: BaritoneWaypoint[] = [
    {id: "home", name: "Home", tag: "HOME", position: {x: 124, y: 68, z: -48}},
    {id: "portal", name: "Nether portal", tag: "PORTAL", position: {x: 205, y: 71, z: 93}},
];

const PATHING_ROUTE: BaritoneRoute = {
    revision: 12,
    points: Array.from({length: 96}, (_, index) => ({
        x: 124 + index,
        y: 68 + Math.floor(index / 28),
        z: -48 + Math.round(Math.sin(index / 8) * 18) + index,
    })),
};

const BASE_SNAPSHOT: BaritoneSnapshot = {
    revision: 12,
    availability: "AVAILABLE",
    status: "PATHING",
    task: {type: "GOTO", label: "Go to 219 71 55", details: "95 blocks remaining"},
    etaSeconds: 18,
    progress: 0.62,
    pauseReason: null,
    settings: SETTINGS,
    waypoints: WAYPOINTS,
    logs: [
        {revision: 10, level: "INFO", message: "Path calculation completed.", timestamp: "14:32:08"},
        {revision: 11, level: "INFO", message: "Following primary path.", timestamp: "14:32:09"},
    ],
};

function fixture(
    status: BaritoneSnapshot["status"],
    overrides: Partial<BaritoneSnapshot> = {},
    route: BaritoneRoute = {revision: BASE_SNAPSHOT.revision, points: []},
) {
    return {
        snapshot: {...BASE_SNAPSHOT, status, ...overrides},
        route,
    };
}

export const BARITONE_PREVIEW_FIXTURES = {
    unavailable: fixture("UNAVAILABLE", {
        availability: "UNAVAILABLE",
        task: null,
        etaSeconds: null,
        progress: null,
        failure: "Baritone API is not available in this build.",
    }),
    noWorld: fixture("NO_WORLD", {
        availability: "AVAILABLE",
        task: null,
        etaSeconds: null,
        progress: null,
        failure: "Join a world to start pathing.",
    }),
    idle: fixture("IDLE", {task: null, etaSeconds: null, progress: null}),
    calculating: fixture("CALCULATING", {
        task: {type: "GOTO", label: "Go to 219 71 55"},
        etaSeconds: null,
        progress: 0.08,
    }),
    pathing: fixture("PATHING", {}, PATHING_ROUTE),
    paused: fixture("PAUSED", {
        pauseReason: "User movement has priority",
    }, PATHING_ROUTE),
    failed: fixture("FAILED", {
        etaSeconds: null,
        failure: "No valid path reaches the selected target.",
    }),
    arrived: fixture("ARRIVED", {
        task: {type: "GOTO", label: "Go to 219 71 55"},
        etaSeconds: 0,
        progress: 1,
    }),
} as const;

export function createBaritonePreviewState(
    fixtureName: BaritonePreviewFixtureName = "pathing",
): BaritonePreviewState {
    const selectedFixture = BARITONE_PREVIEW_FIXTURES[fixtureName];
    return {
        snapshot: structuredClone(selectedFixture.snapshot),
        route: structuredClone(selectedFixture.route),
        clipboard: "",
        typing: false,
        mutations: [],
    };
}

export async function routeBaritonePreviewRequest(
    state: BaritonePreviewState,
    request: Request,
): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === "GET" && path.endsWith("/baritone/snapshot")) {
        return json(state.snapshot);
    }
    if (request.method === "GET" && path.endsWith("/baritone/route")) {
        return json(state.route);
    }
    if (request.method === "GET" && path.endsWith("/baritone/waypoints")) {
        return json(state.snapshot.waypoints);
    }
    if (request.method === "GET" && path.endsWith("/baritone/completions")) {
        return json(commandCompletions(url.searchParams.get("input") ?? ""));
    }
    if (request.method === "PUT" && path.endsWith("/baritone/task")) {
        return updateTask(state, await request.json() as BaritoneTaskRequest);
    }
    if (request.method === "PUT" && path.endsWith("/baritone/control")) {
        const body = await request.json() as {action?: string};
        return updateControl(state, body.action);
    }
    if (request.method === "POST" && path.endsWith("/baritone/settings/reset")) {
        state.snapshot.settings = structuredClone(SETTINGS);
        publishState(state);
        return json(state.snapshot.settings);
    }
    if (path.includes("/baritone/settings/")) {
        return updateSetting(state, request, decodeURIComponent(path.split("/").at(-1) ?? ""));
    }
    if (request.method === "POST" && path.endsWith("/baritone/waypoints")) {
        return addWaypoint(state, await request.json() as BaritoneWaypointRequest);
    }
    if (request.method === "DELETE" && path.endsWith("/baritone/waypoints")) {
        const body = await request.json() as {id?: string};
        return deleteWaypoint(state, body.id ?? "");
    }
    if (request.method === "DELETE" && path.includes("/baritone/waypoints/")) {
        return deleteWaypoint(state, decodeURIComponent(path.split("/").at(-1) ?? ""));
    }
    if (request.method === "POST" && path.endsWith("/baritone/command")) {
        const body = await request.json() as {command?: string};
        return runCommand(state, body.command ?? "");
    }
    if (request.method === "GET" && path.endsWith("/client/clipboard")) {
        return json({text: state.clipboard});
    }
    if (request.method === "PUT" && path.endsWith("/client/clipboard")) {
        const body = await request.json() as {text?: string};
        state.clipboard = body.text ?? "";
        return new Response(null, {status: 204});
    }
    if (request.method === "PUT" && path.endsWith("/client/typing")) {
        const body = await request.json() as {typing?: boolean};
        state.typing = body.typing === true;
        return new Response(null, {status: 204});
    }

    return error(404, "NOT_FOUND", `No preview route for ${request.method} ${path}.`);
}

export function drainBaritonePreviewMutations(
    state: BaritonePreviewState,
): BaritonePreviewMutation[] {
    return state.mutations.splice(0, state.mutations.length);
}

function updateTask(state: BaritonePreviewState, task: BaritoneTaskRequest): Response {
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

function updateControl(state: BaritonePreviewState, action: string | undefined): Response {
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

async function updateSetting(
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

function addWaypoint(state: BaritonePreviewState, request: BaritoneWaypointRequest): Response {
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

function deleteWaypoint(state: BaritonePreviewState, waypointId: string): Response {
    const previousLength = state.snapshot.waypoints.length;
    state.snapshot.waypoints = state.snapshot.waypoints.filter(waypoint => waypoint.id !== waypointId);
    if (state.snapshot.waypoints.length === previousLength) {
        return error(400, "UNKNOWN_WAYPOINT", `Unknown waypoint ${waypointId}.`, "id");
    }
    publishState(state);
    return new Response(null, {status: 204});
}

function runCommand(state: BaritonePreviewState, command: string): Response {
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

function publishState(state: BaritonePreviewState): void {
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

function commandCompletions(input: string): string[] {
    const commands = ["goal", "goto", "mine", "follow", "farm", "explore", "build", "elytra", "pause", "resume", "cancel"];
    const normalized = input.trim().toLocaleLowerCase();
    return commands.filter(command => command.startsWith(normalized)).slice(0, 8);
}

function json(value: unknown, status = 200): Response {
    return Response.json(value, {status});
}

function error(status: number, code: string, message: string, field?: string): Response {
    return json({code, message, field}, status);
}
