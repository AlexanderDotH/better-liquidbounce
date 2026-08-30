import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const tabGuiRoot = new URL(
    "../../../src/routes/hud/elements/tabgui/",
    import.meta.url,
);

function read(relativePath) {
    return readFileSync(new URL(relativePath, tabGuiRoot), "utf8");
}

test("Modern TabGUI is an explicit presentation variant with compact category pills", () => {
    const tabGui = read("TabGui.svelte");
    const category = read("Category.svelte");

    assert.match(tabGui, /export let variant:\s*"classic"\s*\|\s*"modern"/);
    assert.match(tabGui, /class:modern=\{variant === "modern"\}/);
    assert.match(tabGui, /<Category \{name\}[^>]*\{variant\}/s);
    assert.match(category, /export let variant:\s*"classic"\s*\|\s*"modern"/);
    assert.match(category, /class:modern=\{variant === "modern"\}/);
    assert.match(category, /new URL\(iconPath,\s*location\.href\)\.href/);
    assert.match(category, /\.category\.modern[\s\S]*border-radius:\s*8px/);
    assert.match(category, /\.category\.modern[\s\S]*min-height:\s*28px/);
});

test("Modern module flyout uses compact module pills and an enabled-state dot", () => {
    const tabGui = read("TabGui.svelte");
    const module = read("Module.svelte");

    assert.match(tabGui, /<Module[^>]*\{variant\}/s);
    assert.match(tabGui, /max-height:/);
    assert.match(module, /class="status-dot"/);
    assert.match(module, /class:modern=\{variant === "modern"\}/);
    assert.match(module, /\.module\.modern[\s\S]*border-radius:\s*7px/);
    assert.match(module, /\.module\.modern\.enabled[\s\S]*\.status-dot/);
});

test("Classic TabGUI keeps its original geometry while shared motion respects reduced motion", () => {
    const tabGui = read("TabGui.svelte");
    const category = read("Category.svelte");

    assert.match(category, /width:\s*62px/);
    assert.match(category, /background:\s*linear-gradient/);
    assert.match(tabGui, /prefersReducedMotion/);
    assert.match(tabGui, /hudMotionDuration\(variant,\s*\$prefersReducedMotion\)/);
    assert.doesNotMatch(tabGui, /matchMedia\(/);
    assert.match(tabGui, /motionOffset = variant === "modern" \? -8 : -10/);
    assert.match(tabGui, /transition:fly=\{\{ x: motionOffset, duration: motionDuration \}\}/);
});
