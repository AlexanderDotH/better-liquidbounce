import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
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
import {
    BARITONE_PREVIEW_FIXTURES,
    createBaritonePreviewState,
    routeBaritonePreviewRequest,
} from "../src/dev/baritone-preview/previewFixture.ts";

const themeRoot = new URL("../", import.meta.url);

function source(relativePath) {
    return readFileSync(new URL(relativePath, themeRoot), "utf8");
}

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

test("preview fixtures cover every lifecycle state and route all primary mutations", async () => {
    assert.deepEqual(Object.keys(BARITONE_PREVIEW_FIXTURES), [
        "unavailable",
        "noWorld",
        "idle",
        "calculating",
        "pathing",
        "paused",
        "failed",
        "arrived",
    ]);

    const state = createBaritonePreviewState("pathing");
    const controlResponse = await routeBaritonePreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/baritone/control", {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({action: "PAUSE"}),
        }),
    );
    const routeResponse = await routeBaritonePreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/baritone/route"),
    );

    assert.equal(controlResponse.status, 200);
    assert.equal(state.snapshot.status, "PAUSED");
    assert.ok((await routeResponse.json()).points.length > 0);
});

test("preview controls pause, resume, and cancel without keeping stale route data", async () => {
    const state = createBaritonePreviewState("pathing");
    const control = action => routeBaritonePreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/baritone/control", {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({action}),
        }),
    );

    assert.equal((await control("PAUSE")).status, 200);
    assert.equal(state.snapshot.status, "PAUSED");
    assert.equal((await control("RESUME")).status, 200);
    assert.equal(state.snapshot.status, "PATHING");
    assert.equal((await control("CANCEL")).status, 200);
    assert.equal(state.snapshot.status, "IDLE");
    assert.equal(state.snapshot.task, null);
    assert.deepEqual(state.route.points, []);
});

test("preview accepts every task kind and rejects unavailable or no-world mutations", async () => {
    const taskTypes = ["GOTO", "GET_TO_BLOCK", "MINE", "FOLLOW", "FARM", "EXPLORE", "BUILD", "ELYTRA"];
    for (const type of taskTypes) {
        const state = createBaritonePreviewState("idle");
        const response = await routeBaritonePreviewRequest(
            state,
            new Request("http://preview.local/api/v1/client/baritone/task", {
                method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({type, x: 1, y: 64, z: 2, block: "minecraft:stone", player: "Alex"}),
            }),
        );
        assert.equal(response.status, 200, type);
        assert.equal(state.snapshot.task.type, type);
    }

    const unavailable = createBaritonePreviewState("unavailable");
    const unavailableResponse = await routeBaritonePreviewRequest(
        unavailable,
        new Request("http://preview.local/api/v1/client/baritone/task", {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({type: "GOTO", x: 1, y: 64, z: 2}),
        }),
    );
    assert.equal(unavailableResponse.status, 503);

    const noWorld = createBaritonePreviewState("noWorld");
    const noWorldResponse = await routeBaritonePreviewRequest(
        noWorld,
        new Request("http://preview.local/api/v1/client/baritone/control", {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({action: "PAUSE"}),
        }),
    );
    assert.equal(noWorldResponse.status, 409);
});

test("preview settings, waypoints, and console follow the production contracts", async () => {
    const state = createBaritonePreviewState("idle");
    const request = (path, method = "GET", body) => routeBaritonePreviewRequest(
        state,
        new Request(`http://preview.local/api/v1/client/baritone${path}`, {
            method,
            headers: body === undefined ? undefined : {"Content-Type": "application/json"},
            body: body === undefined ? undefined : JSON.stringify(body),
        }),
    );

    assert.equal((await request("/settings/allowSprint", "PUT", {value: false})).status, 200);
    assert.equal(state.snapshot.settings.find(setting => setting.name === "allowSprint").value, false);
    assert.equal((await request("/settings/allowSprint", "DELETE")).status, 200);
    assert.equal(state.snapshot.settings.find(setting => setting.name === "allowSprint").value, true);
    assert.equal((await request("/settings/chatControl", "PUT", {value: true})).status, 409);

    const addWaypoint = await request("/waypoints", "POST", {name: "Mine", tag: "USER", x: 4, y: 12, z: -8});
    const waypoint = await addWaypoint.json();
    assert.equal(addWaypoint.status, 201);
    assert.equal((await request("/waypoints", "DELETE", {id: waypoint.id})).status, 204);

    const completions = await request("/completions?input=go");
    assert.deepEqual(await completions.json(), ["goal", "goto"]);
    const command = await request("/command", "POST", {command: "goto 4 12 -8"});
    assert.deepEqual(await command.json(), {accepted: true, output: "Command accepted by preview."});
    assert.match(state.snapshot.logs.at(-1).message, /> goto 4 12 -8/);
});

test("the dashboard is accessible, responsive, and routes every editable field through CEF", () => {
    const dashboard = source("src/routes/baritone/BaritoneDashboard.svelte");
    const field = source("src/routes/baritone/BaritoneField.svelte");
    const routeMap = source("src/routes/baritone/BaritoneRouteMap.svelte");

    assert.match(dashboard, /role="tablist"/);
    assert.match(dashboard, /role="tabpanel"/);
    assert.match(dashboard, /aria-live="polite"/);
    assert.match(dashboard, /Pause/);
    assert.match(dashboard, /Resume/);
    assert.match(dashboard, /Cancel/);
    assert.match(dashboard, /Navigation mode/);
    assert.match(dashboard, /Navigation state/);
    assert.match(dashboard, /Retries remaining/);
    assert.match(dashboard, /Active flight route/);
    assert.match(dashboard, /Active walking route/);
    assert.match(dashboard, /@media\s*\(max-width:/);
    assert.match(dashboard, /@media\s*\(prefers-reduced-motion:\s*reduce\)/);
    assert.match(field, /use:cefTextInput=/);
    assert.match(field, /screenNames:\s*\["baritone"\]/);
    assert.match(routeMap, /<svg/);
    assert.match(routeMap, /role="img"/);
});
