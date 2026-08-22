import assert from "node:assert/strict";
import test from "node:test";

import {
    LatestArrayListModuleLoader,
    areArrayListEntriesRenderEquivalent,
    buildArrayListEntries,
    getArrayListMotionOffset,
} from "../src/routes/hud/elements/arrayListModel.ts";

const CLASSIC_FONT = "500 14px Inter";
const MODERN_NAME_FONT = "550 12px Inter";
const MODERN_TAG_FONT = "600 10px Inter";

function module(name, overrides = {}) {
    return {
        name,
        category: "Combat",
        keyBind: {
            boundKey: "UNKNOWN",
            action: "Toggle",
            modifiers: [],
        },
        enabled: true,
        description: `${name} description`,
        hidden: false,
        aliases: [],
        tag: null,
        ...overrides,
    };
}

function mappedMeasurer(expectedWidths, calls = []) {
    return (text, font) => {
        calls.push({text, font});
        const key = `${font}|${text}`;
        assert.ok(expectedWidths.has(key), `Unexpected measurement: ${key}`);
        return expectedWidths.get(key);
    };
}

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });

    return {promise, resolve, reject};
}

test("Modern entries use the rendered name and concentric tagged/untagged chrome", () => {
    const modules = [
        module("AlphaTool", {tag: "Mode"}),
        module("Beta", {tag: ""}),
    ];
    const formatCalls = [];
    const measureCalls = [];
    const widths = new Map([
        [`${MODERN_NAME_FONT}|Alpha Tool`, 41],
        [`${MODERN_TAG_FONT}|Mode`, 22],
        [`${MODERN_NAME_FONT}|Beta`, 30],
    ]);

    const entries = buildArrayListEntries(
        modules,
        {showTags: true, order: "Ascending"},
        "modern",
        (name) => {
            formatCalls.push(name);
            return name === "AlphaTool" ? "Alpha Tool" : name;
        },
        mappedMeasurer(widths, measureCalls),
    );

    assert.deepEqual(
        entries.map(({name, displayName, visibleTag, measuredWidth}) => ({
            name,
            displayName,
            visibleTag,
            measuredWidth,
        })),
        [
            {name: "Beta", displayName: "Beta", visibleTag: null, measuredWidth: 48},
            {
                name: "AlphaTool",
                displayName: "Alpha Tool",
                visibleTag: "Mode",
                measuredWidth: 95,
            },
        ],
    );
    assert.deepEqual(formatCalls, ["AlphaTool", "Beta"]);
    assert.deepEqual(measureCalls, [
        {text: "Alpha Tool", font: MODERN_NAME_FONT},
        {text: "Mode", font: MODERN_TAG_FONT},
        {text: "Beta", font: MODERN_NAME_FONT},
    ]);
});

test("Modern entries omit tag measurement and use 18px chrome when tags are off", () => {
    const measureCalls = [];
    const widths = new Map([[`${MODERN_NAME_FONT}|Alpha Tool`, 41]]);

    const [entry] = buildArrayListEntries(
        [module("AlphaTool", {tag: "Mode"})],
        {showTags: false, order: "Descending"},
        "modern",
        () => "Alpha Tool",
        mappedMeasurer(widths, measureCalls),
    );

    assert.equal(entry.visibleTag, null);
    assert.equal(entry.measuredWidth, 59);
    assert.deepEqual(measureCalls, [
        {text: "Alpha Tool", font: MODERN_NAME_FONT},
    ]);
});

test("Classic entries measure the complete rendered label and add 20px chrome", () => {
    const measureCalls = [];
    const widths = new Map([
        [`${CLASSIC_FONT}|Alpha Tool Mode`, 72],
        [`${CLASSIC_FONT}|Beta`, 30],
    ]);

    const entries = buildArrayListEntries(
        [module("AlphaTool", {tag: "Mode"}), module("Beta")],
        {showTags: true, order: "Descending"},
        "classic",
        (name) => name === "AlphaTool" ? "Alpha Tool" : name,
        mappedMeasurer(widths, measureCalls),
    );

    assert.deepEqual(
        entries.map(({name, visibleTag, measuredWidth}) => ({name, visibleTag, measuredWidth})),
        [
            {name: "AlphaTool", visibleTag: "Mode", measuredWidth: 92},
            {name: "Beta", visibleTag: null, measuredWidth: 50},
        ],
    );
    assert.deepEqual(measureCalls, [
        {text: "Alpha Tool Mode", font: CLASSIC_FONT},
        {text: "Beta", font: CLASSIC_FONT},
    ]);
});

test("Sorting uses complete rendered width in both directions", () => {
    const modules = [
        module("LongName"),
        module("A", {tag: "VeryWideTag"}),
    ];
    const widths = new Map([
        [`${MODERN_NAME_FONT}|LongName`, 70],
        [`${MODERN_NAME_FONT}|A`, 8],
        [`${MODERN_TAG_FONT}|VeryWideTag`, 80],
    ]);
    const build = (order) => buildArrayListEntries(
        modules,
        {showTags: true, order},
        "modern",
        (name) => name,
        mappedMeasurer(widths),
    );

    assert.deepEqual(build("Ascending").map(({name}) => name), ["LongName", "A"]);
    assert.deepEqual(build("Descending").map(({name}) => name), ["A", "LongName"]);
});

test("Disabled and hidden modules are excluded before formatting", () => {
    const formatCalls = [];
    const widths = new Map([[`${MODERN_NAME_FONT}|Visible`, 30]]);

    const entries = buildArrayListEntries(
        [
            module("Disabled", {enabled: false}),
            module("Hidden", {hidden: true}),
            module("Visible"),
        ],
        {showTags: true, order: "Ascending"},
        "modern",
        (name) => {
            formatCalls.push(name);
            return name;
        },
        mappedMeasurer(widths),
    );

    assert.deepEqual(entries.map(({name}) => name), ["Visible"]);
    assert.deepEqual(formatCalls, ["Visible"]);
});

test("Building entries does not mutate the input array or modules", () => {
    const first = Object.freeze(module("First"));
    const second = Object.freeze(module("Second"));
    const modules = Object.freeze([first, second]);
    const snapshot = structuredClone(modules);

    buildArrayListEntries(
        modules,
        {showTags: true, order: "Descending"},
        "modern",
        (name) => name,
        (text) => text.length,
    );

    assert.deepEqual(modules, snapshot);
    assert.equal(modules[0], first);
    assert.equal(modules[1], second);
});

test("Equivalent rerenders do not replace rows while visual changes still do", () => {
    const current = [
        {...module("Alpha"), displayName: "Alpha", visibleTag: null, measuredWidth: 40},
        {...module("Beta", {tag: "Mode"}), displayName: "Beta", visibleTag: "Mode", measuredWidth: 70},
    ];
    const remeasured = current.map(entry => ({
        ...entry,
        measuredWidth: entry.measuredWidth + 5,
    }));

    assert.equal(areArrayListEntriesRenderEquivalent(current, remeasured), true);
    assert.equal(areArrayListEntriesRenderEquivalent(current, [...remeasured].reverse()), false);
    assert.equal(
        areArrayListEntriesRenderEquivalent(current, [
            remeasured[0],
            {...remeasured[1], visibleTag: "Other"},
        ]),
        false,
    );
    assert.equal(
        areArrayListEntriesRenderEquivalent(current, [
            {...remeasured[0], displayName: "A l p h a"},
            remeasured[1],
        ]),
        false,
    );
});

test("ArrayList motion is mirrored for both presentations", () => {
    assert.equal(getArrayListMotionOffset("modern", "Left"), -18);
    assert.equal(getArrayListMotionOffset("modern", "Right"), 18);
    assert.equal(getArrayListMotionOffset("classic", "Left"), -50);
    assert.equal(getArrayListMotionOffset("classic", "Right"), 50);
});

test("Only the newest overlapping module load is accepted", async () => {
    const firstLoad = deferred();
    const secondLoad = deferred();
    const pendingLoads = [firstLoad, secondLoad];
    const loader = new LatestArrayListModuleLoader(
        () => pendingLoads.shift().promise,
    );

    const firstResult = loader.loadLatest();
    const secondResult = loader.loadLatest();
    const newestModules = [module("Newest")];
    secondLoad.resolve(newestModules);

    assert.equal(await secondResult, newestModules);

    firstLoad.resolve([module("Stale")]);
    assert.equal(await firstResult, null);
});

test("Invalidation prevents an in-flight module load from being accepted", async () => {
    const pendingLoad = deferred();
    const loader = new LatestArrayListModuleLoader(() => pendingLoad.promise);
    const result = loader.loadLatest();

    loader.invalidate();
    pendingLoad.resolve([module("AfterUnmount")]);

    assert.equal(await result, null);
});

test("Module loader errors propagate to the caller", async () => {
    const expectedError = new Error("load failed");
    const loader = new LatestArrayListModuleLoader(async () => {
        throw expectedError;
    });

    await assert.rejects(loader.loadLatest(), expectedError);
});
