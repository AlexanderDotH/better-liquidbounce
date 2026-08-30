import assert from "node:assert/strict";
import test from "node:test";

import {
    baritoneEtaLabel,
    baritoneProgressPercent,
    baritoneStatusLabel,
    coordinateValue,
    nextBaritoneTabIndex,
} from "../src/routes/baritone/baritoneDashboardPresentation.ts";

test("Baritone dashboard labels preserve state, ETA, and bounded progress output", () => {
    assert.equal(baritoneStatusLabel("NO_WORLD"), "No world");
    assert.equal(baritoneStatusLabel("ARRIVED"), "Arrived");
    assert.equal(baritoneEtaLabel(null), "—");
    assert.equal(baritoneEtaLabel(125), "2m 5s");
    assert.equal(baritoneEtaLabel(7.6), "8s");
    assert.equal(baritoneProgressPercent(Number.NaN), 0);
    assert.equal(baritoneProgressPercent(-0.2), 0);
    assert.equal(baritoneProgressPercent(1.2), 100);
});

test("coordinate parsing and roving tab navigation retain their edge behavior", () => {
    assert.equal(coordinateValue("-12.5", "X"), -12.5);
    assert.throws(() => coordinateValue("NaN", "X"), /X must be a finite number/);
    assert.equal(nextBaritoneTabIndex("ArrowDown", 10, 10), 0);
    assert.equal(nextBaritoneTabIndex("ArrowUp", 0, 10), 10);
    assert.equal(nextBaritoneTabIndex("Home", 4, 10), 0);
    assert.equal(nextBaritoneTabIndex("End", 4, 10), 10);
    assert.equal(nextBaritoneTabIndex("Enter", 4, 10), null);
});
