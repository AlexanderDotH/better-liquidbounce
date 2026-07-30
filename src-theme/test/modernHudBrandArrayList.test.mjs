import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const hudRoot = new URL("../src/routes/hud/", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, hudRoot), "utf8");
}

function ruleBody(styles, selector) {
    const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = styles.match(new RegExp(`${escapedSelector}\\s*\\{([\\s\\S]*?)\\n\\}`));

    assert.ok(match, `Missing CSS rule for ${selector}`);
    return match[1];
}

test("Modern watermark is a standalone official LiquidBounce badge", () => {
    const watermark = read("themes/modern/ModernWatermark.svelte");

    assert.match(watermark, /class="modern-watermark"\s+aria-label="LiquidBounce"/);
    assert.match(watermark, /src="\/img\/menu\/icon-liquidbounce\.svg"/);
    assert.match(watermark, /\.modern-watermark[\s\S]*width:\s*36px/);
    assert.match(watermark, /\.modern-watermark[\s\S]*height:\s*36px/);
    assert.match(watermark, /background:\s*#4677ff/);
    assert.doesNotMatch(watermark, /brand-copy|status-dot|Modern HUD/);
});

test("ArrayList separates module names from optional mode tags", () => {
    const arrayList = read("elements/ArrayList.svelte");

    assert.match(arrayList, /<span class="module-name">[\s\S]*convertToSpacedString\(name\)[\s\S]*<\/span>/);
    assert.match(arrayList, /<span class="tag">\{tag\}<\/span>/);
    assert.doesNotMatch(arrayList, /<span class="tag">\s+\{tag\}<\/span>/);
});

test("Modern ArrayList width includes the visual mode capsule", () => {
    const arrayList = read("elements/ArrayList.svelte");

    assert.match(arrayList, /export let variant:\s*"classic" \| "modern" = "classic"/);
    assert.match(arrayList, /MODERN_TAG_GAP_PX\s*=\s*6/);
    assert.match(arrayList, /MODERN_TAG_HORIZONTAL_PADDING_PX\s*=\s*12/);
    assert.match(arrayList, /getTextWidth\(visibleTag,\s*MODERN_TAG_FONT\)/);
    assert.match(arrayList, /MODERN_TAG_GAP_PX\s*\+\s*MODERN_TAG_HORIZONTAL_PADDING_PX/);
    assert.match(arrayList, /variant\s*!==\s*"modern"/);
});

test("Modern ArrayList pills are borderless and modes use a blue contrast capsule", () => {
    const modern = read("themes/modern/modernHud.scss");
    const moduleRule = ruleBody(
        modern,
        '.hud-theme--modern :global([data-component="ArrayList"] .module)',
    );
    const tagRule = ruleBody(
        modern,
        '.hud-theme--modern :global([data-component="ArrayList"] .tag)',
    );

    assert.match(moduleRule, /display:\s*(?:inline-)?flex/);
    assert.match(moduleRule, /border:\s*(?:0|none)/);
    assert.doesNotMatch(moduleRule, /border-left/);
    assert.match(tagRule, /background:\s*#4677ff/);
    assert.match(tagRule, /color:\s*#(?:fff|ffffff)/i);
    assert.match(tagRule, /border-radius:\s*999px/);
});

test("Classic ArrayList retains its established left stripe and tag color", () => {
    const arrayList = read("elements/ArrayList.svelte");

    assert.match(arrayList, /\.module[\s\S]*border-left:\s*solid 4px var\(--arraylist-border-color\)/);
    assert.match(arrayList, /\.tag[\s\S]*color:\s*var\(--arraylist-tag-color\)/);
    assert.doesNotMatch(arrayList, /\.tag\s*\{[^}]*margin-left/);
});
