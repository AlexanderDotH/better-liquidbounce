import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const clickGuiRoot = new URL("../src/routes/clickgui/", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, clickGuiRoot), "utf8");
}

test("modules with only the Hidden setting can open in both ClickGUI themes", () => {
    for (const relativePath of [
        "Module.svelte",
        "themes/modern/ModernModule.svelte",
    ]) {
        const module = read(relativePath);

        assert.match(module, /setting\.name !== "Bind"|v\.name !== "Bind"/);
        assert.doesNotMatch(module, /setting\.name !== "Hidden"|v\.name !== "Hidden"/);
    }
});

test("module summaries count Hidden as an interop-visible setting", () => {
    const moduleFunctions = readFileSync(
        new URL(
            "../../src/main/kotlin/net/ccbluex/liquidbounce/integration/interop/protocol/rest/v1/client/ModuleFunctions.kt",
            import.meta.url,
        ),
        "utf8",
    );
    const hasSettings = moduleFunctions.match(
        /val hasSettings = inner\.any \{ value ->[\s\S]*?\n    \}/,
    )?.[0];

    assert.ok(hasSettings);
    assert.match(hasSettings, /value\.name != "Bind"/);
    assert.match(hasSettings, /!value\.notAnOption/);
    assert.doesNotMatch(hasSettings, /checkIfInclude\(\)/);
});
