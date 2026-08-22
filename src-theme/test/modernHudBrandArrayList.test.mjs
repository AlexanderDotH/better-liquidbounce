import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const hudRoot = new URL("../src/routes/hud/", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, hudRoot), "utf8");
}

function ruleBody(styles, selector) {
    const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = styles.match(new RegExp(`${escapedSelector}\\s*\\{([\\s\\S]*?)\\n\\s*\\}`));

    assert.ok(match, `Missing CSS rule for ${selector}`);
    return match[1];
}

test("Modern watermark is a standalone official LiquidBounce badge", () => {
    const watermark = read("themes/modern/ModernWatermark.svelte");

    assert.match(watermark, /class="modern-watermark"\s+aria-label="LiquidBounce"/);
    assert.match(watermark, /src="img\/menu\/icon-liquidbounce\.svg"/);
    assert.doesNotMatch(watermark, /src="\/img\//);
    assert.match(watermark, /\.modern-watermark[\s\S]*width:\s*36px/);
    assert.match(watermark, /\.modern-watermark[\s\S]*height:\s*36px/);
    assert.match(watermark, /background:\s*#4677ff/);
    assert.doesNotMatch(watermark, /brand-copy|status-dot|Modern HUD/);
});

test("ArrayList separates module names from optional mode tags", () => {
    const arrayList = read("elements/ArrayList.svelte");
    const arrayListModel = read("elements/arrayListModel.ts");

    assert.match(arrayList, /<span class="module-name">\{displayName\}<\/span>/);
    assert.match(arrayList, /<span class="tag">\{visibleTag\}<\/span>/);
    assert.match(arrayList, /\{#if visibleTag !== null\}/);
    assert.match(arrayListModel, /const displayName = formatName\(module\.name\)/);
    assert.match(arrayListModel, /return \{\.\.\.module, displayName, visibleTag, measuredWidth\}/);
});

test("Modern ArrayList width includes the visual mode capsule", () => {
    const arrayList = read("elements/ArrayList.svelte");
    const arrayListModel = read("elements/arrayListModel.ts");

    assert.match(arrayList, /export let variant:\s*ArrayListVariant = "classic"/);
    assert.match(arrayList, /const nextEntries = buildArrayListEntries\(/);
    assert.match(arrayListModel, /MODERN_TAG_GAP_PX\s*=\s*6/);
    assert.match(arrayListModel, /MODERN_TAG_HORIZONTAL_PADDING_PX\s*=\s*12/);
    assert.match(arrayListModel, /MODERN_UNTAGGED_OUTER_PADDING_PX\s*=\s*18/);
    assert.match(arrayListModel, /MODERN_TAGGED_OUTER_PADDING_PX\s*=\s*14/);
    assert.match(arrayListModel, /measureText\(visibleTag,\s*MODERN_TAG_FONT\)/);
    assert.match(arrayListModel, /MODERN_TAG_GAP_PX[\s\S]*MODERN_TAG_HORIZONTAL_PADDING_PX[\s\S]*MODERN_TAGGED_OUTER_PADDING_PX/);
});

test("Modern ArrayList keeps untagged insets and centers tagged mode capsules", () => {
    const modern = read("themes/modern/modernHud.scss");
    const moduleRule = ruleBody(
        modern,
        '.hud-theme--modern :global([data-component="ArrayList"] .module)',
    );
    const taggedModuleRule = ruleBody(
        modern,
        '.hud-theme--modern :global([data-component="ArrayList"] .module.has-visible-tag)',
    );
    const tagRule = ruleBody(
        modern,
        '.hud-theme--modern :global([data-component="ArrayList"] .tag)',
    );

    assert.match(moduleRule, /display:\s*(?:inline-)?flex/);
    assert.match(moduleRule, /padding:\s*5px 9px/);
    assert.match(moduleRule, /border:\s*(?:0|none)/);
    assert.doesNotMatch(moduleRule, /border-left/);
    assert.deepEqual(
        taggedModuleRule.split(";").map(declaration => declaration.trim()).filter(Boolean),
        ["padding-right: 5px"],
    );
    assert.match(moduleRule, /box-shadow:\s*0 7px 18px rgba\(0, 0, 0, 0\.18\)/);
    assert.match(tagRule, /display:\s*inline-flex/);
    assert.match(tagRule, /align-items:\s*center/);
    assert.match(tagRule, /margin-left:\s*0/);
    assert.match(tagRule, /padding:\s*2px 6px/);
    assert.match(tagRule, /background:\s*#4677ff/);
    assert.match(tagRule, /color:\s*#(?:fff|ffffff)/i);
    assert.match(tagRule, /font-size:\s*10px/);
    assert.match(tagRule, /font-weight:\s*600/);
    assert.match(tagRule, /line-height:\s*1\.2/);
    assert.match(tagRule, /border-radius:\s*999px/);
});

test("Classic ArrayList retains its established left stripe and tag color", () => {
    const arrayList = read("elements/ArrayList.svelte");
    const moduleRule = ruleBody(arrayList, ".module");
    const tagRule = ruleBody(arrayList, ".tag");

    assert.match(moduleRule, /border-left:\s*solid 4px var\(--arraylist-border-color\)/);
    assert.match(tagRule, /color:\s*var\(--arraylist-tag-color\)/);
    assert.doesNotMatch(tagRule, /margin-left/);
});
