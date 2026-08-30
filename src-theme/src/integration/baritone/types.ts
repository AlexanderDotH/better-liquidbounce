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
