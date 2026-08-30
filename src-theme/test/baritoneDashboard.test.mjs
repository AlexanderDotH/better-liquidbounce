import assert from "node:assert/strict";
import test from "node:test";
import {readComponentSourceWithStyles} from "./themeSource.mjs";

const themeRoot = new URL("../", import.meta.url);

test("the dashboard is accessible, responsive, and routes every editable field through CEF", () => {
    const dashboard = componentSource("src/routes/baritone/BaritoneDashboard.svelte");

    assert.match(dashboard, /role="tablist"/);
    assert.match(dashboard, /role="tabpanel"/);
    assert.match(dashboard, /aria-live="polite"/);
    assert.match(dashboard, /Pause/);
    assert.match(dashboard, /Resume/);
    assert.match(dashboard, /Cancel/);
    assert.match(dashboard, /Navigation mode/);
    assert.match(dashboard, /Navigation state/);
    assert.match(dashboard, /Retries remaining/);
    assert.match(dashboard, /Active flight route/);
    assert.match(dashboard, /Active walking route/);
    assert.match(dashboard, /@media\s*\(max-width:/);
    assert.match(dashboard, /@media\s*\(prefers-reduced-motion:\s*reduce\)/);
    assert.match(dashboard, /use:cefTextInput=/);
    assert.match(dashboard, /screenNames:\s*\["baritone"\]/);
    assert.match(dashboard, /<svg/);
    assert.match(dashboard, /role="img"/);
});

function componentSource(relativePath) {
    return readComponentSourceWithStyles(new URL(relativePath, themeRoot));
}
