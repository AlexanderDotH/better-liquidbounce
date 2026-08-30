import assert from "node:assert/strict";
import {existsSync, readFileSync, readdirSync} from "node:fs";
import {relative} from "node:path";
import test from "node:test";

import {
    convertLegacyCodes,
    translateTextColor,
} from "../src/components/text/legacyTextFormatting.ts";

const themeRoot = new URL("../", import.meta.url);
const sourceRoot = new URL("src/", themeRoot);
const routesRoot = new URL("routes/", sourceRoot);
const integrationRoot = new URL("integration/", sourceRoot);

function sourcePath(relativePath) {
    return new URL(relativePath, sourceRoot);
}

function routeSources(directory = routesRoot) {
    return readdirSync(directory, {withFileTypes: true}).flatMap((entry) => {
        const path = new URL(`${entry.name}${entry.isDirectory() ? "/" : ""}`, directory);
        if (entry.isDirectory()) return routeSources(path);
        return /\.(?:svelte|ts)$/.test(entry.name) ? [path] : [];
    });
}

test("legacy text formatting keeps Minecraft colors and formatting state", () => {
    const formatted = convertLegacyCodes("§aReady §lNow");
    const visibleParts = formatted.extra.filter((part) => typeof part !== "string" && part.text);

    assert.equal(translateTextColor("green"), "#55ff55");
    assert.equal(translateTextColor("#123456"), "#123456");
    assert.deepEqual(visibleParts, [
        {
            color: "#55ff55",
            bold: false,
            italic: false,
            underlined: false,
            obfuscated: false,
            strikethrough: false,
            text: "Ready ",
        },
        {
            color: "#55ff55",
            bold: true,
            italic: false,
            underlined: false,
            obfuscated: false,
            strikethrough: false,
            text: "Now",
        },
    ]);
});

test("shared UI contracts live outside route internals", () => {
    const neutralSources = [
        "integration/input/cefTextInput.ts",
        "integration/input/cefTextInputKeyboard.ts",
        "integration/input/cefTextInputSupport.ts",
        "integration/input/textEditing.ts",
        "integration/accountLoginState.ts",
        "integration/clientEnvironment.ts",
        "components/text/TextComponent.svelte",
        "components/text/legacyTextFormatting.ts",
        "components/bind/BindDisplay.svelte",
        "shared/hud-editor/HudComponentSettings.svelte",
        "shared/hud-editor/HudEditor.svelte",
        "shared/hud-editor/HudEditorContracts.ts",
        "shared/hud-theme/HudThemeSelector.svelte",
        "shared/settings/GenericSetting.svelte",
    ];
    for (const path of neutralSources) {
        assert.equal(existsSync(sourcePath(path)), true, `${path} must be route-neutral`);
    }

    const forbiddenRouteInternals = [
        /clickgui\/setting\/common\/cefTextInput/,
        /clickgui\/setting\/common\/GenericSetting\.svelte/,
        /clickgui\/setting\/bind\/BindDisplay\.svelte/,
        /clickgui\/tabs\/hud_editor\/(?:ComponentSettings\.svelte|constants)/,
        /hud\/theme\/HudThemeSelector\.svelte/,
        /menu\/common\/TextComponent\.svelte/,
    ];
    for (const path of routeSources()) {
        const source = readFileSync(path, "utf8");
        for (const forbidden of forbiddenRouteInternals) {
            assert.doesNotMatch(
                source,
                forbidden,
                `${relative(new URL(".", themeRoot).pathname, path.pathname)} imports another route's internal UI`,
            );
        }
    }
});

test("integration never reaches into route-owned state", () => {
    for (const path of routeSources(integrationRoot)) {
        const source = readFileSync(path, "utf8");
        assert.doesNotMatch(source, /(?:\.\.\/)+routes\//, `${path.pathname} imports route-owned state`);
    }
});

test("route features compose through shared modules instead of other routes", () => {
    for (const path of routeSources()) {
        const sourceOwner = routeOwner(path);
        const imports = [...readFileSync(path, "utf8").matchAll(/from\s+["']([^"']+)["']/g)];
        for (const [, specifier] of imports) {
            if (!specifier.startsWith(".")) continue;
            const targetOwner = routeOwner(new URL(specifier, path));
            assert.ok(
                targetOwner === undefined || targetOwner === sourceOwner,
                `${path.pathname} reaches from ${sourceOwner} into ${targetOwner}`,
            );
        }
    }
});

function routeOwner(path) {
    return path.pathname.match(/\/routes\/([^/]+)\//)?.[1];
}
