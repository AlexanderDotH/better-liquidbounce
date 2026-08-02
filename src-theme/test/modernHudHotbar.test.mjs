import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const hudRoot = new URL("../src/routes/hud/", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, hudRoot), "utf8");
}

test("Modern hotbar keeps native-item geometry and clamps the selected slot", () => {
    const hotbar = read("elements/hotbar/HotBar.svelte");
    const modern = read("themes/modern/modernHud.scss");

    assert.match(
        hotbar,
        /currentSlot\s*=\s*Math\.min\(8,\s*Math\.max\(0,\s*playerData\.selectedSlot\)\)/,
    );
    assert.match(modern, /\[data-component="Hotbar"\] \.hotbar\)[\s\S]*transform:\s*translateY\(-16px\)/);
    assert.match(modern, /\[data-component="Hotbar"\] \.slot\)[\s\S]*width:\s*45px[\s\S]*height:\s*45px/);
});

test("Modern hotbar selection frame moves smoothly while Classic remains immediate", () => {
    const hotbar = read("elements/hotbar/HotBar.svelte");
    const modern = read("themes/modern/modernHud.scss");

    assert.match(
        hotbar,
        /class="slider"\s+style="transform:\s*translateX\(\{currentSlot \* 45\}px\)"/,
    );
    assert.match(hotbar, /\.slider\s*\{[\s\S]*?left:\s*0\s*;/);
    assert.doesNotMatch(hotbar, /transition:\s*(?:left|transform)/);
    assert.match(
        modern,
        /\[data-component="Hotbar"\] \.slider\)[\s\S]*?transition:\s*transform var\(--modern-hud-motion\) var\(--modern-hud-easing\)/,
    );
    assert.match(modern, /@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*--modern-hud-motion:\s*0ms/);
    assert.doesNotMatch(modern, /transition[^;]*infinite/);
});
