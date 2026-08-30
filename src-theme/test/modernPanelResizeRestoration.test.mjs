import assert from "node:assert/strict";
import test from "node:test";
import {readComponentSourceWithStyles} from "./themeSource.mjs";

const panelSource = readComponentSourceWithStyles(
    new URL(
        "../src/routes/clickgui/themes/modern/ModernPanel.svelte",
        import.meta.url,
    ),
    "utf8",
);

function functionBody(name) {
    const declarationIndex = panelSource.indexOf(`function ${name}(`);
    assert.notEqual(declarationIndex, -1, `${name} must exist`);

    const openingBraceIndex = panelSource.indexOf("{", declarationIndex);
    assert.notEqual(openingBraceIndex, -1, `${name} must open a block`);

    let depth = 0;
    for (let index = openingBraceIndex; index < panelSource.length; index += 1) {
        if (panelSource[index] === "{") {
            depth += 1;
        } else if (panelSource[index] === "}") {
            depth -= 1;
        }

        if (depth === 0) {
            return panelSource.slice(openingBraceIndex + 1, index);
        }
    }

    assert.fail(`${name} must close its block`);
}

test("temporary viewport clamping keeps the persisted panel arrangement restorable", () => {
    const resizeBody = functionBody("handleWindowResize");
    const syncBody = functionBody("syncVisiblePanelPosition");
    const moveBody = functionBody("setPanelPosition");
    const saveBody = functionBody("savePanelState");

    assert.match(
        panelSource,
        /let visiblePanelPosition = \$state<ModernPanelPosition>/,
    );
    assert.match(panelSource, /visiblePosition=\{visiblePanelPosition\}/);
    assert.match(panelSource, /style:left="\{visiblePosition\.left\}px"/);
    assert.match(panelSource, /style:top="\{visiblePosition\.top\}px"/);

    assert.match(resizeBody, /syncVisiblePanelPosition\(\)/);
    assert.doesNotMatch(resizeBody, /savePanelState\(\)/);
    assert.match(
        syncBody,
        /clampModernPanelPosition\(\s*panelState,\s*viewport,?\s*\)/,
    );

    assert.match(moveBody, /panelState\.left\s*=\s*position\.left/);
    assert.match(moveBody, /panelState\.top\s*=\s*position\.top/);
    assert.match(moveBody, /visiblePanelPosition\.left\s*=\s*position\.left/);
    assert.match(moveBody, /visiblePanelPosition\.top\s*=\s*position\.top/);
    assert.match(saveBody, /\$state\.snapshot\(panelState\)/);
});
