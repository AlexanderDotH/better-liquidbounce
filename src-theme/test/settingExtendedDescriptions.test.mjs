import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import ts from "typescript";

const settingDescriptionSource = readFileSync(
    new URL("../src/routes/clickgui/setting/common/settingDescription.ts", import.meta.url),
    "utf8",
);
const settingDescriptionModule = await import(`data:text/javascript;base64,${Buffer.from(
    ts.transpileModule(settingDescriptionSource, {
        compilerOptions: {module: ts.ModuleKind.ESNext},
    }).outputText,
).toString("base64")}`);
const {
    preferredSettingDescription,
    settingShiftDescription,
} = settingDescriptionModule;

function setting(valueType, description, extendedDescription) {
    return {
        valueType,
        name: "Example",
        value: true,
        description,
        extendedDescription,
        key: "liquidbounce.module.example.setting",
    };
}

test("checkboxes and sliders prefer their extended description", () => {
    assert.equal(settingShiftDescription(setting("BOOLEAN", "Short checkbox", "Detailed checkbox")), "Detailed checkbox");
    assert.equal(settingShiftDescription(setting("INT", "Short slider", "Detailed slider")), "Detailed slider");
    assert.equal(settingShiftDescription(setting("FLOAT_RANGE", "Short range", "Detailed range")), "Detailed range");
});

test("all non-mode settings fall back to their normal description", () => {
    assert.equal(settingShiftDescription(setting("BOOLEAN", "Checkbox help", undefined)), "Checkbox help");
    assert.equal(settingShiftDescription(setting("TEXT", "Text help", "")), "Text help");
    assert.equal(settingShiftDescription(setting("VECTOR3_D", undefined, undefined)), undefined);
});

test("mode choices keep their option-specific dropdown descriptions", () => {
    assert.equal(settingShiftDescription(setting("CHOICE", "Choice help", "Group details")), undefined);
});

test("nested groups expose their description only from their own header", () => {
    for (const valueType of ["CONFIGURABLE", "TOGGLEABLE"]) {
        const nested = setting(valueType, "Group help", "Detailed group help");
        assert.equal(settingShiftDescription(nested), undefined);
        assert.equal(preferredSettingDescription(nested), "Detailed group help");
    }
});

test("the shared setting renderer wires shift hover once for every setting family", () => {
    const source = readFileSync(
        new URL("../src/shared/settings/GenericSetting.svelte", import.meta.url),
        "utf8",
    );

    assert.match(source, /import\s+\{shiftDescription\}\s+from\s+"\.\.\/\.\.\/routes\/clickgui\/setting\/common\/shiftDescription"/);
    assert.match(source, /use:shiftDescription=\{\{\s*getText:\s*\(\)\s*=>\s*settingShiftDescription\(setting\)/);

    for (const component of ["ConfigurableSetting.svelte", "TogglableSetting.svelte"]) {
        const nestedSource = readFileSync(
            new URL(`../src/routes/clickgui/setting/${component}`, import.meta.url),
            "utf8",
        );
        assert.match(nestedSource, /use:shiftDescription=\{\{\s*getText:\s*\(\)\s*=>\s*preferredSettingDescription\(cSetting\)/);
    }
});
