import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const classicSettings = readFileSync(
    new URL("../src/routes/clickgui/tabs/GlobalSettings.svelte", import.meta.url),
    "utf8",
);

test("Classic settings mounts the compact HUD appearance selector below ClickGUI appearance", () => {
    assert.match(
        classicSettings,
        /import HudThemeSelector from "\.\.\/\.\.\/\.\.\/shared\/hud-theme\/HudThemeSelector\.svelte";/,
    );
    assert.match(classicSettings, /<HudThemeSelector variant="compact"\s*\/>/);

    const clickGuiAppearanceStart = classicSettings.indexOf(
        '<section class="appearance-setting"',
    );
    const clickGuiAppearanceEnd = classicSettings.indexOf(
        "</section>",
        clickGuiAppearanceStart,
    );
    const hudAppearanceStart = classicSettings.indexOf(
        '<HudThemeSelector variant="compact"',
    );
    const globalSettingsStart = classicSettings.indexOf(
        "{#if globalLoading && !globalSettings}",
    );

    assert.ok(clickGuiAppearanceStart >= 0);
    assert.ok(clickGuiAppearanceEnd > clickGuiAppearanceStart);
    assert.ok(hudAppearanceStart > clickGuiAppearanceEnd);
    assert.ok(globalSettingsStart > hudAppearanceStart);
});

test("HUD appearance stays isolated from ClickGUI theme and global settings persistence", () => {
    assert.doesNotMatch(classicSettings, /import\s+\{[^}]*hudThemeSession/);
    assert.match(classicSettings, /\$clickGuiThemeSession\.saveError/);
    assert.match(classicSettings, /clickGuiThemeSession\.retryThemeSave\(\)/);
    assert.match(classicSettings, /globalSettingsSaveQueue\.enqueue/);
    assert.match(classicSettings, /globalSettingsSaveQueue\.retry\(\)/);
});
