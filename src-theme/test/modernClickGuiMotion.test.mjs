import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

import {
    MODERN_ANIMATION_STALL_GUARD_MS,
    MODERN_MODULE_STAGGER_LIMIT,
    MODERN_PANEL_STAGGER_LIMIT,
    MODERN_RESULT_STAGGER_LIMIT,
    motionStaggerIndex,
    shouldSettleModernAnimation,
} from "../src/routes/clickgui/themes/modern/model/modernMotion.ts";

const modernRoot = new URL(
    "../src/routes/clickgui/themes/modern/",
    import.meta.url,
);

function read(relativePath) {
    return readFileSync(new URL(relativePath, modernRoot), "utf8");
}

function cssBlock(source, selector) {
    const selectorIndex = source.indexOf(selector);
    assert.notEqual(selectorIndex, -1, `${selector} must exist`);

    const openingBraceIndex = source.indexOf("{", selectorIndex);
    assert.notEqual(openingBraceIndex, -1, `${selector} must open a block`);

    let depth = 0;
    for (let index = openingBraceIndex; index < source.length; index += 1) {
        if (source[index] === "{") {
            depth += 1;
        } else if (source[index] === "}") {
            depth -= 1;
        }

        if (depth === 0) {
            return source.slice(openingBraceIndex + 1, index);
        }
    }

    assert.fail(`${selector} must close its block`);
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

test("only finite animations frozen on their opening frame are force-settled", () => {
    assert.equal(MODERN_ANIMATION_STALL_GUARD_MS, 600);
    assert.equal(shouldSettleModernAnimation("running", 0, 260), true);
    assert.equal(shouldSettleModernAnimation("paused", 0, 260), true);
    assert.equal(shouldSettleModernAnimation("running", 16, 260), false);
    assert.equal(shouldSettleModernAnimation("finished", 0, 260), false);
    assert.equal(shouldSettleModernAnimation("running", 0, Number.POSITIVE_INFINITY), false);
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
    assert.match(command, /@keyframes\s+modern-command-sheen/);
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

test("layout reset animates both the command feedback and mounted panel positions", () => {
    const tabbed = read("ModernTabbedClickGui.svelte");
    const command = read("ModernCommandBar.svelte");
    const panel = read("ModernPanel.svelte");

    assert.match(tabbed, /resetVersion=\{resetLayoutVersion\}/);
    assert.match(command, /#key\s+resetVersion/);
    assert.match(command, /@keyframes\s+modern-reset-confirm/);
    assert.match(panel, /class:resetting/);
    assert.match(
        panel,
        /\.panel\.resetting[\s\S]*transition:[\s\S]*left[\s\S]*top/,
    );
});

test("panel expansion and module state changes use finite accent sweeps", () => {
    const command = read("ModernCommandBar.svelte");
    const panel = read("ModernPanel.svelte");
    const module = read("ModernModule.svelte");

    assert.match(command, /@keyframes\s+modern-command-sheen/);
    assert.match(panel, /@keyframes\s+modern-panel-expand-sweep/);
    assert.match(module, /#key\s+liveEnabled/);
    assert.match(module, /@keyframes\s+modern-module-toggle-sweep/);
    assert.match(module, /@keyframes\s+modern-state-label-enter/);
    assert.doesNotMatch(command, /modern-command-sheen[\s\S]{0,160}infinite/);
    assert.doesNotMatch(panel, /modern-panel-expand-sweep[\s\S]{0,160}infinite/);
    assert.doesNotMatch(module, /modern-module-toggle-sweep[\s\S]{0,160}infinite/);
});

test("keyboard selection and pointer feedback move without hover zoom", () => {
    const search = read("ModernSearch.svelte");
    const settings = read("ModernSettings.svelte");

    assert.match(search, /\.result\.selected::before/);
    assert.match(search, /\.result:hover[\s\S]*translateX/);
    assert.match(settings, /\.theme-option:hover:not\(:disabled\)[\s\S]*translateY/);
    assert.match(settings, /\.setting-card:hover[\s\S]*translateY/);
    assert.doesNotMatch(settings, /:hover[^{]*\{[^}]*scale\(/);
});

test("Modern keeps the game visible beneath a centered command pill", () => {
    const tabbed = read("ModernTabbedClickGui.svelte");
    const command = read("ModernCommandBar.svelte");
    const shellBlock = cssBlock(tabbed, ".modern-clickgui");
    const commandBlock = cssBlock(command, ".command-bar");

    assert.match(shellBlock, /background:\s*transparent;/);
    assert.doesNotMatch(tabbed, /\.modern-clickgui::before/);
    assert.match(commandBlock, /left:\s*0;/);
    assert.match(commandBlock, /right:\s*0;/);
    assert.match(
        commandBlock,
        /width:\s*min\(960px,\s*calc\(100%\s*-\s*32px\)\);/,
    );
    assert.match(commandBlock, /margin-inline:\s*auto;/);
    assert.match(commandBlock, /border-radius:\s*999px;/);
});

test("transient sheen layers settle hidden instead of tinting controls", () => {
    const command = read("ModernCommandBar.svelte");
    const module = read("ModernModule.svelte");
    const panel = read("ModernPanel.svelte");

    for (const block of [
        cssBlock(command, ".command-bar::after"),
        cssBlock(module, ".toggle-sweep"),
        cssBlock(panel, ".header::after"),
    ]) {
        assert.match(block, /opacity:\s*0;/);
        assert.match(block, /transform:\s*translateX\(/);
    }
});

test("essential Modern state stays visible when an animation timeline stalls", () => {
    const command = read("ModernCommandBar.svelte");
    const module = read("ModernModule.svelte");
    const panel = read("ModernPanel.svelte");
    const search = read("ModernSearch.svelte");
    const settings = read("ModernSettings.svelte");
    const tabbed = read("ModernTabbedClickGui.svelte");

    const visibleAtRestKeyframes = [
        [command, "@keyframes modern-command-enter"],
        [command, "@keyframes modern-command-item-enter"],
        [module, "@keyframes settings-open"],
        [module, "@keyframes modern-module-enter"],
        [module, "@keyframes modern-setting-enter"],
        [module, "@keyframes modern-state-label-enter"],
        [panel, "@keyframes modern-panel-enter"],
        [search, "@keyframes results-enter"],
        [search, "@keyframes modern-search-result-enter"],
        [search, "@keyframes modern-search-control-enter"],
        [settings, "@keyframes settings-enter"],
        [settings, "@keyframes modern-settings-section-enter"],
        [settings, "@keyframes modern-theme-option-enter"],
        [settings, "@keyframes modern-setting-card-enter"],
        [settings, "@keyframes modern-selection-confirm"],
        [tabbed, "@keyframes modern-view-enter"],
    ];

    for (const [source, keyframe] of visibleAtRestKeyframes) {
        assert.doesNotMatch(cssBlock(source, keyframe), /opacity:\s*0;/);
    }

    assert.doesNotMatch(cssBlock(panel, ".modules"), /transition:/);
    assert.doesNotMatch(cssBlock(panel, ".expand-toggle svg"), /transition:/);
    assert.doesNotMatch(cssBlock(command, ".tabs::before"), /transition:/);
    assert.doesNotMatch(cssBlock(command, ".search-region"), /transition:/);
    assert.doesNotMatch(cssBlock(search, ".result::before"), /transition:/);
    assert.match(tabbed, /getAnimations\(\{subtree:\s*true\}\)/);
    assert.match(tabbed, /animation\.finish\(\)/);
});
