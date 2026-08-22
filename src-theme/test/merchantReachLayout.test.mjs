import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";
import test from "node:test";

const componentUrl = new URL(
    "../src/routes/clickgui/setting/merchant/MerchantReachSetting.svelte",
    import.meta.url,
);

test("Range and Wall Range sliders use separate full-width rows", async () => {
    const source = await readFile(componentUrl, "utf8");
    const reachControls = source.match(/\.reach-controls\s*\{(?<rules>[^}]*)}/s)?.groups?.rules ?? "";

    assert.match(reachControls, /grid-template-columns:\s*minmax\(0,\s*1fr\)/);
    assert.doesNotMatch(reachControls, /repeat\(2,/);
});
