import assert from "node:assert/strict";
import test from "node:test";

import {
    BARITONE_PREVIEW_FIXTURES,
    createBaritonePreviewState,
    routeBaritonePreviewRequest,
} from "../src/dev/baritone-preview/previewFixture.ts";

test("preview fixtures cover every lifecycle state and route all primary mutations", async () => {
    assert.deepEqual(Object.keys(BARITONE_PREVIEW_FIXTURES), [
        "unavailable", "noWorld", "idle", "calculating", "pathing", "paused", "failed", "arrived",
    ]);
    const state = createBaritonePreviewState("pathing");
    const controlResponse = await routeBaritonePreviewRequest(
        state,
        previewRequest("/control", "PUT", {action: "PAUSE"}),
    );
    const routeResponse = await routeBaritonePreviewRequest(state, previewRequest("/route"));

    assert.equal(controlResponse.status, 200);
    assert.equal(state.snapshot.status, "PAUSED");
    assert.ok((await routeResponse.json()).points.length > 0);
});

test("preview controls pause, resume, and cancel without keeping stale route data", async () => {
    const state = createBaritonePreviewState("pathing");
    const control = action => routeBaritonePreviewRequest(
        state,
        previewRequest("/control", "PUT", {action}),
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
            previewRequest("/task", "PUT", {type, x: 1, y: 64, z: 2, block: "minecraft:stone", player: "Alex"}),
        );
        assert.equal(response.status, 200, type);
        assert.equal(state.snapshot.task.type, type);
    }

    const unavailable = createBaritonePreviewState("unavailable");
    const unavailableResponse = await routeBaritonePreviewRequest(
        unavailable,
        previewRequest("/task", "PUT", {type: "GOTO", x: 1, y: 64, z: 2}),
    );
    assert.equal(unavailableResponse.status, 503);

    const noWorld = createBaritonePreviewState("noWorld");
    const noWorldResponse = await routeBaritonePreviewRequest(
        noWorld,
        previewRequest("/control", "PUT", {action: "PAUSE"}),
    );
    assert.equal(noWorldResponse.status, 409);
});

test("preview settings, waypoints, and console follow the production contracts", async () => {
    const state = createBaritonePreviewState("idle");
    const request = (path, method = "GET", body) => routeBaritonePreviewRequest(
        state,
        previewRequest(path, method, body),
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

function previewRequest(path, method = "GET", body) {
    return new Request(`http://preview.local/api/v1/client/baritone${path}`, {
        method,
        headers: body === undefined ? undefined : {"Content-Type": "application/json"},
        body: body === undefined ? undefined : JSON.stringify(body),
    });
}
