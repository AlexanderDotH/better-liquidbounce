import assert from "node:assert/strict";
import test from "node:test";

import {
    createModernHudPreviewSnapshotEvents,
    createModernHudPreviewState,
    resolveModernHudPreviewFixture,
    routeModernHudPreviewRequest,
} from "../src/dev/modern-hud-preview/previewFixture.ts";

const SHOWCASE_COMPONENTS = [
    "Watermark",
    "Text",
    "KeyBinds",
    "TabGui",
    "ArrayList",
    "Notifications",
    "Hotbar",
    "Scoreboard",
    "TargetHud",
    "Effects",
];

const INVENTORY_COMPONENTS = [
    "ArmorItems",
    "InventoryStatistics",
    "Inventory",
    "CraftingInventory",
    "EnderChestInventory",
];

function jsonRequest(url, method, body) {
    return new Request(url, {
        method,
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body),
    });
}

test("showcase fixture serves every component but enables only the product HUD", async () => {
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
    assert.ok(components.every(component => component.settings.alignment));
    assert.deepEqual(
        new Set(enabledComponentNames(components)),
        new Set(SHOWCASE_COMPONENTS),
    );
    assert.equal(
        components.find(component => component.name === "Text").settings.text,
        "XYZ {blockPosition.x} / {blockPosition.y} / {blockPosition.z}",
    );
    assert.equal(
        components.find(component => component.name === "Text").settings.container,
        "Pill",
    );
    assert.equal(
        components.find(component => component.name === "InventoryStatistics").settings.rowLength,
        1,
    );
});

test("inventory fixture isolates the optional inventory family in a non-overlapping layout", () => {
    const state = createModernHudPreviewState("inventory");

    assert.deepEqual(
        new Set(enabledComponentNames(state.components)),
        new Set(INVENTORY_COMPONENTS),
    );
    assert.deepEqual(
        componentPosition(state, "Inventory"),
        ["Left", 20, "Top", 90],
    );
    assert.deepEqual(
        componentPosition(state, "CraftingInventory"),
        ["CenterTranslated", 0, "Top", 90],
    );
    assert.deepEqual(
        componentPosition(state, "EnderChestInventory"),
        ["Right", 20, "Top", 90],
    );
    assert.deepEqual(
        componentPosition(state, "InventoryStatistics"),
        ["CenterTranslated", -96, "Bottom", 34],
    );
    assert.deepEqual(
        componentPosition(state, "ArmorItems"),
        ["CenterTranslated", 96, "Bottom", 34],
    );
    assert.deepEqual(
        state.components.map(component => component.name),
        state.metadata.components,
    );
});

test("fixture query selection defaults safely to showcase", () => {
    assert.equal(resolveModernHudPreviewFixture(new URLSearchParams()), "showcase");
    assert.equal(
        resolveModernHudPreviewFixture(new URLSearchParams("fixture=showcase")),
        "showcase",
    );
    assert.equal(
        resolveModernHudPreviewFixture(new URLSearchParams("fixture=inventory")),
        "inventory",
    );
    assert.equal(
        resolveModernHudPreviewFixture(new URLSearchParams("fixture=prototype")),
        "showcase",
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

function enabledComponentNames(components) {
    return components
        .filter(component => component.settings.enabled)
        .map(component => component.name);
}

function componentPosition(state, name) {
    const component = state.components.find(candidate => candidate.name === name);
    assert.ok(component, `${name} component must exist`);
    const alignment = component.settings.alignment;

    return [
        alignment.horizontalAlignment,
        alignment.horizontalOffset,
        alignment.verticalAlignment,
        alignment.verticalOffset,
    ];
}
