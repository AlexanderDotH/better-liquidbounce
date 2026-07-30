import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const settings = readFileSync(
    new URL("../src/routes/clickgui/themes/modern/ModernSettings.svelte", import.meta.url),
    "utf8",
);

test("places the card HUD appearance selector between ClickGUI appearance and global settings", () => {
    assert.match(
        settings,
        /import HudThemeSelector from "\.\.\/\.\.\/\.\.\/hud\/theme\/HudThemeSelector\.svelte";/,
    );

    const clickGuiAppearance = settings.indexOf('id="appearance-heading"');
    const hudAppearance = settings.indexOf('<HudThemeSelector variant="card" />');
    const globalSettings = settings.indexOf('id="global-heading"');

    assert.notEqual(clickGuiAppearance, -1);
    assert.notEqual(hudAppearance, -1);
    assert.notEqual(globalSettings, -1);
    assert.ok(clickGuiAppearance < hudAppearance);
    assert.ok(hudAppearance < globalSettings);
});

test("keeps ClickGUI, HUD, and global save state independent", () => {
    assert.match(
        settings,
        /disabled=\{\$session\.loading \|\| \$session\.saving\}/,
    );
    assert.match(settings, /globalSettingsSaveQueue\.enqueue/);
    assert.doesNotMatch(settings, /HudThemeSelector[^>]+(?:session|disabled)=/);
    assert.doesNotMatch(settings, /hudThemeSession/);
});
