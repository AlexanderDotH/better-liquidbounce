import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

import {
    MODERN_MODULE_STAGGER_LIMIT,
    MODERN_PANEL_STAGGER_LIMIT,
    MODERN_RESULT_STAGGER_LIMIT,
    motionStaggerIndex,
} from "../src/routes/clickgui/themes/modern/model/modernMotion.ts";

const modernRoot = new URL(
    "../src/routes/clickgui/themes/modern/",
    import.meta.url,
);

function read(relativePath) {
    return readFileSync(new URL(relativePath, modernRoot), "utf8");
}

test("motion stagger indices are finite, integral, and capped", () => {
    assert.equal(motionStaggerIndex(-4, MODERN_PANEL_STAGGER_LIMIT), 0);
    assert.equal(motionStaggerIndex(2.9, MODERN_MODULE_STAGGER_LIMIT), 2);
    assert.equal(
        motionStaggerIndex(99, MODERN_RESULT_STAGGER_LIMIT),
        MODERN_RESULT_STAGGER_LIMIT,
    );
    assert.equal(motionStaggerIndex(Number.NaN, MODERN_PANEL_STAGGER_LIMIT), 0);
});

test("Modern owns centralized entrance, interaction, and reduced-motion tokens", () => {
    const tabbed = read("ModernTabbedClickGui.svelte");

    for (const token of [
        "--modern-motion-fast",
        "--modern-motion-duration",
        "--modern-motion-entrance-duration",
        "--modern-motion-stagger",
        "--modern-motion-easing",
    ]) {
        assert.match(tabbed, new RegExp(token));
    }

    assert.match(
        tabbed,
        /@media\s*\(prefers-reduced-motion:\s*reduce\)[\s\S]*--modern-motion-entrance-duration:\s*0ms/,
    );
});

test("panels and module rows use capped staggered entrance motion", () => {
    const clickGui = read("ModernClickGui.svelte");
    const panel = read("ModernPanel.svelte");
    const module = read("ModernModule.svelte");

    assert.match(clickGui, /\{panelIndex\}/);
    assert.match(panel, /--modern-panel-enter-index/);
    assert.match(panel, /@keyframes\s+modern-panel-enter/);
    assert.match(panel, /\{moduleIndex\}/);
    assert.match(module, /class:revealed/);
    assert.match(module, /--modern-module-enter-index/);
    assert.match(module, /@keyframes\s+modern-module-enter/);
    assert.match(module, /@keyframes\s+modern-state-confirm/);
});

test("command, search, and settings surfaces expose purposeful motion cues", () => {
    const command = read("ModernCommandBar.svelte");
    const search = read("ModernSearch.svelte");
    const settings = read("ModernSettings.svelte");

    assert.match(command, /class:settings-active/);
    assert.match(command, /@keyframes\s+modern-command-enter/);
    assert.match(command, /\.tabs::before[\s\S]*transition:[\s\S]*transform/);
    assert.match(search, /--modern-result-enter-index/);
    assert.match(search, /@keyframes\s+modern-search-result-enter/);
    assert.match(settings, /--modern-setting-card-index/);
    assert.match(settings, /@keyframes\s+modern-selection-confirm/);
});

test("every animated Modern surface explicitly disables decorative motion", () => {
    for (const relativePath of [
        "ModernTabbedClickGui.svelte",
        "ModernCommandBar.svelte",
        "ModernPanel.svelte",
        "ModernModule.svelte",
        "ModernSearch.svelte",
        "ModernSettings.svelte",
    ]) {
        assert.match(
            read(relativePath),
            /@media\s*\(prefers-reduced-motion:\s*reduce\)/,
            `${relativePath} must define a reduced-motion fallback`,
        );
    }
});
