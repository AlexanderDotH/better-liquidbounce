import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import {readModuleGraph} from "./themeSource.mjs";

const events = readModuleGraph(
    new URL("../src/integration/events.ts", import.meta.url),
);

test("frontend event map exposes live HUD settings updates", () => {
    assert.match(events, /hudValueChange:\s*HudValueChangeEvent/);
    assert.match(
        events,
        /export interface HudValueChangeEvent\s*\{\s*configurable:\s*ConfigurableSetting;\s*\}/,
    );
});
