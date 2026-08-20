import assert from "node:assert/strict";
import test from "node:test";

import {
    addMerchantTradeFilter,
    createEmptyMerchantTradeFilter,
    isMerchantTradeFilterActive,
    moveMerchantTradeFilter,
    normalizeMerchantTradeFilters,
    removeMerchantTradeFilter,
    searchMerchantRegistryItems,
    toggleMerchantTradeFilterItem,
} from "../src/routes/clickgui/setting/merchant/merchantTradeEditorModel.ts";

const breadTrade = {
    inputA: ["minecraft:emerald"],
    inputB: [],
    outputs: ["minecraft:bread"],
};

const lanternTrade = {
    inputA: ["minecraft:emerald"],
    inputB: ["minecraft:iron_nugget"],
    outputs: ["minecraft:lantern"],
};

test("adding a trade creates a fresh empty row without changing existing rules", () => {
    const original = [breadTrade];

    const result = addMerchantTradeFilter(original);

    assert.deepEqual(result, [breadTrade, createEmptyMerchantTradeFilter()]);
    assert.deepEqual(original, [breadTrade]);
    assert.notEqual(result[0], original[0]);
    assert.notEqual(result[1].inputA, result[1].outputs);
});

test("removing and reordering trade rows preserves their configured item alternatives", () => {
    const rules = [breadTrade, lanternTrade];

    const moved = moveMerchantTradeFilter(rules, 1, 0);
    const remaining = removeMerchantTradeFilter(moved, 1);

    assert.deepEqual(moved, [lanternTrade, breadTrade]);
    assert.deepEqual(remaining, [lanternTrade]);
    assert.deepEqual(rules, [breadTrade, lanternTrade]);
});

test("selecting another item replaces the slot and selecting it again clears the slot", () => {
    const duplicateInput = [{...breadTrade, inputA: ["minecraft:emerald", "minecraft:emerald"]}];

    const added = toggleMerchantTradeFilterItem(duplicateInput, 0, "inputA", "minecraft:diamond");
    const removed = toggleMerchantTradeFilterItem(added, 0, "inputA", "minecraft:diamond");

    assert.deepEqual(added[0].inputA, ["minecraft:diamond"]);
    assert.deepEqual(removed[0].inputA, []);
    assert.deepEqual(duplicateInput[0].inputA, ["minecraft:emerald", "minecraft:emerald"]);
});

test("search matches every query term across localized names and identifiers", () => {
    const items = [
        {value: "minecraft:iron_ingot", name: "Iron Ingot", icon: undefined},
        {value: "minecraft:raw_iron", name: "Raw Iron", icon: undefined},
        {value: "minecraft:emerald", name: "Emerald", icon: undefined},
    ];

    assert.deepEqual(
        searchMerchantRegistryItems(items, "iron ingot").map(item => item.value),
        ["minecraft:iron_ingot"],
    );
    assert.deepEqual(
        searchMerchantRegistryItems(items, "MINECRAFT emerald").map(item => item.value),
        ["minecraft:emerald"],
    );
    assert.deepEqual(searchMerchantRegistryItems(items, "   "), items);
});

test("normalization removes invalid and duplicate identifiers while retaining row order", () => {
    const normalized = normalizeMerchantTradeFilters([
        {
            inputA: ["minecraft:emerald", "minecraft:diamond", "minecraft:emerald", "", 42],
            inputB: undefined,
            outputs: [" minecraft:bread ", "minecraft:book", "minecraft:bread"],
        },
        null,
        lanternTrade,
    ]);

    assert.deepEqual(normalized, [breadTrade, lanternTrade]);
});

test("a row is active only when input A and output alternatives both exist", () => {
    assert.equal(isMerchantTradeFilterActive(breadTrade), true);
    assert.equal(isMerchantTradeFilterActive({...breadTrade, inputA: []}), false);
    assert.equal(isMerchantTradeFilterActive({...breadTrade, outputs: []}), false);
    assert.equal(isMerchantTradeFilterActive({...breadTrade, inputB: []}), true);
});

test("serialized rules survive a collapsed editor remount without transient picker state", () => {
    const configured = [breadTrade, lanternTrade];
    const persisted = JSON.stringify(configured);

    const remounted = normalizeMerchantTradeFilters(JSON.parse(persisted));

    assert.deepEqual(remounted, configured);
    assert.equal(persisted.includes("search"), false);
    assert.equal(persisted.includes("drawer"), false);
});
