import assert from "node:assert/strict";
import test from "node:test";

import {
    createHudThemeSession,
    parseHudVisualTheme,
    replaceHudVisualTheme,
} from "../src/routes/hud/theme/hudThemeState.ts";

function classicSettings() {
    return {
        name: "HUD",
        valueType: "CONFIGURABLE",
        value: [
            {
                name: "Blur",
                valueType: "BOOLEAN",
                value: true,
                description: undefined,
                key: "liquidbounce.module.hud.blur",
            },
            {
                name: "Theme",
                valueType: "CHOOSE",
                value: "Classic",
                description: "Selects the HUD visual theme.",
                key: "liquidbounce.module.hud.theme",
                choices: ["Classic", "Modern"],
            },
        ],
        description: undefined,
        key: "liquidbounce.module.hud",
    };
}

function currentState(session) {
    let current;
    const unsubscribe = session.subscribe(value => {
        current = value;
    });
    unsubscribe();
    return current;
}

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((nextResolve, nextReject) => {
        resolve = nextResolve;
        reject = nextReject;
    });

    return {promise, resolve, reject};
}

test("parses Classic and Modern while defaulting missing or unknown values to Modern", () => {
    assert.equal(parseHudVisualTheme(classicSettings()), "Classic");

    const modern = replaceHudVisualTheme(classicSettings(), "Modern");
    assert.equal(parseHudVisualTheme(modern), "Modern");

    assert.equal(parseHudVisualTheme({...classicSettings(), value: []}), "Modern");
    assert.equal(parseHudVisualTheme(null), "Modern");

    modern.value.find(setting => setting.name === "Theme").value = "Future";
    assert.equal(parseHudVisualTheme(modern), "Modern");
});

test("replaces only Theme without mutating serialized HUD settings", () => {
    const original = classicSettings();
    const originalBlur = original.value[0];

    const updated = replaceHudVisualTheme(original, "Modern");

    assert.notEqual(updated, original);
    assert.notEqual(updated.value, original.value);
    assert.equal(updated.value[0], originalBlur);
    assert.equal(original.value[1].value, "Classic");
    assert.equal(updated.value[1].value, "Modern");
});

test("adds Theme to an older HUD configuration without changing existing settings", () => {
    const original = classicSettings();
    original.value = original.value.filter(setting => setting.name !== "Theme");

    const updated = replaceHudVisualTheme(original, "Classic");

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

test("keeps the mounted HUD theme unchanged until persistence succeeds", async () => {
    const save = deferred();
    const saved = [];
    const session = createHudThemeSession({
        loadSettings: async () => classicSettings(),
        saveSettings: settings => {
            saved.push(settings);
            return save.promise;
        },
    });

    await session.load();
    const selection = session.selectTheme("Modern");

    assert.equal(currentState(session).theme, "Classic");
    assert.equal(currentState(session).saving, true);
    assert.equal(
        saved[0].value.find(setting => setting.name === "Theme").value,
        "Modern",
    );

    save.resolve();
    assert.equal(await selection, true);
    assert.equal(currentState(session).theme, "Modern");
    assert.equal(currentState(session).saving, false);
    assert.equal(currentState(session).saveError, null);
});

test("preserves synchronized HUD values that arrive during a theme save", async () => {
    const save = deferred();
    const session = createHudThemeSession({
        loadSettings: async () => classicSettings(),
        saveSettings: () => save.promise,
    });

    await session.load();
    const selection = session.selectTheme("Modern");
    const synchronized = classicSettings();
    synchronized.value.find(setting => setting.name === "Blur").value = false;
    session.synchronize(synchronized);

    save.resolve();
    assert.equal(await selection, true);
    assert.equal(
        currentState(session).settings.value.find(setting => setting.name === "Blur").value,
        false,
    );
    assert.equal(
        currentState(session).settings.value.find(setting => setting.name === "Theme").value,
        "Modern",
    );
});

test("restores the previous theme, keeps synchronized values, and retries a failed save", async () => {
    let shouldFail = true;
    const session = createHudThemeSession({
        loadSettings: async () => classicSettings(),
        saveSettings: async () => {
            if (shouldFail) {
                throw new Error("Server rejected the HUD update");
            }
        },
    });

    await session.load();
    const synchronized = classicSettings();
    synchronized.value.find(setting => setting.name === "Blur").value = false;
    const selection = session.selectTheme("Modern");
    session.synchronize(synchronized);

    assert.equal(await selection, false);
    const failed = currentState(session);
    assert.equal(failed.theme, "Classic");
    assert.equal(failed.saving, false);
    assert.equal(failed.failedTheme, "Modern");
    assert.equal(
        failed.settings.value.find(setting => setting.name === "Blur").value,
        false,
    );
    assert.match(failed.saveError, /Server rejected the HUD update/);

    shouldFail = false;
    assert.equal(await session.retryThemeSave(), true);
    assert.equal(currentState(session).theme, "Modern");
    assert.equal(currentState(session).saveError, null);
    assert.equal(currentState(session).failedTheme, null);
});

test("coalesces simultaneous loads so two mounted selectors share one request", async () => {
    const load = deferred();
    let loadCount = 0;
    const session = createHudThemeSession({
        loadSettings: () => {
            loadCount += 1;
            return load.promise;
        },
        saveSettings: async () => {},
    });

    const first = session.load();
    const second = session.load();

    assert.equal(loadCount, 1);
    load.resolve(classicSettings());
    assert.equal(await first, true);
    assert.equal(await second, true);
    assert.equal(currentState(session).theme, "Classic");
});

test("exposes a retryable load error and clears it after a successful retry", async () => {
    let shouldFail = true;
    const session = createHudThemeSession({
        loadSettings: async () => {
            if (shouldFail) {
                throw new Error("HUD settings are unavailable");
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
