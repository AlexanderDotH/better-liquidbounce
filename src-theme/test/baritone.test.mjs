import assert from "node:assert/strict";
import test from "node:test";

import {
    BARITONE_PATH_LIMIT,
    BARITONE_TABS,
    applyBaritoneLogEvent,
    applyBaritoneRouteEvent,
    applyBaritoneStateEvent,
    coerceBaritoneSettingValue,
    createBaritoneDataSource,
    createBaritoneRestClient,
    createInitialBaritoneViewState,
    filterBaritoneSettings,
} from "../src/integration/baritone.ts";

function point(index) {
    return {x: index, y: 64 + index % 3, z: index * 2};
}

function snapshot(revision, status = "IDLE") {
    return {
        revision,
        availability: "AVAILABLE",
        status,
        task: null,
        etaSeconds: null,
        progress: null,
        pauseReason: null,
        settings: [],
        waypoints: [],
        logs: [],
    };
}

test("the dashboard exposes every planned workflow tab in a stable order", () => {
    assert.deepEqual(
        BARITONE_TABS.map(tab => tab.label),
        [
            "Overview",
            "Navigate",
            "Mine",
            "Follow",
            "Farm",
            "Explore",
            "Build",
            "Elytra",
            "Waypoints",
            "Settings",
            "Advanced Console",
        ],
    );
});

test("state, route, and log reducers reject stale websocket revisions", () => {
    let state = createInitialBaritoneViewState(snapshot(4));
    state = applyBaritoneStateEvent(state, {revision: 5, snapshot: snapshot(5, "PATHING")});
    const currentState = state;

    assert.equal(state.snapshot.status, "PATHING");
    assert.equal(
        applyBaritoneStateEvent(state, {revision: 4, snapshot: snapshot(4, "FAILED")}),
        currentState,
    );

    state = applyBaritoneRouteEvent(state, {revision: 8, route: {revision: 8, points: [point(0), point(1)]}});
    const currentRoute = state;
    assert.equal(
        applyBaritoneRouteEvent(state, {revision: 7, route: {revision: 7, points: [point(9)]}}),
        currentRoute,
    );

    state = applyBaritoneLogEvent(state, {
        revision: 3,
        entry: {revision: 3, level: "INFO", message: "new", timestamp: "12:00:00"},
    });
    const currentLog = state;
    assert.equal(
        applyBaritoneLogEvent(state, {
            revision: 2,
            entry: {revision: 2, level: "ERROR", message: "old", timestamp: "11:59:59"},
        }),
        currentLog,
    );
});

test("navigation state is cloned and legacy snapshots receive an idle Fly default", () => {
    const legacy = createInitialBaritoneViewState(snapshot(1));
    assert.deepEqual(legacy.snapshot.navigation, {
        requested: "FLY",
        active: null,
        phase: "IDLE",
        flyMode: null,
        ownership: null,
        detail: null,
        restartsRemaining: 3,
    });

    const navigation = {
        requested: "FLY",
        active: "WALK",
        phase: "WALK_FALLBACK",
        flyMode: "Vulcan277",
        ownership: "USER",
        detail: "No safe aerial route; walking to a landing anchor.",
        restartsRemaining: 1,
    };
    const initial = createInitialBaritoneViewState({...snapshot(2), navigation});
    navigation.detail = "mutated outside state";

    assert.equal(initial.snapshot.navigation.detail, "No safe aerial route; walking to a landing anchor.");
    assert.equal(initial.snapshot.navigation.active, "WALK");
});

test("route reduction preserves endpoints and caps oversized paths at 512 points", () => {
    const oversized = Array.from({length: 900}, (_, index) => point(index));
    const state = applyBaritoneRouteEvent(createInitialBaritoneViewState(), {
        revision: 1,
        route: {revision: 1, points: oversized},
    });

    assert.equal(BARITONE_PATH_LIMIT, 512);
    assert.equal(state.route.points.length, BARITONE_PATH_LIMIT);
    assert.deepEqual(state.route.points[0], oversized[0]);
    assert.deepEqual(state.route.points.at(-1), oversized.at(-1));

    const empty = applyBaritoneRouteEvent(state, {
        revision: 2,
        route: {revision: 2, points: []},
    });
    assert.deepEqual(empty.route.points, []);
});

test("settings coerce canonical values by declared type and filter by name or description", () => {
    const settings = [
        {name: "allowSprint", type: "BOOLEAN", value: true, defaultValue: true, description: "Use sprint", mutable: true},
        {name: "maxFallHeightNoWater", type: "INTEGER", value: 3, defaultValue: 3, description: "Safe drop", mutable: true},
        {name: "primaryTimeoutMS", type: "DOUBLE", value: 500.5, defaultValue: 500, description: "Path timeout", mutable: true},
        {name: "logger", type: "STRING", value: "chat", defaultValue: "chat", description: "Output target", mutable: false},
        {name: "acceptableThrowawayItems", type: "STRING_LIST", value: ["minecraft:dirt"], defaultValue: [], description: "Disposable blocks", mutable: true},
    ];

    assert.equal(coerceBaritoneSettingValue(settings[0], "false"), false);
    assert.equal(coerceBaritoneSettingValue(settings[1], "7"), 7);
    assert.equal(coerceBaritoneSettingValue(settings[2], "250.25"), 250.25);
    assert.equal(coerceBaritoneSettingValue(settings[3], "toast"), "toast");
    assert.deepEqual(
        coerceBaritoneSettingValue(settings[4], "minecraft:dirt, minecraft:cobblestone\nminecraft:netherrack"),
        ["minecraft:dirt", "minecraft:cobblestone", "minecraft:netherrack"],
    );
    assert.throws(() => coerceBaritoneSettingValue(settings[1], "3.2"), /whole number/i);
    assert.deepEqual(filterBaritoneSettings(settings, "safe").map(setting => setting.name), ["maxFallHeightNoWater"]);
});

test("the data source snapshots on initial connect and reconnect and drops stale events", async () => {
    let snapshotRevision = 1;
    const listeners = new Map();
    const emissions = [];
    const client = {
        getSnapshot: async () => snapshot(snapshotRevision, snapshotRevision === 1 ? "IDLE" : "ARRIVED"),
        getRoute: async () => ({revision: snapshotRevision, points: [point(snapshotRevision)]}),
    };
    const dataSource = createBaritoneDataSource({
        client,
        subscribe: (name, listener) => listeners.set(name, listener),
        onChange: state => emissions.push(state),
    });

    await dataSource.refresh();
    assert.equal(emissions.at(-1).snapshot.status, "IDLE");

    listeners.get("baritoneState")({revision: 4, snapshot: snapshot(4, "PATHING")});
    listeners.get("baritoneState")({revision: 3, snapshot: snapshot(3, "FAILED")});
    assert.equal(emissions.at(-1).snapshot.status, "PATHING");

    snapshotRevision = 5;
    await listeners.get("socketReady")();
    assert.equal(emissions.at(-1).snapshot.status, "ARRIVED");
    assert.equal(emissions.at(-1).route.revision, 5);
});

test("the REST client uses the planned endpoints and structured errors", async () => {
    const requests = [];
    const fetchMock = async (input, init = {}) => {
        requests.push({url: String(input), method: init.method ?? "GET", body: init.body});
        if (String(input).endsWith("/control")) {
            return new Response(JSON.stringify({code: "NO_WORLD", message: "Join a world"}), {
                status: 409,
                headers: {"Content-Type": "application/json"},
            });
        }
        return Response.json(snapshot(1));
    };
    const client = createBaritoneRestClient({fetch: fetchMock, baseUrl: "http://client/api/v1/client/baritone"});

    await client.getSnapshot();
    await client.putTask({type: "GOTO", x: 1, y: 64, z: 2});
    await assert.rejects(() => client.control("PAUSE"), error => {
        assert.equal(error.code, "NO_WORLD");
        assert.equal(error.status, 409);
        return true;
    });

    assert.deepEqual(requests.map(request => [request.method, new URL(request.url).pathname]), [
        ["GET", "/api/v1/client/baritone/snapshot"],
        ["PUT", "/api/v1/client/baritone/task"],
        ["PUT", "/api/v1/client/baritone/control"],
    ]);
});

test("the REST client covers canonical settings, waypoints, commands, and completions", async () => {
    const requests = [];
    const fetchMock = async (input, init = {}) => {
        const url = new URL(String(input));
        requests.push({url, method: init.method ?? "GET", body: init.body ? JSON.parse(init.body) : undefined});
        if (url.pathname.endsWith("/completions")) {
            return Response.json(["goto", "goal"]);
        }
        if (url.pathname.endsWith("/command")) {
            return Response.json({accepted: true, output: "ok"});
        }
        if (url.pathname.endsWith("/waypoints") && (init.method ?? "GET") === "GET") {
            return Response.json([]);
        }
        return Response.json({name: "allowSprint", value: true});
    };
    const client = createBaritoneRestClient({fetch: fetchMock, baseUrl: "http://client/api/v1/client/baritone"});

    await client.getSetting("allowSprint");
    await client.updateSetting("allowSprint", false);
    await client.resetSetting("allowSprint");
    await client.resetSettings();
    await client.getWaypoints();
    await client.addWaypoint({name: "Home", x: 1, y: 64, z: 2});
    await client.deleteWaypoint("home/id");
    assert.deepEqual(await client.completions("go to"), ["goto", "goal"]);
    assert.deepEqual(await client.command("goto 1 64 2"), {accepted: true, output: "ok"});

    assert.deepEqual(requests.map(request => [request.method, request.url.pathname]), [
        ["GET", "/api/v1/client/baritone/settings/allowSprint"],
        ["PUT", "/api/v1/client/baritone/settings/allowSprint"],
        ["DELETE", "/api/v1/client/baritone/settings/allowSprint"],
        ["POST", "/api/v1/client/baritone/settings/reset"],
        ["GET", "/api/v1/client/baritone/waypoints"],
        ["POST", "/api/v1/client/baritone/waypoints"],
        ["DELETE", "/api/v1/client/baritone/waypoints"],
        ["GET", "/api/v1/client/baritone/completions"],
        ["POST", "/api/v1/client/baritone/command"],
    ]);
    assert.deepEqual(requests[6].body, {id: "home/id"});
    assert.equal(requests[7].url.searchParams.get("input"), "go to");
});
