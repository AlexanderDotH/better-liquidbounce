import assert from "node:assert/strict";
import test from "node:test";

import {
    createModernClickGuiPreviewState,
    routeModernClickGuiPreviewRequest,
} from "../src/dev/modern-clickgui-preview/previewFixture.ts";

function jsonRequest(url, method, body) {
    return new Request(url, {
        method,
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body),
    });
}

test("serves deterministic modules and comprehensive settings fixtures", async () => {
    const state = createModernClickGuiPreviewState();

    const modulesResponse = await routeModernClickGuiPreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/modules"),
    );
    const modules = await modulesResponse.json();
    const settingsResponse = await routeModernClickGuiPreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/modules/settings?name=KillAura"),
    );
    const settings = await settingsResponse.json();

    assert.equal(modulesResponse.status, 200);
    assert.deepEqual(
        [...new Set(modules.map(module => module.category))],
        ["Combat", "Movement", "Player", "World", "Render", "Exploit", "Fun", "Misc"],
    );
    assert.deepEqual(
        new Set(settings.value.map(setting => setting.valueType)),
        new Set([
            "BOOLEAN",
            "FLOAT",
            "INT",
            "CHOICE",
            "CHOOSE",
            "MULTI_CHOOSE",
            "TEXT",
            "BIND",
            "TOGGLEABLE",
            "CONFIGURABLE",
            "MUTABLE_LIST",
            "REGISTRY_LIST",
        ]),
    );
});

test("module toggle and settings writes update only preview state", async () => {
    const state = createModernClickGuiPreviewState();
    const toggleResponse = await routeModernClickGuiPreviewRequest(
        state,
        jsonRequest(
            "http://preview.local/api/v1/client/modules/toggle",
            "POST",
            {name: "KillAura", enabled: false},
        ),
    );

    const nextSettings = structuredClone(state.moduleSettings.KillAura);
    nextSettings.value.find(setting => setting.name === "AutoBlock").value = false;
    const settingsResponse = await routeModernClickGuiPreviewRequest(
        state,
        jsonRequest(
            "http://preview.local/api/v1/client/modules/settings?name=KillAura",
            "PUT",
            nextSettings,
        ),
    );

    assert.equal(toggleResponse.status, 204);
    assert.equal(settingsResponse.status, 204);
    assert.equal(state.modules.find(module => module.name === "KillAura").enabled, false);
    assert.equal(
        state.moduleSettings.KillAura.value.find(setting => setting.name === "AutoBlock").value,
        false,
    );
});

test("AutoShop fixture preserves Vanilla rules while its mode is inactive", async () => {
    const state = createModernClickGuiPreviewState();
    const settings = structuredClone(state.moduleSettings.AutoShop);
    const mode = settings.value.find(setting => setting.name === "Mode");
    const vanillaSettings = mode.choices.Vanilla.value;

    assert.deepEqual(
        vanillaSettings.map(setting => setting.name),
        ["Trades", "Reach", "CPS", "Rotations"],
    );
    assert.deepEqual(
        vanillaSettings.slice(0, 2).map(setting => setting.valueType),
        ["MERCHANT_TRADE_FILTERS", "MERCHANT_REACH"],
    );
    assert.deepEqual(vanillaSettings[1].value, {range: 4.5, wallRange: 3});

    vanillaSettings[0].value[0].outputs = ["minecraft:bookshelf"];
    mode.active = "ServerShop";
    const writeResponse = await routeModernClickGuiPreviewRequest(
        state,
        jsonRequest(
            "http://preview.local/api/v1/client/modules/settings?name=AutoShop",
            "PUT",
            settings,
        ),
    );
    const readResponse = await routeModernClickGuiPreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/modules/settings?name=AutoShop"),
    );
    const remounted = await readResponse.json();
    const remountedMode = remounted.value.find(setting => setting.name === "Mode");

    assert.equal(writeResponse.status, 204);
    assert.equal(remountedMode.active, "ServerShop");
    assert.deepEqual(
        remountedMode.choices.Vanilla.value[0].value[0].outputs,
        ["minecraft:bookshelf"],
    );
});

test("global settings, typing, and persistent storage round-trip through the router", async () => {
    const state = createModernClickGuiPreviewState();
    const combat = state.globalSettings.value.find(setting => setting.name === "Combat");
    assert.equal(
        combat.value.find(setting => setting.name === "DelegateKillAuraAttacks").value,
        false,
    );
    const globals = structuredClone(state.globalSettings);
    globals.value[0].value[0].value = false;

    await routeModernClickGuiPreviewRequest(
        state,
        jsonRequest("http://preview.local/api/v1/client/global", "PUT", globals),
    );
    await routeModernClickGuiPreviewRequest(
        state,
        jsonRequest("http://preview.local/api/v1/client/typing", "POST", {typing: true}),
    );
    await routeModernClickGuiPreviewRequest(
        state,
        jsonRequest(
            "http://preview.local/api/v1/client/localStorage/all",
            "PUT",
            {items: [{key: "clickgui.preview", value: "saved"}]},
        ),
    );

    const storageResponse = await routeModernClickGuiPreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/localStorage/all"),
    );

    assert.equal(state.globalSettings.value[0].value[0].value, false);
    assert.equal(state.typing, true);
    assert.deepEqual(await storageResponse.json(), {
        items: [{key: "clickgui.preview", value: "saved"}],
    });
});

test("returns an explicit 404 for production APIs missing from the preview contract", async () => {
    const state = createModernClickGuiPreviewState();
    const response = await routeModernClickGuiPreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/unsupported"),
    );

    assert.equal(response.status, 404);
    assert.match(await response.text(), /unsupported preview API/i);
});
