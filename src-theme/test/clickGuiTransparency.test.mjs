import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const clickGuiRoot = new URL("../src/routes/clickgui/", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, clickGuiRoot), "utf8");
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

test("the shared ClickGUI host never paints over the game", () => {
    const host = read("theme/ClickGuiThemeHost.svelte");

    assert.match(cssBlock(host, ".clickgui-theme-host {"), /background:\s*transparent;/);
    assert.match(cssBlock(host, ".theme-status {"), /background:\s*transparent;/);
    assert.doesNotMatch(host, /background:\s*#090b0f/);
});

test("Classic and Modern ClickGUI shells leave uncovered pixels transparent", () => {
    const classic = read("TabbedClickGui.svelte");
    const modern = read("themes/modern/ModernTabbedClickGui.svelte");

    const classicShell = cssBlock(classic, ".tabbed-clickgui");
    const modernShell = cssBlock(modern, ".modern-clickgui");

    assert.match(classicShell, /background-color:\s*transparent;/);
    assert.doesNotMatch(classicShell, /--clickgui-overlay-background-color/);
    assert.match(modernShell, /background:\s*transparent;/);
});
