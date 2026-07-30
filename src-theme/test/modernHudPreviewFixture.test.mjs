import assert from "node:assert/strict";
import test from "node:test";

import {
    createModernHudPreviewSnapshotEvents,
    createModernHudPreviewState,
    routeModernHudPreviewRequest,
} from "../src/dev/modern-hud-preview/previewFixture.ts";

function jsonRequest(url, method, body) {
    return new Request(url, {
        method,
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body),
    });
}

test("serves every production HUD component with deterministic settings", async () => {
    const state = createModernHudPreviewState();
    const response = await routeModernHudPreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/components/liquidbounce"),
    );
    const components = await response.json();

    assert.equal(response.status, 200);
    assert.deepEqual(
        components.map(component => component.name),
        state.metadata.components,
    );
    assert.ok(components.every(component => component.settings.enabled));
    assert.ok(components.every(component => component.settings.alignment));
    assert.equal(
        components.find(component => component.name === "Text").settings.text,
        "XYZ {blockPosition.x} / {blockPosition.y} / {blockPosition.z}",
    );
    assert.equal(
        components.find(component => component.name === "InventoryStatistics").settings.rowLength,
        1,
    );
});

test("serves player, target, scoreboard, effects, inventory, modules, keybinds, and HUD settings", async () => {
    const state = createModernHudPreviewState();
    const paths = [
        "/api/v1/client/player",
        "/api/v1/client/player/inventory",
        "/api/v1/client/modules",
        "/api/v1/client/keybinds",
        "/api/v1/client/modules/settings?name=HUD",
        "/api/v1/client/info",
        "/api/v1/client/window",
        "/metadata.json",
    ];

    const responses = await Promise.all(paths.map(path =>
        routeModernHudPreviewRequest(
            state,
            new Request(`http://preview.local${path}`),
        ),
    ));

    assert.ok(responses.every(response => response.status === 200));
    assert.equal(state.player.scoreboard.entries.length, 5);
    assert.equal(state.player.effects.length, 3);
    assert.equal(state.target.username, "PreviewTarget");
    assert.equal(state.inventory.main.length, 36);
    assert.ok(state.modules.some(module => module.enabled && module.tag));
    assert.deepEqual(
        state.keybinds.map(bind => bind.bindName),
        ["key.forward", "key.back", "key.left", "key.right", "key.jump"],
    );
    assert.equal(
        state.hudSettings.value.find(setting => setting.name === "Theme").value,
        "Modern",
    );
});

test("HUD settings and module toggles round-trip only through preview state", async () => {
    const state = createModernHudPreviewState();
    const classicSettings = structuredClone(state.hudSettings);
    classicSettings.value.find(setting => setting.name === "Theme").value = "Classic";

    const settingsResponse = await routeModernHudPreviewRequest(
        state,
        jsonRequest(
            "http://preview.local/api/v1/client/modules/settings?name=HUD",
            "PUT",
            classicSettings,
        ),
    );
    const toggleResponse = await routeModernHudPreviewRequest(
        state,
        jsonRequest(
            "http://preview.local/api/v1/client/modules/toggle",
            "POST",
            {name: "Flight", enabled: true},
        ),
    );

    assert.equal(settingsResponse.status, 204);
    assert.equal(toggleResponse.status, 204);
    assert.equal(
        state.hudSettings.value.find(setting => setting.name === "Theme").value,
        "Classic",
    );
    assert.equal(state.modules.find(module => module.name === "Flight").enabled, true);
    assert.deepEqual(
        state.requests.map(request => request.method),
        ["PUT", "POST"],
    );
});

test("snapshot events populate event-driven widgets without sharing mutable fixture state", () => {
    const state = createModernHudPreviewState();
    const events = createModernHudPreviewSnapshotEvents(state);

    assert.deepEqual(
        new Set(events.map(event => event.name)),
        new Set([
            "clientPlayerData",
            "clientPlayerEffect",
            "clientPlayerInventory",
            "targetChange",
            "blockCountChange",
            "notification",
            "key",
            "overlayMessage",
        ]),
    );

    const playerEvent = events.find(event => event.name === "clientPlayerData");
    playerEvent.event.playerData.username = "Mutated";
    assert.equal(state.player.username, "PreviewPlayer");
});

test("returns an explicit 404 for APIs outside the HUD preview contract", async () => {
    const state = createModernHudPreviewState();
    const response = await routeModernHudPreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/unsupported"),
    );

    assert.equal(response.status, 404);
    assert.match(await response.text(), /unsupported modern hud preview api/i);
});
