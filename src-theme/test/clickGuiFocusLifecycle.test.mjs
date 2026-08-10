import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import ts from "typescript";

const themeRoot = new URL("../", import.meta.url);

async function importTypeScript(relativePath) {
    const source = readFileSync(new URL(relativePath, themeRoot), "utf8");
    const {outputText} = ts.transpileModule(source, {
        compilerOptions: {
            module: ts.ModuleKind.ESNext,
            target: ts.ScriptTarget.ES2022,
        },
    });

    return import(`data:text/javascript;base64,${Buffer.from(outputText).toString("base64")}`);
}

const {releaseDocumentFocus} = await importTypeScript("src/util/documentFocus.ts");

test("closing a virtual screen blurs the active HTML control", () => {
    const originalHTMLElement = Object.getOwnPropertyDescriptor(globalThis, "HTMLElement");

    class MockHTMLElement {
        blurCount = 0;

        blur() {
            this.blurCount += 1;
        }
    }

    Object.defineProperty(globalThis, "HTMLElement", {
        configurable: true,
        value: MockHTMLElement,
    });

    try {
        const activeElement = new MockHTMLElement();

        releaseDocumentFocus({activeElement});

        assert.equal(activeElement.blurCount, 1);
    } finally {
        if (originalHTMLElement) {
            Object.defineProperty(globalThis, "HTMLElement", originalHTMLElement);
        } else {
            delete globalThis.HTMLElement;
        }
    }
});

test("closing a virtual screen tolerates a document without a focused HTML control", () => {
    assert.doesNotThrow(() => releaseDocumentFocus({activeElement: null}));
});

test("virtual screen close releases focus before route listener cleanup", () => {
    const app = readFileSync(new URL("src/App.svelte", themeRoot), "utf8");
    const closeCase = app.match(/case "close":(?<body>[\s\S]*?)break;/)?.groups?.body;

    assert.ok(closeCase, "App.svelte must handle virtual-screen close events");

    const releaseFocusIndex = closeCase.indexOf("releaseDocumentFocus()");
    const routeChangeIndex = closeCase.indexOf('await changeRoute("none")');

    assert.notEqual(releaseFocusIndex, -1, "the close path must release DOM focus");
    assert.notEqual(routeChangeIndex, -1, "the close path must navigate away");
    assert.ok(
        releaseFocusIndex < routeChangeIndex,
        "DOM focus must be released before changeRoute cleans up component listeners",
    );
});
