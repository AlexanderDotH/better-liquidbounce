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

test("Modern hotbar owns one contextual island while Classic keeps its current XP row", () => {
    const hud = read("Hud.svelte");
    const hotbar = read("elements/hotbar/HotBar.svelte");
    const contextual = read("elements/hotbar/ModernContextualBar.svelte");

    assert.match(hud, /<HotBar\s+presentation=\{presentation\}\s*\/>/);
    assert.match(hotbar, /export let presentation:\s*"classic" \| "modern"/);
    assert.match(hotbar, /<ModernContextualBar\s+data=\{contextualBar\}/);
    assert.match(
        hotbar,
        /ModernContextualBar[\s\S]*\{#if playerData\.gameMode !== "spectator"\}[\s\S]*class="hotbar-elements"/,
    );
    assert.match(hotbar, /presentation === "classic"[\s\S]*playerData\.experienceLevel > 0[\s\S]*<Status/);

    assert.match(contextual, /data-mode=\{data\.mode\}/);
    assert.match(contextual, /resource\/skin\?uuid=\{marker\.playerUuid\}/);
    assert.match(contextual, /waypointEmoji\(marker\.style\)/);
    assert.match(contextual, /marker\.elevation/);
});

test("Modern experience bar uses the recovered space without changing its height", () => {
    const contextual = read("elements/hotbar/ModernContextualBar.svelte");
    const experienceBranch = contextual.match(
        /\{#if data\.mode === "experience"\}([\s\S]*?)\{:else if data\.mode === "jumpableVehicle"\}/,
    )?.[1];

    assert.ok(experienceBranch);
    assert.doesNotMatch(experienceBranch, /mode-(?:emoji|copy)/);
    assert.match(experienceBranch, /aria-label="Experience level">\{data\.level\}/);
    assert.match(experienceBranch, /contextual-progress contextual-progress--experience/);
    assert.match(contextual, /\.contextual-island--experience\s*\{[\s\S]*grid-template-columns:\s*auto minmax\(0, 1fr\)/);
    assert.match(contextual, /\.contextual-progress--experience span\s*\{[\s\S]*var\(--hotbar-experience-color\)/);
    assert.match(contextual, /\.contextual-progress\s*\{[\s\S]*height:\s*8px/);
});
