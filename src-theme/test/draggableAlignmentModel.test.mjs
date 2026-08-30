import assert from "node:assert/strict";
import test from "node:test";

import {
    clampAlignmentOffset,
    findMagneticSnap,
    generateAlignmentStyle,
    horizontalAnchorZone,
    horizontalCenter,
    snapHudEditorGrid,
    verticalAnchorZone,
    verticalCenter,
} from "../src/routes/hud/elements/draggableAlignmentModel.ts";
import {
    beginHudEditorDrag,
    moveHudEditorDrag,
} from "../src/routes/hud/elements/draggableInteractionModel.ts";

test("draggable alignment geometry preserves every anchor convention", () => {
    assert.equal(horizontalCenter("Left", 10, 20, 200), 20);
    assert.equal(horizontalCenter("Right", 10, 20, 200), 180);
    assert.equal(horizontalCenter("Center", 10, 20, 200), 120);
    assert.equal(horizontalCenter("CenterTranslated", 10, 20, 200), 110);
    assert.equal(verticalCenter("Top", 8, 10, 100), 13);
    assert.equal(verticalCenter("Bottom", 8, 10, 100), 87);
});

test("magnetic snapping prefers the closest in-bounds target and keeps its guide identity", () => {
    const snap = findMagneticSnap({
        center: 42,
        size: 10,
        viewportSize: 100,
        threshold: 5,
        targets: [
            {id: "far", points: [34]},
            {id: "near", points: [40]},
        ],
    });

    assert.deepEqual(snap, {center: 40, guide: 40, targetId: "near"});
    assert.deepEqual(findMagneticSnap({
        center: 49,
        size: 10,
        viewportSize: 100,
        threshold: 5,
        targets: [],
    }), {center: 50, guide: 50});
});

test("drag interaction translates pointer movement into the same alignment offsets", () => {
    const alignment = {
        horizontalAlignment: "Left",
        verticalAlignment: "Top",
        horizontalOffset: 10,
        verticalOffset: 20,
    };
    const started = beginHudEditorDrag(alignment, {
        cursorX: 20,
        cursorY: 30,
        elementWidth: 20,
        elementHeight: 10,
        hudWidth: 100,
        hudHeight: 90,
    });
    assert.deepEqual(started, {
        pointerCenterOffsetX: 0,
        pointerCenterOffsetY: -5,
        horizontalZone: "left",
        verticalZone: "center",
    });

    const moved = moveHudEditorDrag({
        cursorX: 70,
        cursorY: 60,
        pointerCenterOffsetX: 0,
        pointerCenterOffsetY: -5,
        elementWidth: 20,
        elementHeight: 10,
        hudWidth: 100,
        hudHeight: 90,
        gridSize: 5,
        gridIgnored: false,
    });
    assert.deepEqual(moved.alignment, {
        horizontalAlignment: "Right",
        verticalAlignment: "CenterTranslated",
        horizontalOffset: 20,
        verticalOffset: 10,
    });
});

test("zones, clamping, snapping, and CSS output retain editor boundary behavior", () => {
    assert.equal(horizontalAnchorZone(20, 90), "left");
    assert.equal(horizontalAnchorZone(70, 90), "right");
    assert.equal(verticalAnchorZone(45, 90), "center");
    assert.equal(clampAlignmentOffset(99, "Left", 20, 100), 80);
    assert.equal(clampAlignmentOffset(-99, "CenterTranslated", 20, 100), -40);
    assert.equal(snapHudEditorGrid(13, 5, false), 15);
    assert.equal(snapHudEditorGrid(13, 5, true), 13);
    assert.match(
        generateAlignmentStyle({
            horizontalAlignment: "CenterTranslated",
            verticalAlignment: "Bottom",
            horizontalOffset: 4,
            verticalOffset: 7,
        }),
        /left: calc\(50% \+ 4px\);[\s\S]*bottom: 7px;[\s\S]*translate\(-50%, 0\)/,
    );
});
