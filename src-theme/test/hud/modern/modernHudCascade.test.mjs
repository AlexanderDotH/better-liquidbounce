import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const modernHud = readFileSync(
    new URL("../../../src/routes/hud/themes/modern/modernHud.scss", import.meta.url),
    "utf8",
);

test("global Modern Target HUD rules cannot override the essential presentation", () => {
    const targetSelectors = modernHud
        .split("\n")
        .filter(line => line.includes('data-component="TargetHud"'));

    assert.ok(targetSelectors.length > 0, "expected legacy Target HUD fallback selectors");
    for (const selector of targetSelectors) {
        assert.match(
            selector,
            /\.targethud:not\(\.targethud--modern\)/,
            `unguarded global selector can override the essential Target HUD: ${selector}`,
        );
    }
});

test("global rich-notification styling excludes the component-scoped module-toggle pill", () => {
    assert.match(
        modernHud,
        /\[data-component="Notifications"\] \.notification:not\(\.module-toggle-notification\)/,
    );
    assert.doesNotMatch(
        modernHud,
        /\[data-component="Notifications"\] \.notification\.module-toggle-notification/,
    );
});

test("global fallback rules cannot override component-scoped TabGUI and inventory variants", () => {
    const tabGuiSelectors = modernHud
        .split("\n")
        .filter(line => line.includes('data-component="TabGui"'));
    const inventorySelectors = modernHud
        .split("\n")
        .filter(line => /data-component="(?:ArmorItems|InventoryStatistics|Inventory|CraftingInventory|EnderChestInventory)"/.test(line));

    assert.ok(tabGuiSelectors.length > 0);
    for (const selector of tabGuiSelectors) {
        assert.match(selector, /\.tabgui:not\(\.modern\)/);
    }

    assert.ok(inventorySelectors.length > 0);
    for (const selector of inventorySelectors) {
        assert.match(
            selector,
            /\.(?:inventory|item-stack):not\(\.(?:inventory|item-stack)--modern\)/,
        );
    }
});

test("Modern global selectors stay behind the theme boundary", () => {
    const topLevelSelectors = modernHud
        .split("\n")
        .filter(line => line.startsWith(".") && line.endsWith("{"));

    assert.ok(topLevelSelectors.length > 0);
    for (const selector of topLevelSelectors) {
        assert.match(selector, /^\.hud-theme--modern\b/);
    }
});
