import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const hud = readFileSync(
    new URL("../src/routes/hud/Hud.svelte", import.meta.url),
    "utf8",
);

test("HUD derives one live presentation value for shared Modern variants", () => {
    assert.match(hud, /let presentation:\s*"classic" \| "modern" = "classic"/);
    assert.match(
        hud,
        /\$:\s*presentation = \$hudThemeSession\.theme === "Modern" \? "modern" : "classic"/,
    );
    assert.match(hud, /<ArrayList settings=\{c\.settings\} variant=\{presentation\}\s*\/>/);
    assert.match(hud, /<TabGui variant=\{presentation\}\s*\/>/);
    assert.match(hud, /<Notifications variant=\{presentation\}\s*\/>/);
    assert.match(hud, /<TargetHud \{presentation\}\s*\/>/);
});

test("Modern inventory labels are additive and Classic receives no labels", () => {
    for (const label of ["Armor", "Resources", "Inventory", "Crafting", "Ender Chest"]) {
        assert.match(
            hud,
            new RegExp(`label=\\{presentation === "modern" \\? "${label}" : undefined\\}`),
        );
    }

    assert.ok((hud.match(/variant=\{presentation\}/g) ?? []).length >= 7);
});
