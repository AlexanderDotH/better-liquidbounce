import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const source = readFileSync(
    new URL("../src/routes/clickgui/setting/ChoiceSetting.svelte", import.meta.url),
    "utf8",
);

test("nested choice rows remount per active mode without losing fork category descriptions", () => {
    assert.match(source, /\{#each nestedSettings as setting \(`\$\{cSetting\.active}\.\$\{setting\.name}`\)}/);
    assert.match(source, /const categories: Record<string, string\[]> = cSetting\.categories \?\? \{};/);
    assert.match(source, /const extendedDescriptions = Object\.fromEntries/);
    assert.match(source, /\{extendedDescriptions}/);
});
