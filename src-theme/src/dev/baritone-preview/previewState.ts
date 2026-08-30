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

export const SETTINGS: BaritoneSetting[] = [
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
