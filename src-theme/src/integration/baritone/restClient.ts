import type {
    BaritoneCommandResult,
    BaritoneRestClient,
    BaritoneRoute,
    BaritoneSetting,
    BaritoneSnapshot,
    BaritoneWaypoint,
} from "./types";
import {BaritoneRestError} from "./types.ts";

export function createBaritoneRestClient({
    fetch: fetchImplementation = globalThis.fetch,
    baseUrl,
}: {
    fetch?: typeof globalThis.fetch;
    baseUrl: string;
}): BaritoneRestClient {
    const request = createJsonRequester(fetchImplementation, baseUrl.replace(/\/$/, ""));

    return {
        getSnapshot: () => request<BaritoneSnapshot>("/snapshot"),
        getRoute: () => request<BaritoneRoute>("/route"),
        putTask: async task => void await request("/task", {method: "PUT", body: task}),
        control: async action => void await request("/control", {method: "PUT", body: {action}}),
        getSetting: name => request<BaritoneSetting>(`/settings/${encodeURIComponent(name)}`),
        updateSetting: async (name, value) => void await request(`/settings/${encodeURIComponent(name)}`, {
            method: "PUT",
            body: {value},
        }),
        resetSetting: async name => void await request(`/settings/${encodeURIComponent(name)}`, {method: "DELETE"}),
        resetSettings: async () => void await request("/settings/reset", {method: "POST"}),
        getWaypoints: () => request<BaritoneWaypoint[]>("/waypoints"),
        addWaypoint: async waypoint => void await request("/waypoints", {method: "POST", body: waypoint}),
        deleteWaypoint: async id => void await request("/waypoints", {method: "DELETE", body: {id}}),
        command: command => request<BaritoneCommandResult>("/command", {method: "POST", body: {command}}),
        completions: input => request<string[]>(`/completions?${new URLSearchParams({input})}`),
    };
}

function createJsonRequester(fetchImplementation: typeof globalThis.fetch, baseUrl: string) {
    return async <ResponseBody>(
        path: string,
        options: {method?: string; body?: unknown} = {},
    ): Promise<ResponseBody> => {
        const response = await fetchImplementation(`${baseUrl}${path}`, {
            method: options.method,
            headers: options.body === undefined ? undefined : {"Content-Type": "application/json"},
            body: options.body === undefined ? undefined : JSON.stringify(options.body),
        });

        if (!response.ok) {
            throw await responseError(response);
        }
        if (response.status === 204) {
            return undefined as ResponseBody;
        }

        const text = await response.text();
        return (text ? JSON.parse(text) : undefined) as ResponseBody;
    };
}

async function responseError(response: Response): Promise<BaritoneRestError> {
    let body: {code?: string; message?: string; field?: string} = {};
    try {
        body = await response.json() as typeof body;
    } catch {
        // The status still gives the UI a useful failure when a proxy returns plain text.
    }

    return new BaritoneRestError(
        response.status,
        body.code ?? "REQUEST_FAILED",
        body.message ?? `Baritone request failed with status ${response.status}.`,
        body.field,
    );
}
