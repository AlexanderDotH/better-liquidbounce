import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import {readSourceWithStyles} from "../themeSource.mjs";

const settingsRoot = new URL("../../src/routes/clickgui/setting/", import.meta.url);

function readSetting(relativePath) {
    return readSourceWithStyles(new URL(relativePath, settingsRoot));
}

function assertFallbacks(relativePath, expectedFallbacks) {
    const source = readSetting(relativePath);

    for (const fallback of expectedFallbacks) {
        assert.match(
            source,
            new RegExp(`var\\(${fallback.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\$&")}\\)`),
            `${relativePath} should retain the Classic fallback in var(${fallback})`,
        );
    }
}

test("shared controls expose themeable geometry with Classic fallbacks", () => {
    assertFallbacks("common/Dropdown.svelte", [
        "--clickgui-control-padding, 6px 10px",
        "--clickgui-dropdown-radius, 3px",
        "--clickgui-control-font-size, 12px",
        "--clickgui-dropdown-border-width, 1px",
        "--clickgui-control-transition-duration, .2s",
    ]);
    assertFallbacks("common/SettingButton.svelte", [
        "--clickgui-control-padding, 6px 10px",
        "--clickgui-control-radius, 3px",
        "--clickgui-control-font-size, 12px",
        "--clickgui-control-transition-duration, .2s",
    ]);
    assertFallbacks("common/Switch.svelte", [
        "--clickgui-control-font-size, 12px",
        "--clickgui-switch-track-radius, 4px",
        "--clickgui-switch-thumb-size, 12px",
        "--clickgui-switch-transition-duration, .4s",
    ]);
});

test("inputs, binds, chips, and sliders retain their Classic dimensions as fallbacks", () => {
    for (const relativePath of [
        "TextSetting.svelte",
        "VectorSetting.svelte",
        "FileSetting.svelte",
        "list/MutableListSetting.svelte",
    ]) {
        assertFallbacks(relativePath, [
            "--clickgui-control-radius, 3px",
            "--clickgui-control-font-size, 12px",
            "--clickgui-control-border-width, 2px",
            "--clickgui-control-transition-duration, .2s",
        ]);
    }

    for (const relativePath of [
        "KeySetting.svelte",
        "bind/BindSetting.svelte",
    ]) {
        assertFallbacks(relativePath, [
            "--clickgui-control-radius, 3px",
            "--clickgui-control-font-size, 12px",
            "--clickgui-control-border-width, 2px",
            "--clickgui-bind-padding, 4px",
        ]);
    }

    assertFallbacks("MultiChooseSetting.svelte", [
        "--clickgui-chip-radius, 3px",
        "--clickgui-chip-padding, 3px 6px",
        "--clickgui-control-transition-duration, 0.2s",
    ]);
    assertFallbacks("nouislider.scss", [
        "--clickgui-slider-handle-size, 12px",
        "--clickgui-slider-track-height, 2px",
        "--clickgui-slider-track-margin, 10px 0",
    ]);
});

test("nested wrappers expose shared spacing and expansion tokens", () => {
    for (const relativePath of [
        "ConfigurableSetting.svelte",
        "TogglableSetting.svelte",
    ]) {
        assertFallbacks(relativePath, [
            "--clickgui-setting-padding, 7px 0",
            "--clickgui-setting-expanded-gap, 10px",
            "--clickgui-setting-group-border-width, 2px",
            "--clickgui-setting-group-padding, 7px",
            "--clickgui-setting-transition-duration, .2s",
        ]);
    }
});

test("the remaining setting families consume the same shared tokens", () => {
    for (const relativePath of [
        "BooleanSetting.svelte",
        "ChooseSetting.svelte",
        "ColorSetting.svelte",
        "CurveSetting.svelte",
        "list/RegistryMutableListSetting.svelte",
    ]) {
        assertFallbacks(relativePath, [
            "--clickgui-setting-padding, 7px 0",
        ]);
    }

    assertFallbacks("ColorSetting.svelte", [
        "--clickgui-control-radius, 3px",
        "--clickgui-control-font-size, 12px",
    ]);
    assertFallbacks("CurveSetting.svelte", [
        "--clickgui-control-font-size, 12px",
        "--clickgui-setting-transition-duration, 0.2s",
        "--clickgui-setting-expanded-gap, 10px",
    ]);
    assertFallbacks("list/RegistryMutableListSetting.svelte", [
        "--clickgui-control-font-size, 12px",
        "--clickgui-setting-transition-duration, .2s",
        "--clickgui-setting-expanded-gap, 10px",
    ]);
    assertFallbacks("list/ListItem.svelte", [
        "--clickgui-control-font-size, 12px",
        "--clickgui-setting-control-gap, 5px",
    ]);
});
