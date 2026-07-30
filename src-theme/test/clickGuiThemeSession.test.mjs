import assert from "node:assert/strict";
import test from "node:test";

import {
    createClickGuiThemeSession,
    parseClickGuiVisualTheme,
    replaceClickGuiVisualTheme,
} from "../src/routes/clickgui/theme/clickGuiThemeState.ts";

const classicSettings = () => ({
    name: "ClickGUI",
    valueType: "CONFIGURABLE",
    value: [
        {
            name: "Scale",
            valueType: "FLOAT",
            value: 1,
            description: undefined,
            key: "liquidbounce.modules.clickGUI.scale",
            range: {start: 0.5, end: 2},
            suffix: "",
        },
        {
            name: "Theme",
            valueType: "CHOOSE",
            value: "Classic",
            description: "Selects the ClickGUI visual theme.",
            key: "liquidbounce.modules.clickGUI.theme",
            choices: ["Classic", "Modern"],
        },
    ],
    description: undefined,
    key: "liquidbounce.modules.clickGUI",
});

function currentState(session) {
    let current;
    const unsubscribe = session.subscribe(value => {
        current = value;
    });
    unsubscribe();
    return current;
}

test("parses Classic and Modern while defaulting missing or unknown values to Modern", () => {
    assert.equal(parseClickGuiVisualTheme(classicSettings()), "Classic");

    const modern = replaceClickGuiVisualTheme(classicSettings(), "Modern");
    assert.equal(parseClickGuiVisualTheme(modern), "Modern");

    const missing = {...classicSettings(), value: []};
    assert.equal(parseClickGuiVisualTheme(missing), "Modern");

    const unknown = replaceClickGuiVisualTheme(classicSettings(), "Modern");
    unknown.value.find(setting => setting.name === "Theme").value = "Future";
    assert.equal(parseClickGuiVisualTheme(unknown), "Modern");
});

test("replaces only Theme without mutating the serialized ClickGUI settings", () => {
    const original = classicSettings();
    const originalScale = original.value[0];

    const updated = replaceClickGuiVisualTheme(original, "Modern");

    assert.notEqual(updated, original);
    assert.notEqual(updated.value, original.value);
    assert.equal(updated.value[0], originalScale);
    assert.equal(original.value[1].value, "Classic");
    assert.equal(updated.value[1].value, "Modern");
});

test("adds Theme to an older serialized configuration without changing existing settings", () => {
    const original = classicSettings();
    original.value = original.value.filter(setting => setting.name !== "Theme");

    const updated = replaceClickGuiVisualTheme(original, "Classic");

    assert.equal(updated.value[0], original.value[0]);
    assert.deepEqual(updated.value.at(-1), {
        name: "Theme",
        valueType: "CHOOSE",
        value: "Classic",
        description: undefined,
        key: undefined,
        choices: ["Classic", "Modern"],
    });
});

test("keeps the mounted theme unchanged until persistence succeeds", async () => {
    let finishSave;
    const saved = [];
    const session = createClickGuiThemeSession({
        loadSettings: async () => classicSettings(),
        saveSettings: settings => {
            saved.push(settings);
            return new Promise(resolve => {
                finishSave = resolve;
            });
        },
    });

    await session.load();
    const saving = session.selectTheme("Modern");

    assert.equal(currentState(session).theme, "Classic");
    assert.equal(currentState(session).view, "settings");
    assert.equal(currentState(session).saving, true);
    assert.equal(saved[0].value.find(setting => setting.name === "Theme").value, "Modern");

    finishSave();
    assert.equal(await saving, true);
    assert.equal(currentState(session).theme, "Modern");
    assert.equal(currentState(session).saving, false);
    assert.equal(currentState(session).saveError, null);
});

test("preserves synchronized ClickGUI values that arrive during a theme save", async () => {
    let finishSave;
    const session = createClickGuiThemeSession({
        loadSettings: async () => classicSettings(),
        saveSettings: () => new Promise(resolve => {
            finishSave = resolve;
        }),
    });

    await session.load();
    const saving = session.selectTheme("Modern");
    const synchronized = classicSettings();
    synchronized.value.find(setting => setting.name === "Scale").value = 1.4;
    session.synchronize(synchronized);

    finishSave();
    assert.equal(await saving, true);
    assert.equal(
        currentState(session).settings.value.find(setting => setting.name === "Scale").value,
        1.4,
    );
    assert.equal(
        currentState(session).settings.value.find(setting => setting.name === "Theme").value,
        "Modern",
    );
});

test("restores the prior selection and exposes a retryable error after a failed save", async () => {
    let shouldFail = true;
    const session = createClickGuiThemeSession({
        loadSettings: async () => classicSettings(),
        saveSettings: async () => {
            if (shouldFail) {
                throw new Error("Server rejected the update");
            }
        },
    });

    await session.load();
    assert.equal(await session.selectTheme("Modern"), false);

    const failed = currentState(session);
    assert.equal(failed.theme, "Classic");
    assert.equal(failed.view, "settings");
    assert.equal(failed.saving, false);
    assert.equal(failed.failedTheme, "Modern");
    assert.match(failed.saveError, /Server rejected the update/);

    shouldFail = false;
    assert.equal(await session.retryThemeSave(), true);
    assert.equal(currentState(session).theme, "Modern");
    assert.equal(currentState(session).saveError, null);
    assert.equal(currentState(session).failedTheme, null);
});

test("synchronizes external ClickGUI changes without replacing the active view", async () => {
    const session = createClickGuiThemeSession({
        loadSettings: async () => classicSettings(),
        saveSettings: async () => {},
    });

    await session.load();
    session.setView("settings");
    session.synchronize(replaceClickGuiVisualTheme(classicSettings(), "Modern"));

    assert.equal(currentState(session).theme, "Modern");
    assert.equal(currentState(session).view, "settings");
});

test("exposes a retryable load error and clears it after a successful retry", async () => {
    let shouldFail = true;
    const session = createClickGuiThemeSession({
        loadSettings: async () => {
            if (shouldFail) {
                throw new Error("ClickGUI settings are unavailable");
            }

            return classicSettings();
        },
        saveSettings: async () => {},
    });

    assert.equal(await session.load(), false);
    assert.equal(currentState(session).loading, false);
    assert.match(currentState(session).loadError, /settings are unavailable/);

    shouldFail = false;
    assert.equal(await session.load(), true);
    assert.equal(currentState(session).theme, "Classic");
    assert.equal(currentState(session).loadError, null);
});
