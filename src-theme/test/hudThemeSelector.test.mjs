import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import {readSourceWithStyles} from "./themeSource.mjs";

const selector = readSourceWithStyles(
    new URL("../src/shared/hud-theme/HudThemeSelector.svelte", import.meta.url),
);

test("offers self-contained card and compact HUD theme selector variants", () => {
    assert.match(selector, /variant\??:\s*"card"\s*\|\s*"compact"/);
    assert.match(selector, /hud-theme-selector--\$\{variant\}/);
    assert.match(selector, /hud-theme-selector--card/);
    assert.match(selector, /hud-theme-selector--compact/);
});

test("binds accessible choices directly to the shared HUD theme session", () => {
    assert.match(selector, /role="radiogroup"/);
    assert.match(selector, /role="radio"/);
    assert.match(selector, /aria-checked=\{\$hudThemeSession\.theme === option\.value\}/);
    assert.match(selector, /selectTheme\(option\.value\)/);
    assert.match(selector, /hudThemeSession\.selectTheme\(theme\)/);
    assert.match(selector, /hudThemeSession\.retryThemeSave\(\)/);
});

test("loads only when needed and disables choices during load or save", () => {
    assert.match(selector, /if \(!\$hudThemeSession\.settings\)/);
    assert.match(selector, /hudThemeSession\.load\(\)/);
    assert.match(
        selector,
        /disabled=\{\$hudThemeSession\.loading \|\| \$hudThemeSession\.saving\}/,
    );
});
