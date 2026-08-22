import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const arrayList = readFileSync(
    new URL("../src/routes/hud/elements/ArrayList.svelte", import.meta.url),
    "utf8",
);

test("ArrayList renders the model's precomputed names, tags, and motion", () => {
    assert.match(
        arrayList,
        /import\s*\{[\s\S]*buildArrayListEntries[\s\S]*getArrayListMotionOffset[\s\S]*LatestArrayListModuleLoader[\s\S]*\}\s*from\s*"\.\/arrayListModel"/,
    );
    assert.match(arrayList, /const nextEntries\s*=\s*buildArrayListEntries\(\s*moduleSnapshot,/);
    assert.match(
        arrayList,
        /if \(areArrayListEntriesRenderEquivalent\(enabledModules,\s*nextEntries\)\)\s*\{\s*return;\s*\}/,
    );
    assert.match(arrayList, /enabledModules\s*=\s*nextEntries/);
    assert.match(
        arrayList,
        /motionOffset\s*=\s*getArrayListMotionOffset\(variant,\s*cSettings\.itemAlignment\)/,
    );
    assert.match(
        arrayList,
        /#each enabledModules as \{name,\s*displayName,\s*visibleTag\} \(name\)/,
    );
    assert.match(arrayList, /class:has-visible-tag=\{visibleTag !== null\}/);
    assert.match(arrayList, /<span class="module-name">\s*\{displayName\}\s*<\/span>/);
    assert.match(
        arrayList,
        /\{#if visibleTag !== null\}\s*<span class="tag">\{visibleTag\}<\/span>\s*\{\/if\}/,
    );
});

test("full refreshes use the latest loader for mount, explicit refresh, and reconnect", () => {
    assert.match(
        arrayList,
        /const moduleLoader\s*=\s*new LatestArrayListModuleLoader\(getModules\)/,
    );
    assert.match(arrayList, /let moduleSnapshot:\s*Module\[\]\s*=\s*\[\]/);
    assert.match(
        arrayList,
        /const modules\s*=\s*await moduleLoader\.loadLatest\(\)/,
    );
    assert.match(arrayList, /if \(modules === null\)\s*\{\s*return;\s*\}/);
    assert.match(arrayList, /moduleSnapshot\s*=\s*modules/);
    assert.match(arrayList, /console\.error\("\[ArrayList\] Failed to refresh modules",\s*error\)/);
    assert.match(arrayList, /onMount\(\(\)\s*=>\s*\{[\s\S]*void refreshModuleSnapshot\(\)/);
    assert.match(
        arrayList,
        /listen\("refreshArrayList",\s*\(\)\s*=>\s*\{\s*void refreshModuleSnapshot\(\);\s*\}\)/,
    );
    assert.match(
        arrayList,
        /listen\("socketReady",\s*\(\)\s*=>\s*\{\s*void refreshModuleSnapshot\(\);\s*\}\)/,
    );
});

test("module toggles update the cached snapshot immediately and only refetch unknown modules", () => {
    assert.match(arrayList, /function handleModuleToggle\(event:\s*ModuleToggleEvent\)/);
    assert.match(arrayList, /module\.name\s*(?:===|!==)\s*event\.moduleName/);
    assert.match(arrayList, /enabled:\s*event\.enabled/);
    assert.match(arrayList, /hidden:\s*event\.hidden/);
    assert.match(arrayList, /moduleSnapshot\s*=\s*updatedModules/);
    assert.match(
        arrayList,
        /if \(!moduleFound\)\s*\{\s*void refreshModuleSnapshot\(\);\s*return;\s*\}/,
    );
    assert.match(
        arrayList,
        /if \(!moduleFound\)[\s\S]*?return;\s*\}[\s\S]*?moduleLoader\.invalidate\(\);\s*moduleSnapshot\s*=\s*updatedModules/,
    );
    assert.match(arrayList, /listen\("moduleToggle",\s*handleModuleToggle\)/);
});

test("toggled modules enter at their sorted model position without vertical FLIP", () => {
    assert.match(
        arrayList,
        /moduleSnapshot\s*=\s*updatedModules;\s*renderModuleSnapshot\(\);/,
    );
    assert.match(
        arrayList,
        /#each enabledModules as \{name,\s*displayName,\s*visibleTag\} \(name\)/,
    );
    assert.doesNotMatch(arrayList, /animate:flip/);
    assert.doesNotMatch(arrayList, /from "svelte\/animate"/);
    assert.match(
        arrayList,
        /transition:fly=\{\{ x: motionOffset, duration: motionDuration \}\}/,
    );
});

test("layout inputs and font readiness rerender the cached snapshot without REST", () => {
    assert.match(arrayList, /function renderModuleSnapshot\(\)/);
    assert.match(
        arrayList,
        /spaceSeperatedNames\.subscribe\(\(enabled\)\s*=>\s*\{\s*useSpacedNames\s*=\s*enabled;\s*renderModuleSnapshot\(\);\s*\}\)/,
    );
    assert.match(
        arrayList,
        /document\.fonts\.ready\.then\(\(\)\s*=>\s*\{\s*if \(!fontCallbackActive\)\s*\{\s*return;\s*\}\s*renderModuleSnapshot\(\);/,
    );
    assert.match(
        arrayList,
        /if \(cSettings !== settings\)[\s\S]*cSettings\s*=\s*settings as HudArrayListSettings;[\s\S]*renderModuleSnapshot\(\)/,
    );
    assert.match(
        arrayList,
        /if \(variant !== previousVariant\)[\s\S]*previousVariant\s*=\s*variant;[\s\S]*renderModuleSnapshot\(\)/,
    );
});

test("unmount prevents fonts and pending module requests from applying", () => {
    assert.match(
        arrayList,
        /return \(\)\s*=>\s*\{\s*unsubscribe\(\);\s*fontCallbackActive\s*=\s*false;\s*moduleLoader\.invalidate\(\);\s*\}/,
    );
});

test("obsolete inline measuring and tick-based refresh code is removed", () => {
    assert.doesNotMatch(arrayList, /\btick\b/);
    assert.doesNotMatch(arrayList, /function measureModuleWidth/);
    assert.doesNotMatch(arrayList, /MODERN_TAG_(?:GAP|HORIZONTAL_PADDING)_PX/);
});
