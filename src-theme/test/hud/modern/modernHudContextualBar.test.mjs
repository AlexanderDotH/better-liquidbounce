import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

import {
    EMPTY_CONTEXTUAL_BAR,
    clampContextualProgress,
    locatorMarkerPercent,
    locatorRgbColor,
    sortLocatorMarkersForRendering,
    waypointEmoji,
} from "../../../src/routes/hud/elements/hotbar/contextualBarModel.ts";

const themeRoot = new URL("../../../", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, themeRoot), "utf8");
}

test("contextual progress and marker positions remain inside the modern island", () => {
    assert.equal(clampContextualProgress(Number.NaN), 0);
    assert.equal(clampContextualProgress(-0.1), 0);
    assert.equal(clampContextualProgress(0.42), 0.42);
    assert.equal(clampContextualProgress(1.2), 1);

    assert.equal(locatorMarkerPercent(-2), 4);
    assert.equal(locatorMarkerPercent(-1), 4);
    assert.equal(locatorMarkerPercent(0), 50);
    assert.equal(locatorMarkerPercent(1), 96);
    assert.equal(locatorMarkerPercent(2), 96);
});

test("generic waypoint styles have deterministic emoji and tint fallbacks", () => {
    assert.equal(waypointEmoji("minecraft:bowtie"), "🎀");
    assert.equal(waypointEmoji("minecraft:default"), "📍");
    assert.equal(waypointEmoji("custom:unknown"), "📍");
    assert.equal(locatorRgbColor(0x7897d6), "#7897d6");
    assert.equal(locatorRgbColor(-1), "#ffffff");
});

test("locator markers render farthest first so nearby markers stay visible on overlap", () => {
    const nearest = {id: "near", distance: 8};
    const farthest = {id: "far", distance: 120};
    const middle = {id: "middle", distance: 42};
    const original = [nearest, farthest, middle];

    assert.deepEqual(
        sortLocatorMarkersForRendering(original).map(marker => marker.id),
        ["far", "middle", "near"],
    );
    assert.deepEqual(original.map(marker => marker.id), ["near", "far", "middle"]);
});

test("empty contextual state is immutable and clears stale marker data", () => {
    assert.deepEqual(EMPTY_CONTEXTUAL_BAR, {
        mode: "empty",
        progress: 0,
        level: 0,
        cooldown: false,
        markers: [],
    });
    assert.ok(Object.isFrozen(EMPTY_CONTEXTUAL_BAR));
    assert.ok(Object.isFrozen(EMPTY_CONTEXTUAL_BAR.markers));
});

test("socket reconnects notify component-scoped listeners so snapshots can resync", () => {
    const websocket = read("src/integration/ws.ts");

    assert.match(
        websocket,
        /listeners\.get\("socketReady"\)\?\.forEach\(callback => callback\(\)\)/,
    );
});
