import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const themeRoot = new URL("../../../", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, themeRoot), "utf8");
}

test("keeps expiring target and notification fixtures visible for visual QA", () => {
    const runtime = read("src/dev/modern-hud-preview/previewRuntime.ts");
    const snapshotStart = readNumericConstant(
        runtime,
        "MODERN_HUD_SNAPSHOT_START_MS",
    );
    const targetRefresh = readNumericConstant(runtime, "MODERN_HUD_TARGET_REFRESH_MS");
    const notificationRefresh = readNumericConstant(
        runtime,
        "MODERN_HUD_NOTIFICATION_REFRESH_MS",
    );

    assert.ok(snapshotStart > 0);
    assert.ok(snapshotStart < 500);
    assert.ok(targetRefresh > 0);
    assert.ok(targetRefresh < 1_000);
    assert.ok(notificationRefresh > 0);
    assert.ok(notificationRefresh < 3_000);
});

test("waits for asynchronously loaded HUD widgets before broadcasting their snapshot", () => {
    const runtime = read("src/dev/modern-hud-preview/previewRuntime.ts");

    assert.match(
        runtime,
        /scheduleTimeout\(\s*context,\s*\(\) => snapshot\.forEach\(event => emit\(context, event\)\),\s*MODERN_HUD_SNAPSHOT_START_MS\s*\)/,
    );
});

test("installs the mock runtime before dynamically importing the production HUD", () => {
    const main = read("src/dev/modern-hud-preview/main.ts");
    const runtimeInstall = main.indexOf("installModernHudPreviewRuntime");
    const hudImport = main.indexOf('import("../../routes/hud/Hud.svelte")');

    assert.ok(runtimeInstall >= 0);
    assert.ok(hudImport > runtimeInstall);
    assert.match(main, /mount\(Hud,/);
    assert.match(main, /resolveModernHudPreviewFixture/);
    assert.match(main, /new URLSearchParams\(window\.location\.search\)/);
});

test("keeps the preview and its game-like transparency scene out of production entries", () => {
    const app = read("src/App.svelte");
    const main = read("src/main.ts");
    const viteConfig = read("vite.config.ts");
    const productionHtml = read("index.html");
    const previewHtml = read("modern-hud-preview.html");
    const previewStyles = read("src/dev/modern-hud-preview/preview.scss");

    for (const source of [app, main, viteConfig, productionHtml]) {
        assert.doesNotMatch(source, /modern-hud-preview|preview-world/);
    }

    assert.match(previewHtml, /class="preview-world"/);
    assert.match(previewHtml, /src="\/src\/dev\/modern-hud-preview\/main\.ts"/);
    assert.match(previewStyles, /\.preview-world/);
    assert.match(previewStyles, /#preview-app/);
});

function readNumericConstant(source, name) {
    const match = source.match(new RegExp(`${name} = ([\\d_]+)`));
    assert.ok(match, `${name} must be declared`);
    return Number(match[1].replaceAll("_", ""));
}
