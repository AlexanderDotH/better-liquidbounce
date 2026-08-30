import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const inventoryRoot = new URL("../../../src/routes/hud/elements/inventory/", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, inventoryRoot), "utf8");
}

test("inventory panels expose an additive Modern presentation with optional labels", () => {
    const inventory = read("GenericPlayerInventory.svelte");
    const statistics = read("InventoryStatistics.svelte");

    assert.match(inventory, /export let variant:\s*"classic"\s*\|\s*"modern"\s*=\s*"classic"/);
    assert.match(inventory, /export let label:\s*string\s*\|\s*undefined\s*=\s*undefined/);
    assert.match(inventory, /\{#if variant === "modern" && label\}/);
    assert.match(inventory, /class="inventory-label"/);
    assert.match(inventory, /<ItemStackView\s+\{stack\}\s+\{variant\}\s*\/>/);

    assert.match(statistics, /export let variant:\s*"classic"\s*\|\s*"modern"\s*=\s*"classic"/);
    assert.match(statistics, /export let label:\s*string\s*\|\s*undefined\s*=\s*undefined/);
    assert.match(statistics, /<GenericPlayerInventory[\s\S]*\{variant\}[\s\S]*\{label\}/);
});

test("Modern inventory slots shrink to 28px with a quiet two-pixel grid", () => {
    const inventory = read("GenericPlayerInventory.svelte");
    const itemStack = read("ItemStackView.svelte");

    assert.match(inventory, /\.inventory--modern\s*\{[\s\S]*gap:\s*2px/);
    assert.match(inventory, /\.inventory--modern\s*\{[\s\S]*border:\s*0/);
    assert.match(itemStack, /\.item-stack\s*\{[\s\S]*width:\s*32px[\s\S]*height:\s*32px/);
    assert.match(itemStack, /\.item-stack--modern\s*\{[\s\S]*width:\s*28px[\s\S]*height:\s*28px/);
});

test("Modern presentation keeps item texture, count, enchantment, and durability rendering", () => {
    const itemStack = read("ItemStackView.svelte");

    assert.match(itemStack, /itemTextureUrl\(identifier\)/);
    assert.match(itemStack, /class="mask"/);
    assert.match(itemStack, /class="durability-bar"/);
    assert.match(itemStack, /class="count"/);
    assert.match(itemStack, /\{count\}/);
});
