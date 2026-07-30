import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const hudRoot = new URL("../src/routes/hud/", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, hudRoot), "utf8");
}

test("HUD selects a live visual theme without remounting its shared component layout", () => {
    const hud = read("Hud.svelte");

    assert.match(hud, /import\s+\{hudThemeSession\}\s+from\s+"\.\/theme\/themeSession"/);
    assert.match(hud, /listen\("hudValueChange"/);
    assert.match(hud, /hudThemeSession\.synchronize\(event\.configurable\)/);
    assert.match(hud, /hudThemeSession\.load\(\)/);
    assert.match(hud, /class:hud-theme--classic=\{\$hudThemeSession\.theme === "Classic"\}/);
    assert.match(hud, /class:hud-theme--modern=\{\$hudThemeSession\.theme === "Modern"\}/);
    assert.doesNotMatch(hud, /#key\s+\$hudThemeSession\.theme/);
});

test("HUD exposes stable component names and keeps custom content unwrapped", () => {
    const hud = read("Hud.svelte");
    const draggable = read("elements/DraggableComponent.svelte");

    assert.match(hud, /componentName=\{c\.name\}/);
    assert.match(draggable, /export let componentName:\s*string/);
    assert.match(draggable, /data-component=\{componentName\}/);

    for (const rawComponent of ["Text", "Image", "Taco"]) {
        assert.match(hud, new RegExp(`c\\.name === "${rawComponent}"`));
    }
});

test("Modern HUD uses a compact watermark while Classic keeps its current watermark", () => {
    const hud = read("Hud.svelte");
    const watermark = read("themes/modern/ModernWatermark.svelte");

    assert.match(hud, /import ModernWatermark from "\.\/themes\/modern\/ModernWatermark\.svelte"/);
    assert.match(
        hud,
        /c\.name === "Watermark"[\s\S]*\$hudThemeSession\.theme === "Modern"[\s\S]*<ModernWatermark\/>[\s\S]*<Watermark\/>/,
    );
    assert.match(watermark, /class="modern-watermark"/);
    assert.match(watermark, /LiquidBounce/);
    assert.match(watermark, /border-radius:\s*999px/);
});

test("Modern HUD foundation is transparent and scoped away from Classic", () => {
    const hud = read("Hud.svelte");
    const modern = read("themes/modern/modernHud.scss");

    assert.match(hud, /background:\s*transparent/);
    assert.match(modern, /\.hud-theme--modern/);
    assert.doesNotMatch(modern, /\.hud-theme--classic/);
    assert.doesNotMatch(modern, /backdrop-filter/);
    assert.doesNotMatch(modern, /background:\s*#(?:0[0-9a-f]){3,6}\s*;/i);
});

test("Modern HUD covers every bundled widget family with Graphite Glass surfaces", () => {
    const modern = read("themes/modern/modernHud.scss");

    for (const componentName of [
        "ArrayList",
        "TabGui",
        "Notifications",
        "TargetHud",
        "BlockCounter",
        "Hotbar",
        "Scoreboard",
        "ArmorItems",
        "InventoryStatistics",
        "Inventory",
        "CraftingInventory",
        "EnderChestInventory",
        "Keystrokes",
        "Effects",
        "KeyBinds",
    ]) {
        assert.match(
            modern,
            new RegExp(`data-component=["']${componentName}["']`),
            `${componentName} needs a Modern scoped treatment`,
        );
    }

    assert.match(modern, /rgba\(15,\s*18,\s*23,\s*0\.(?:8[0-9]|9[0-9])\)/);
    assert.match(modern, /rgba\(255,\s*255,\s*255,\s*0\.1\)/);
});

test("Modern HUD preserves native item and hotbar geometry", () => {
    const modern = read("themes/modern/modernHud.scss");

    assert.match(
        modern,
        /\[data-component="Hotbar"\][\s\S]*\.slot[\s\S]*width:\s*45px[\s\S]*height:\s*45px/,
    );
    assert.match(
        modern,
        /\[data-component="Hotbar"\][\s\S]*\.slider[\s\S]*width:\s*45px[\s\S]*height:\s*45px/,
    );
    assert.match(
        modern,
        /\[data-component="Inventory"\][\s\S]*\.item-stack[\s\S]*width:\s*32px[\s\S]*height:\s*32px/,
    );
});

test("Modern HUD leaves Text, Image, and Taco as raw user content", () => {
    const modern = read("themes/modern/modernHud.scss");

    for (const componentName of ["Text", "Image", "Taco"]) {
        assert.match(
            modern,
            new RegExp(`\\[data-component="${componentName}"\\][\\s\\S]*background:\\s*transparent`),
        );
    }
});

test("Modern HUD motion is finite, visible at rest, and accessibility-safe", () => {
    const modern = read("themes/modern/modernHud.scss");

    assert.match(modern, /--modern-hud-motion:\s*(?:1[4-9]\d|2[0-2]\d)ms/);
    assert.match(modern, /@media\s*\(prefers-reduced-motion:\s*reduce\)/);
    assert.match(modern, /--modern-hud-motion:\s*0ms/);
    assert.doesNotMatch(modern, /infinite/);
    assert.doesNotMatch(modern, /opacity:\s*0(?:;|\s)/);
});
