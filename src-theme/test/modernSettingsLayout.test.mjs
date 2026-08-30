import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";
import test from "node:test";
import {readSourceWithStyles} from "./themeSource.mjs";

const componentUrl = new URL(
    "../src/routes/clickgui/themes/modern/ModernSettings.svelte",
    import.meta.url,
);

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

test("Global settings cards form compact balanced columns without row gaps", async () => {
    const source = readSourceWithStyles(componentUrl);
    const settingsGrid = cssBlock(source, ".settings-grid");
    const settingCard = cssBlock(source, ".setting-card");

    assert.match(settingsGrid, /column-count:\s*2;/);
    assert.match(settingsGrid, /column-gap:\s*12px;/);
    assert.match(settingsGrid, /column-fill:\s*balance;/);
    assert.doesNotMatch(settingsGrid, /grid-template-columns/);

    assert.match(settingCard, /break-inside:\s*avoid;/);
    assert.match(settingCard, /display:\s*inline-block;/);
    assert.match(settingCard, /width:\s*100%;/);
    assert.match(settingCard, /margin-bottom:\s*12px;/);

    assert.match(
        source,
        /@media\s*\(max-width:\s*800px\)[\s\S]*?\.settings-grid\s*\{[\s\S]*?column-count:\s*1;/,
    );
});
