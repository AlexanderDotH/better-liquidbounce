import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const clickGuiRoot = new URL("../src/routes/clickgui/", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, clickGuiRoot), "utf8");
}

test("layout reset updates mounted panels instead of remounting transition descendants", () => {
    const clickGui = read("themes/modern/ModernClickGui.svelte");
    const panel = read("themes/modern/ModernPanel.svelte");

    assert.doesNotMatch(clickGui, /#key\s+layoutGeneration/);
    assert.match(clickGui, /resetVersion=/);
    assert.match(panel, /resetVersion/);
});

test("setting transitions stay local when an ancestor theme or panel is removed", () => {
    for (const relativePath of [
        "setting/common/GenericSetting.svelte",
        "setting/MultiChooseSetting.svelte",
        "setting/list/GenericListSetting.svelte",
        "setting/list/RegistryMutableListSetting.svelte",
        "Description.svelte",
        "theme/ClickGuiThemeHost.svelte",
    ]) {
        assert.doesNotMatch(
            read(relativePath),
            /(?:in:|out:|transition:)[a-zA-Z]+\|global/,
            `${relativePath} must not retain a removed ancestor through a global transition`,
        );
    }
});

test("the production build keeps the dev fixture out of its entry graph", () => {
    const app = read("../../App.svelte");
    const viteConfig = readFileSync(
        new URL("../vite.config.ts", import.meta.url),
        "utf8",
    );

    assert.doesNotMatch(app, /modern-clickgui-preview/);
    assert.doesNotMatch(viteConfig, /modern-clickgui-preview/);
});

test("CEF search uses only the websocket keyboard bridge while the dev preview opts into native input", () => {
    const search = read("themes/modern/ModernSearch.svelte");
    const tabbed = read("themes/modern/ModernTabbedClickGui.svelte");
    const previewMain = readFileSync(
        new URL("../src/dev/modern-clickgui-preview/main.ts", import.meta.url),
        "utf8",
    );

    assert.match(search, /readonly=\{!allowNativeInput\}/);
    assert.match(search, /if \(!allowNativeInput\)/);
    assert.match(tabbed, /allowNativeInput=\{nativeTextInput\}/);
    assert.match(previewMain, /nativeTextInput:\s*true/);
});
