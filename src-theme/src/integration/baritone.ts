export const BARITONE_PATH_LIMIT = 512;
export const BARITONE_LOG_LIMIT = 200;

export const BARITONE_TABS = [
    {id: "overview", label: "Overview"},
    {id: "navigate", label: "Navigate"},
    {id: "mine", label: "Mine"},
    {id: "follow", label: "Follow"},
    {id: "farm", label: "Farm"},
    {id: "explore", label: "Explore"},
    {id: "build", label: "Build"},
    {id: "elytra", label: "Elytra"},
    {id: "waypoints", label: "Waypoints"},
    {id: "settings", label: "Settings"},
    {id: "console", label: "Advanced Console"},
] as const;

export type BaritoneTabId = typeof BARITONE_TABS[number]["id"];
export type BaritoneAvailability = "AVAILABLE" | "UNAVAILABLE";
export type BaritoneStatus =
    | "UNAVAILABLE"
    | "NO_WORLD"
    | "IDLE"
    | "CALCULATING"
    | "PATHING"
    | "PAUSED"
    | "FAILED"
    | "ARRIVED";
export type BaritoneControlAction = "PAUSE" | "RESUME" | "CANCEL";
export type BaritoneLocomotion = "FLY" | "WALK";
export type BaritoneNavigationPhase =
    | "IDLE"
    | "WAITING_FOR_PATH"
    | "PLANNING"
    | "ARMING"
    | "FLYING"
    | "WALK_FALLBACK"
    | "WAITING_FOR_USER";
export type BaritoneFlyOwnership = "BARITONE" | "USER";
export type BaritoneTaskType =
    | "GOTO"
    | "GET_TO_BLOCK"
    | "MINE"
    | "FOLLOW"
    | "FARM"
    | "EXPLORE"
    | "BUILD"
    | "ELYTRA";
export type BaritoneSettingType =
    | "BOOLEAN"
    | "INTEGER"
    | "LONG"
    | "FLOAT"
    | "DOUBLE"
    | "STRING"
    | "STRING_LIST"
    | "ENUM";
export type BaritoneSettingValue = boolean | number | string | string[];

export interface BaritonePoint {
    x: number;
    y: number;
    z: number;
}

export interface BaritoneRoute {
    revision: number;
    points: BaritonePoint[];
}

export interface BaritoneTaskSummary {
    type: BaritoneTaskType;
    label: string;
    details?: string;
}

export interface BaritoneWaypoint {
    id: string;
    name: string;
    tag?: string;
    position: BaritonePoint;
}

export interface BaritoneSetting {
    name: string;
    type: BaritoneSettingType;
    value: BaritoneSettingValue;
    defaultValue: BaritoneSettingValue;
    description: string;
    mutable: boolean;
    options?: string[];
}

export interface BaritoneLogEntry {
    revision: number;
    level: "DEBUG" | "INFO" | "WARNING" | "ERROR";
    message: string;
    timestamp: string;
}

export interface BaritoneNavigation {
    requested: BaritoneLocomotion;
    active: BaritoneLocomotion | null;
    phase: BaritoneNavigationPhase;
    flyMode: string | null;
    ownership: BaritoneFlyOwnership | null;
    detail: string | null;
    restartsRemaining: number;
}

export interface BaritoneSnapshot {
    revision: number;
    availability: BaritoneAvailability;
    status: BaritoneStatus;
    task: BaritoneTaskSummary | null;
    etaSeconds: number | null;
    progress: number | null;
    pauseReason: string | null;
    settings: BaritoneSetting[];
    waypoints: BaritoneWaypoint[];
    logs: BaritoneLogEntry[];
    failure?: string | null;
    /** Optional only for compatibility with snapshots produced before Fly navigation existed. */
    navigation?: BaritoneNavigation;
}

export interface BaritoneViewState {
    snapshot: BaritoneSnapshot;
    route: BaritoneRoute;
    stateRevision: number;
    routeRevision: number;
    logRevision: number;
}

export interface BaritoneStateEvent {
    revision: number;
    snapshot: BaritoneSnapshot;
}

export interface BaritoneRouteEvent {
    revision: number;
    route: BaritoneRoute;
}

export interface BaritoneLogEvent {
    revision: number;
    entry: BaritoneLogEntry;
}

export type BaritoneEventName =
    | "socketReady"
    | "baritoneState"
    | "baritoneRoute"
    | "baritoneLog";

export interface BaritoneEventMap {
    socketReady: void;
    baritoneState: BaritoneStateEvent;
    baritoneRoute: BaritoneRouteEvent;
    baritoneLog: BaritoneLogEvent;
}

export type BaritoneSubscribe = <Name extends BaritoneEventName>(
    eventName: Name,
    listener: (event: BaritoneEventMap[Name]) => void | Promise<void>,
) => void | (() => void);

export type BaritoneTaskRequest = {
    type: BaritoneTaskType;
    [field: string]: string | number | boolean | BaritoneTaskType | undefined;
};

export interface BaritoneWaypointRequest {
    name: string;
    tag?: string;
    x: number;
    y: number;
    z: number;
}

export interface BaritoneCommandResult {
    accepted: boolean;
    output?: string;
}

export interface BaritoneRestClient {
    getSnapshot(): Promise<BaritoneSnapshot>;
    getRoute(): Promise<BaritoneRoute>;
    putTask(task: BaritoneTaskRequest): Promise<void>;
    control(action: BaritoneControlAction): Promise<void>;
    getSetting(name: string): Promise<BaritoneSetting>;
    updateSetting(name: string, value: BaritoneSettingValue): Promise<void>;
    resetSetting(name: string): Promise<void>;
    resetSettings(): Promise<void>;
    getWaypoints(): Promise<BaritoneWaypoint[]>;
    addWaypoint(waypoint: BaritoneWaypointRequest): Promise<void>;
    deleteWaypoint(id: string): Promise<void>;
    command(command: string): Promise<BaritoneCommandResult>;
    completions(input: string): Promise<string[]>;
}

export class BaritoneRestError extends Error {
    readonly status: number;
    readonly code: string;
    readonly field?: string;

    constructor(status: number, code: string, message: string, field?: string) {
        super(message);
        this.name = "BaritoneRestError";
        this.status = status;
        this.code = code;
        this.field = field;
    }
}

const EMPTY_SNAPSHOT: BaritoneSnapshot = {
    revision: 0,
    availability: "UNAVAILABLE",
    status: "UNAVAILABLE",
    task: null,
    etaSeconds: null,
    progress: null,
    pauseReason: null,
    settings: [],
    waypoints: [],
    logs: [],
    navigation: defaultNavigation(),
};

export function createInitialBaritoneViewState(
    snapshot?: BaritoneSnapshot,
): BaritoneViewState {
    const initialSnapshot = snapshot ?? EMPTY_SNAPSHOT;
    return {
        snapshot: cloneSnapshot(initialSnapshot),
        route: {revision: 0, points: []},
        stateRevision: snapshot ? snapshot.revision : -1,
        routeRevision: -1,
        logRevision: snapshot ? latestLogRevision(snapshot.logs) : -1,
    };
}

export function applyBaritoneStateEvent(
    state: BaritoneViewState,
    event: BaritoneStateEvent,
): BaritoneViewState {
    if (!isNewerRevision(event.revision, state.stateRevision)) {
        return state;
    }

    const snapshot = cloneSnapshot(event.snapshot);
    snapshot.revision = Math.max(snapshot.revision, event.revision);
    return {
        ...state,
        snapshot,
        stateRevision: event.revision,
        logRevision: Math.max(state.logRevision, latestLogRevision(snapshot.logs)),
    };
}

export function applyBaritoneRouteEvent(
    state: BaritoneViewState,
    event: BaritoneRouteEvent,
): BaritoneViewState {
    if (!isNewerRevision(event.revision, state.routeRevision)) {
        return state;
    }

    return {
        ...state,
        route: {
            revision: Math.max(event.route.revision, event.revision),
            points: limitRoutePoints(event.route.points),
        },
        routeRevision: event.revision,
    };
}

export function applyBaritoneLogEvent(
    state: BaritoneViewState,
    event: BaritoneLogEvent,
): BaritoneViewState {
    if (!isNewerRevision(event.revision, state.logRevision)) {
        return state;
    }

    const entry = {...event.entry, revision: Math.max(event.entry.revision, event.revision)};
    const logs = [...state.snapshot.logs, entry].slice(-BARITONE_LOG_LIMIT);
    return {
        ...state,
        snapshot: {...state.snapshot, logs},
        logRevision: event.revision,
    };
}

export function limitRoutePoints(points: readonly BaritonePoint[]): BaritonePoint[] {
    if (points.length <= BARITONE_PATH_LIMIT) {
        return points.map(point => ({...point}));
    }

    const lastIndex = points.length - 1;
    return Array.from({length: BARITONE_PATH_LIMIT}, (_, index) => {
        const sourceIndex = Math.round(index * lastIndex / (BARITONE_PATH_LIMIT - 1));
        return {...points[sourceIndex]};
    });
}

export function filterBaritoneSettings(
    settings: readonly BaritoneSetting[],
    query: string,
): BaritoneSetting[] {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    if (!normalizedQuery) {
        return [...settings];
    }

    return settings.filter(setting => {
        const searchText = `${setting.name} ${setting.description}`.toLocaleLowerCase();
        return searchText.includes(normalizedQuery);
    });
}

export function coerceBaritoneSettingValue(
    setting: BaritoneSetting,
    rawValue: string | boolean,
): BaritoneSettingValue {
    if (setting.type === "BOOLEAN") {
        return coerceBoolean(rawValue);
    }

    if (setting.type === "INTEGER" || setting.type === "LONG") {
        return coerceInteger(rawValue);
    }

    if (setting.type === "FLOAT" || setting.type === "DOUBLE") {
        return coerceNumber(rawValue);
    }

    if (setting.type === "STRING_LIST") {
        return String(rawValue)
            .split(/[,\n]/)
            .map(value => value.trim())
            .filter(Boolean);
    }

    const value = String(rawValue);
    if (setting.type === "ENUM" && !setting.options?.includes(value)) {
        throw new Error(`${setting.name} must be one of its supported values.`);
    }

    return value;
}

export function createBaritoneDataSource({
    client,
    subscribe,
    onChange,
}: {
    client: Pick<BaritoneRestClient, "getSnapshot" | "getRoute">;
    subscribe: BaritoneSubscribe;
    onChange: (state: BaritoneViewState) => void;
}) {
    let state = createInitialBaritoneViewState();
    let refreshGeneration = 0;

    const update = (nextState: BaritoneViewState) => {
        if (nextState === state) {
            return;
        }
        state = nextState;
        onChange(state);
    };

    const refresh = async () => {
        const generation = ++refreshGeneration;
        const [snapshot, route] = await Promise.all([
            client.getSnapshot(),
            client.getRoute(),
        ]);
        if (generation !== refreshGeneration) {
            return;
        }

        let nextState = applyBaritoneStateEvent(state, {
            revision: snapshot.revision,
            snapshot,
        });
        nextState = applyBaritoneRouteEvent(nextState, {
            revision: route.revision,
            route,
        });
        update(nextState);
    };

    const disposers = [
        subscribe("socketReady", refresh),
        subscribe("baritoneState", event => update(applyBaritoneStateEvent(state, event))),
        subscribe("baritoneRoute", event => update(applyBaritoneRouteEvent(state, event))),
        subscribe("baritoneLog", event => update(applyBaritoneLogEvent(state, event))),
    ].filter((dispose): dispose is () => void => typeof dispose === "function");

    return {
        refresh,
        getState: () => state,
        stop: () => disposers.forEach(dispose => dispose()),
    };
}

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

function isNewerRevision(nextRevision: number, currentRevision: number): boolean {
    return Number.isFinite(nextRevision) && nextRevision > currentRevision;
}

function cloneSnapshot(snapshot: BaritoneSnapshot): BaritoneSnapshot {
    return {
        ...snapshot,
        navigation: snapshot.navigation ? {...snapshot.navigation} : defaultNavigation(),
        settings: snapshot.settings.map(setting => ({
            ...setting,
            options: setting.options ? [...setting.options] : undefined,
        })),
        waypoints: snapshot.waypoints.map(waypoint => ({
            ...waypoint,
            position: {...waypoint.position},
        })),
        logs: snapshot.logs.map(entry => ({...entry})),
        task: snapshot.task ? {...snapshot.task} : null,
    };
}

function defaultNavigation(): BaritoneNavigation {
    return {
        requested: "FLY",
        active: null,
        phase: "IDLE",
        flyMode: null,
        ownership: null,
        detail: null,
        restartsRemaining: 3,
    };
}

function latestLogRevision(logs: readonly BaritoneLogEntry[]): number {
    return logs.reduce((latest, entry) => Math.max(latest, entry.revision), 0);
}

function coerceBoolean(rawValue: string | boolean): boolean {
    if (typeof rawValue === "boolean") {
        return rawValue;
    }

    const normalizedValue = rawValue.trim().toLocaleLowerCase();
    if (normalizedValue === "true") {
        return true;
    }
    if (normalizedValue === "false") {
        return false;
    }
    throw new Error("Boolean settings must be true or false.");
}

function coerceInteger(rawValue: string | boolean): number {
    const value = Number(rawValue);
    if (Number.isFinite(value) && Number.isInteger(value)) {
        return value;
    }
    throw new Error("This setting requires a whole number.");
}

function coerceNumber(rawValue: string | boolean): number {
    const value = Number(rawValue);
    if (Number.isFinite(value)) {
        return value;
    }
    throw new Error("This setting requires a finite number.");
}
