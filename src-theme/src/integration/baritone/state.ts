import type {
    BaritoneLogEntry,
    BaritoneLogEvent,
    BaritoneNavigation,
    BaritonePoint,
    BaritoneRouteEvent,
    BaritoneSnapshot,
    BaritoneStateEvent,
    BaritoneViewState,
} from "./types";
import {BARITONE_LOG_LIMIT, BARITONE_PATH_LIMIT} from "./types.ts";

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
