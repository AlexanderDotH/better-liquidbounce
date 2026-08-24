import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

import {
    copyTextSelection,
    cutTextSelection,
    pasteTextSelection,
} from "../src/routes/clickgui/setting/common/textEditing.ts";
import {
    createModernClickGuiPreviewState,
    routeModernClickGuiPreviewRequest,
} from "../src/dev/modern-clickgui-preview/previewFixture.ts";

const themeRoot = new URL("../", import.meta.url);

function source(relativePath) {
    return readFileSync(new URL(relativePath, themeRoot), "utf8");
}

function jsonRequest(url, method, body) {
    return new Request(url, {
        method,
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body),
    });
}

test("copy keeps the field value and writes the selected text", () => {
    const result = copyTextSelection("world-seed", {start: 0, end: 5});

    assert.deepEqual(result, {
        value: "world-seed",
        selection: {start: 0, end: 5},
        clipboardText: "world",
    });
});

test("cut writes the selected text and removes it from the field", () => {
    const result = cutTextSelection("world-seed", {start: 5, end: 0});

    assert.deepEqual(result, {
        value: "-seed",
        selection: {start: 0, end: 0},
        clipboardText: "world",
    });
});

test("paste replaces the current selection and places the caret after the pasted text", () => {
    const result = pasteTextSelection("old-seed", {start: 0, end: 3}, "new");

    assert.deepEqual(result, {
        value: "new-seed",
        selection: {start: 3, end: 3},
    });
});

test("the preview clipboard supports the same read/write contract as Minecraft", async () => {
    const state = createModernClickGuiPreviewState();
    const writeResponse = await routeModernClickGuiPreviewRequest(
        state,
        jsonRequest("http://preview.local/api/v1/client/clipboard", "PUT", {text: "copied seed"}),
    );
    const readResponse = await routeModernClickGuiPreviewRequest(
        state,
        new Request("http://preview.local/api/v1/client/clipboard"),
    );

    assert.equal(writeResponse.status, 204);
    assert.deepEqual(await readResponse.json(), {text: "copied seed"});
});

test("every editable ClickGUI field uses the CEF text-input action", () => {
    const editableComponents = [
        "src/routes/clickgui/Search.svelte",
        "src/routes/clickgui/setting/ColorSetting.svelte",
        "src/routes/clickgui/setting/TextSetting.svelte",
        "src/routes/clickgui/setting/VectorSetting.svelte",
        "src/routes/clickgui/setting/common/ValueInput.svelte",
        "src/routes/clickgui/setting/list/MutableListSetting.svelte",
        "src/routes/clickgui/setting/list/SearchableList.svelte",
        "src/routes/clickgui/setting/merchant/MerchantItemDrawer.svelte",
        "src/routes/clickgui/tabs/hud_editor/drawer/ComponentDrawer.svelte",
        "src/routes/clickgui/themes/modern/ModernSearch.svelte",
    ];

    for (const relativePath of editableComponents) {
        assert.match(
            source(relativePath),
            /use:cefTextInput=/,
            `${relativePath} must route keyboard and clipboard input through cefTextInput`,
        );
    }
});

test("text fields keep newer local edits while an older save is being acknowledged", () => {
    const textSetting = source("src/routes/clickgui/setting/TextSetting.svelte");

    assert.doesNotMatch(
        textSetting,
        /listen\("valueChanged"/,
        "TextSetting must not overwrite its current draft with a per-save WebSocket echo",
    );
    assert.match(
        textSetting,
        /function handleChange\(value: string\)[\s\S]*?cSetting\.value = value;[\s\S]*?dispatch\("change"\);/,
        "TextSetting must keep updating the parent-owned settings draft",
    );
});

test("the CEF action routes copy, cut, and paste through the Minecraft clipboard", () => {
    const action = source("src/routes/clickgui/setting/common/cefTextInput.ts");

    assert.match(action, /event\.keyCode === KEY_C[\s\S]*?copyTextSelection[\s\S]*?setClipboardText/);
    assert.match(action, /event\.keyCode === KEY_X[\s\S]*?cutTextSelection[\s\S]*?setClipboardText/);
    assert.match(action, /event\.keyCode === KEY_V[\s\S]*?getClipboardText[\s\S]*?pasteTextSelection/);
});

test("the CEF action releases focus on outside clicks and window blur", () => {
    const action = source("src/routes/clickgui/setting/common/cefTextInput.ts");

    assert.match(action, /document\.addEventListener\("pointerdown", handlePointerDown, true\)/);
    assert.match(action, /window\.addEventListener\("blur", releaseFocus\)/);
    assert.match(action, /screenActive = isActiveKeyboardScreen\(event\.screen, options\)/);
    assert.doesNotMatch(action, /if \(event\.screen !== undefined\)/);
    assert.match(
        action,
        /function handlePointerDown[\s\S]*?event\.composedPath\(\)\.includes\(node\)[\s\S]*?releaseFocus\(\)/,
    );
});
