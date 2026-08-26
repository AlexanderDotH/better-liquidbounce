import assert from "node:assert/strict";
import test from "node:test";

import {
    MODERN_PANEL_CANVAS_TOP,
    MODERN_PANEL_HEADER_HEIGHT,
    MODERN_PANEL_STATE_PREFIX,
    MODERN_PANEL_WIDTH,
    arrangeModernPanels,
    clampModernPanelPosition,
    findModernPanelStateKeys,
    modernPanelStateKey,
    parseModernPanelState,
    snapModernPanelPosition,
} from "../src/routes/clickgui/themes/modern/model/modernPanelState.ts";
import {
    filterModulesBySearch,
    normalizeModuleSearchText,
} from "../src/routes/clickgui/themes/modern/model/moduleSearch.ts";
import {
    shouldLoadModernModuleSettings,
} from "../src/routes/clickgui/themes/modern/model/modernInteractionState.ts";

function modules(count) {
    return Array.from({length: count}, (_, index) => ({
        name: `Module${index + 1}`,
        category: "Test",
        keyBind: {boundKey: "key.keyboard.unknown", action: "TOGGLE"},
        enabled: false,
        description: "",
        hidden: false,
        aliases: [],
        tag: null,
    }));
}

test("arranges four 288px panels per row at 1280 logical pixels", () => {
    const positions = arrangeModernPanels(modules(5), 1280);

    assert.deepEqual(positions.map(({left, top}) => ({left, top})), [
        {left: 20, top: 84},
        {left: 324, top: 84},
        {left: 628, top: 84},
        {left: 932, top: 84},
        {left: 20, top: 144},
    ]);
});

test("arranges six panels per row at 1920 logical pixels", () => {
    const positions = arrangeModernPanels(modules(7), 1920);

    assert.equal(positions[5].left, 1540);
    assert.equal(positions[5].top, MODERN_PANEL_CANVAS_TOP);
    assert.equal(positions[6].left, 20);
    assert.equal(positions[6].top, 144);
});

test("arranges eleven panels per row at 3440 logical pixels", () => {
    const positions = arrangeModernPanels(modules(12), 3440);

    assert.equal(positions[10].left, 3060);
    assert.equal(positions[10].top, MODERN_PANEL_CANVAS_TOP);
    assert.equal(positions[11].left, 20);
    assert.equal(positions[11].top, 144);
});

test("falls back when persisted panel JSON is malformed or invalid", () => {
    const fallback = {
        left: 20,
        top: MODERN_PANEL_CANVAS_TOP,
        expanded: false,
        scrollTop: 0,
        zIndex: 0,
    };

    assert.deepEqual(parseModernPanelState("{", fallback), fallback);
    assert.deepEqual(parseModernPanelState(JSON.stringify({
        ...fallback,
        left: Number.POSITIVE_INFINITY,
    }), fallback), fallback);
    assert.deepEqual(parseModernPanelState(JSON.stringify({
        ...fallback,
        expanded: "yes",
    }), fallback), fallback);
});

test("returns a copy of valid persisted panel state", () => {
    const fallback = {
        left: 20,
        top: MODERN_PANEL_CANVAS_TOP,
        expanded: false,
        scrollTop: 0,
        zIndex: 0,
    };
    const persisted = {
        left: 92,
        top: 128,
        expanded: true,
        scrollTop: 33,
        zIndex: 7,
    };

    assert.deepEqual(parseModernPanelState(JSON.stringify(persisted), fallback), persisted);
});

test("clamps the full panel width and keeps its header visible", () => {
    assert.deepEqual(
        clampModernPanelPosition(
            {left: 2000, top: 900},
            {width: 1280, height: 720},
        ),
        {
            left: 1280 - MODERN_PANEL_WIDTH,
            top: 720 - MODERN_PANEL_HEADER_HEIGHT,
        },
    );

    assert.deepEqual(
        clampModernPanelPosition(
            {left: -200, top: -100},
            {width: 1280, height: 720},
        ),
        {left: 0, top: MODERN_PANEL_CANVAS_TOP},
    );
});

test("snaps both coordinates unless Shift bypasses snapping", () => {
    const position = {left: 37, top: 103};

    assert.deepEqual(
        snapModernPanelPosition(position, {
            gridSize: 16,
            snappingEnabled: true,
            shiftHeld: false,
        }),
        {left: 32, top: 96},
    );
    assert.deepEqual(
        snapModernPanelPosition(position, {
            gridSize: 16,
            snappingEnabled: true,
            shiftHeld: true,
        }),
        position,
    );
    assert.deepEqual(
        snapModernPanelPosition(position, {
            gridSize: 16,
            snappingEnabled: false,
            shiftHeld: false,
        }),
        position,
    );
});

test("discovers only versioned Modern panel state keys for reset", () => {
    const keys = [
        modernPanelStateKey("Combat"),
        "clickgui.panel.Combat",
        "clickgui.modern.module.v1.KillAura",
        modernPanelStateKey("Movement"),
        `${MODERN_PANEL_STATE_PREFIX}`,
        `${MODERN_PANEL_STATE_PREFIX}Combat.extra`,
    ];

    assert.deepEqual(findModernPanelStateKeys(keys), [
        modernPanelStateKey("Combat"),
        modernPanelStateKey("Movement"),
        `${MODERN_PANEL_STATE_PREFIX}Combat.extra`,
    ]);
});

test("normalizes module search without case or whitespace sensitivity", () => {
    assert.equal(normalizeModuleSearchText("  Kill Aura  "), "killaura");
    assert.equal(normalizeModuleSearchText("FLY\tMode"), "flymode");
});

test("searches module names and aliases while preserving module order", () => {
    const searchableModules = [
        {...modules(1)[0], name: "KillAura", aliases: ["Force Field"]},
        {...modules(1)[0], name: "Flight", aliases: ["Fly Mode"]},
        {...modules(1)[0], name: "Speed", aliases: ["BHop"]},
    ];

    assert.deepEqual(
        filterModulesBySearch(searchableModules, "KILL aura").map(module => module.name),
        ["KillAura"],
    );
    assert.deepEqual(
        filterModulesBySearch(searchableModules, "forcefield").map(module => module.name),
        ["KillAura"],
    );
    assert.deepEqual(
        filterModulesBySearch(searchableModules, "flymode").map(module => module.name),
        ["Flight"],
    );
    assert.deepEqual(filterModulesBySearch(searchableModules, "missing"), []);
});

test("an empty search returns no popover results", () => {
    assert.deepEqual(filterModulesBySearch(modules(2), " \t "), []);
});

test("loads Modern module settings only for an expanded unloaded module", () => {
    assert.equal(shouldLoadModernModuleSettings({
        expanded: false,
        hasSettings: true,
        loaded: false,
        loading: false,
    }), false);
    assert.equal(shouldLoadModernModuleSettings({
        expanded: true,
        hasSettings: true,
        loaded: false,
        loading: false,
    }), true);
    assert.equal(shouldLoadModernModuleSettings({
        expanded: true,
        hasSettings: true,
        loaded: true,
        loading: false,
    }), false);
    assert.equal(shouldLoadModernModuleSettings({
        expanded: true,
        hasSettings: true,
        loaded: false,
        loading: true,
    }), false);
    assert.equal(shouldLoadModernModuleSettings({
        expanded: true,
        hasSettings: false,
        loaded: false,
        loading: false,
    }), false);
});
