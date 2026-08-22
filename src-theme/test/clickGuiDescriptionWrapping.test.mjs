import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const descriptionPath = new URL(
    "../src/routes/clickgui/Description.svelte",
    import.meta.url,
);

test("module hover descriptions wrap within the shared tooltip", () => {
    const source = readFileSync(descriptionPath, "utf8");
    const textStyleStart = source.indexOf("  .text {");
    const extendedStyleStart = source.indexOf("    &.extended", textStyleStart);
    const textStyle = source.slice(textStyleStart, extendedStyleStart);

    assert.notEqual(textStyleStart, -1, "Description.svelte should define the tooltip text style");
    assert.notEqual(extendedStyleStart, -1, "Description.svelte should keep extended text overrides separate");
    assert.match(textStyle, /max-width:\s*300px;/);
    assert.match(textStyle, /white-space:\s*normal;/);
    assert.match(textStyle, /overflow-wrap:\s*anywhere;/);
});
