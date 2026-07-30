import assert from "node:assert/strict";
import test from "node:test";

import {
    MODERN_MODULE_EXPANSION_PREFIX,
    clampSearchSelection,
    modernModuleExpansionKey,
    moveSearchSelection,
    readSearchBarAutoFocus,
} from "../src/routes/clickgui/themes/modern/model/modernInteractionState.ts";

test("Modern module expansion state is isolated from Classic persistence", () => {
    assert.equal(
        modernModuleExpansionKey("KillAura"),
        `${MODERN_MODULE_EXPANSION_PREFIX}KillAura`,
    );
    assert.equal(
        modernModuleExpansionKey("ClickGUI"),
        "clickgui.modern.module.v1.ClickGUI",
    );
});

test("search selection wraps in both directions", () => {
    assert.equal(moveSearchSelection(0, 4, 1), 1);
    assert.equal(moveSearchSelection(3, 4, 1), 0);
    assert.equal(moveSearchSelection(0, 4, -1), 3);
    assert.equal(moveSearchSelection(2, 4, -1), 1);
});

test("search selection remains valid as results change", () => {
    assert.equal(clampSearchSelection(5, 3), 2);
    assert.equal(clampSearchSelection(-2, 3), 0);
    assert.equal(clampSearchSelection(1, 0), 0);
    assert.equal(moveSearchSelection(7, 0, 1), 0);
});

test("ClickGUI search autofocus accepts only boolean setting values", () => {
    const configurable = {
        valueType: "CONFIGURABLE",
        name: "ClickGUI",
        value: [
            {
                valueType: "BOOLEAN",
                name: "SearchBarAutoFocus",
                value: false,
                description: undefined,
                key: undefined,
            },
        ],
        description: undefined,
        key: undefined,
    };

    assert.equal(readSearchBarAutoFocus(configurable), false);
    configurable.value[0].value = "false";
    assert.equal(readSearchBarAutoFocus(configurable), true);
    configurable.value = [];
    assert.equal(readSearchBarAutoFocus(configurable, false), false);
});
