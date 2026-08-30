import type {BaritoneTaskRequest, BaritoneWaypointRequest} from "../../integration/baritone";
import type {BaritonePreviewMutation, BaritonePreviewState} from "./previewState";
import {SETTINGS} from "./previewState.ts";
import {addWaypoint, commandCompletions, deleteWaypoint, error, json, publishState, runCommand, updateControl, updateSetting, updateTask} from "./previewMutations.ts";

export async function routeBaritonePreviewRequest(
    state: BaritonePreviewState,
    request: Request,
): Promise<Response> {
    const url = new URL(request.url);
    const route = normalizedRoute(request.method, url.pathname);
    const response = await routeBaritoneRead(state, url, route)
        ?? await routeBaritoneWrite(state, request, route)
        ?? await routeDynamicBaritonePath(state, request, url)
        ?? await routeClientInterop(state, request, route);
    return response ?? error(404, "NOT_FOUND", `No preview route for ${request.method} ${url.pathname}.`);
}

function routeBaritoneRead(state: BaritonePreviewState, url: URL, route: string): Response | null {
    switch (route) {
        case "GET /baritone/snapshot": return json(state.snapshot);
        case "GET /baritone/route": return json(state.route);
        case "GET /baritone/waypoints": return json(state.snapshot.waypoints);
        case "GET /baritone/completions":
            return json(commandCompletions(url.searchParams.get("input") ?? ""));
        default: return null;
    }
}

async function routeBaritoneWrite(
    state: BaritonePreviewState,
    request: Request,
    route: string,
): Promise<Response | null> {
    switch (route) {
        case "PUT /baritone/task":
            return updateTask(state, await request.json() as BaritoneTaskRequest);
        case "PUT /baritone/control":
            return updateControl(state, (await request.json() as {action?: string}).action);
        case "POST /baritone/settings/reset": return resetSettings(state);
        case "POST /baritone/waypoints":
            return addWaypoint(state, await request.json() as BaritoneWaypointRequest);
        case "DELETE /baritone/waypoints":
            return deleteWaypoint(state, (await request.json() as {id?: string}).id ?? "");
        case "POST /baritone/command":
            return runCommand(state, (await request.json() as {command?: string}).command ?? "");
        default: return null;
    }
}

function resetSettings(state: BaritonePreviewState): Response {
    state.snapshot.settings = structuredClone(SETTINGS);
    publishState(state);
    return json(state.snapshot.settings);
}

function routeDynamicBaritonePath(
    state: BaritonePreviewState,
    request: Request,
    url: URL,
): Promise<Response> | Response | null {
    if (url.pathname.includes("/baritone/settings/")) {
        return updateSetting(state, request, lastPathSegment(url));
    }
    if (request.method === "DELETE" && url.pathname.includes("/baritone/waypoints/")) {
        return deleteWaypoint(state, lastPathSegment(url));
    }
    return null;
}

async function routeClientInterop(
    state: BaritonePreviewState,
    request: Request,
    route: string,
): Promise<Response | null> {
    switch (route) {
        case "GET /client/clipboard": return json({text: state.clipboard});
        case "PUT /client/clipboard":
            state.clipboard = (await request.json() as {text?: string}).text ?? "";
            return new Response(null, {status: 204});
        case "PUT /client/typing":
            state.typing = (await request.json() as {typing?: boolean}).typing === true;
            return new Response(null, {status: 204});
        default: return null;
    }
}

function lastPathSegment(url: URL): string {
    return decodeURIComponent(url.pathname.split("/").at(-1) ?? "");
}

function normalizedRoute(method: string, path: string): string {
    const baritone = path.lastIndexOf("/baritone/");
    if (baritone >= 0) return `${method} ${path.slice(baritone)}`;
    const client = path.lastIndexOf("/client/");
    return `${method} ${client >= 0 ? path.slice(client) : path}`;
}

export function drainBaritonePreviewMutations(
    state: BaritonePreviewState,
): BaritonePreviewMutation[] {
    return state.mutations.splice(0, state.mutations.length);
}
