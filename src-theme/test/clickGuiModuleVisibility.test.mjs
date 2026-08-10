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
