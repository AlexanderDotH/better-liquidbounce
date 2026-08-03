import assert from "node:assert/strict";
import test from "node:test";

import {
    logicalViewportDimension,
    motionAwareScrollBehavior,
    moveClickGuiView,
    normalizeClickGuiScaleFactor,
} from "../src/routes/clickgui/themes/modern/modernShellState.ts";

test("arrow navigation includes the upstream HUD editor tab", () => {
    assert.equal(moveClickGuiView("clickgui", 1), "hud-editor");
    assert.equal(moveClickGuiView("hud-editor", 1), "settings");
    assert.equal(moveClickGuiView("settings", 1), "clickgui");
    assert.equal(moveClickGuiView("clickgui", -1), "settings");
    assert.equal(moveClickGuiView("settings", -1), "hud-editor");
});

test("ClickGUI scaling keeps viewport math finite and uses Minecraft scale two as fallback", () => {
    assert.equal(normalizeClickGuiScaleFactor(1.5), 1.5);
    assert.equal(normalizeClickGuiScaleFactor(0), 2);
    assert.equal(normalizeClickGuiScaleFactor(Number.NaN), 2);

    assert.equal(logicalViewportDimension(1920, 2), 1920);
    assert.equal(logicalViewportDimension(1920, 1), 3840);
    assert.equal(logicalViewportDimension(1920, 0), 1920);
    assert.equal(logicalViewportDimension(Number.NaN, 2), 0);
});

test("reduced motion disables smooth programmatic scrolling", () => {
    assert.equal(motionAwareScrollBehavior(false), "smooth");
    assert.equal(motionAwareScrollBehavior(true), "auto");
});
