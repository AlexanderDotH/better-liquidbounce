import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const hudRoot = new URL("../src/routes/hud/", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, hudRoot), "utf8");
}

test("HUD motion preference is shared and releases its media-query listener", () => {
    const motion = read("motion/hudMotion.ts");

    assert.match(motion, /readable\(false/);
    assert.match(motion, /REDUCED_MOTION_QUERY = "\(prefers-reduced-motion: reduce\)"/);
    assert.match(motion, /matchMedia\(REDUCED_MOTION_QUERY\)/);
    assert.match(motion, /mediaQuery\.addEventListener\("change", synchronize\)/);
    assert.match(motion, /mediaQuery\.removeEventListener\("change", synchronize\)/);
    assert.match(motion, /CLASSIC_MOTION_DURATION_MS = 200/);
    assert.match(motion, /MODERN_MOTION_DURATION_MS = 160/);
    assert.match(
        motion,
        /presentation === "modern" \? modernDuration : CLASSIC_MOTION_DURATION_MS/,
    );
    assert.match(motion, /reducedMotion \? 0/);
});

test("ArrayList has stable sorted insertion and finite horizontal motion", () => {
    const arrayList = read("elements/ArrayList.svelte");
    const arrayListModel = read("elements/arrayListModel.ts");

    assert.match(arrayList, /export let variant:\s*ArrayListVariant = "classic"/);
    assert.doesNotMatch(arrayList, /hudThemeSession/);
    assert.match(arrayList, /hudMotionDuration\(variant,\s*\$prefersReducedMotion\)/);
    assert.doesNotMatch(arrayList, /animate:flip/);
    assert.doesNotMatch(arrayList, /from "svelte\/animate"/);
    assert.match(arrayList, /transition:fly=\{\{ x: motionOffset, duration: motionDuration \}\}/);
    assert.match(arrayList, /motionOffset = getArrayListMotionOffset\(variant,\s*cSettings\.itemAlignment\)/);
    assert.match(arrayListModel, /const magnitude = variant === "modern" \? 18 : 50/);
    assert.match(arrayListModel, /itemAlignment === "Left" \? -magnitude : magnitude/);
    assert.match(arrayList, /variant !== previousVariant/);
});

test("Target HUD keeps one transition root across live presentation changes", () => {
    const targetHud = read("elements/targethud/TargetHud.svelte");
    const transitions = targetHud.match(/transition:fly=/g) ?? [];

    assert.equal(transitions.length, 1);
    assert.match(
        targetHud,
        /hudMotionDuration\(presentation,\s*\$prefersReducedMotion,\s*180\)/,
    );
    assert.match(targetHud, /motionOffset = presentation === "modern" \? -6 : -10/);
    assert.match(
        targetHud,
        /class:targethud--modern=\{presentation === "modern"\}[\s\S]*transition:fly=[\s\S]*\{#if presentation === "modern"\}/,
    );
});

test("Target health percentage stays finite and within the visible track", () => {
    const healthProgress = read("elements/targethud/HealthProgress.svelte");

    assert.match(healthProgress, /function healthPercentage/);
    assert.match(healthProgress, /!Number\.isFinite\(maxHealth\)/);
    assert.match(healthProgress, /maxHealth <= 0/);
    assert.match(healthProgress, /!Number\.isFinite\(health\)/);
    assert.match(healthProgress, /Math\.min\(100,\s*Math\.max\(0,/);
    assert.match(healthProgress, /\$:\s*width = healthPercentage\(health,\s*maxHealth\)/);
});

test("TabGUI ignores navigation until choices exist and bounds both selections", () => {
    const tabGui = read("elements/tabgui/TabGui.svelte");

    assert.match(tabGui, /function moveSelection\(direction:\s*-1 \| 1\)/);
    assert.match(tabGui, /if \(itemCount === 0\)/);
    assert.match(tabGui, /selectedCategoryIndex = wrapIndex/);
    assert.match(tabGui, /selectedModuleIndex = wrapIndex/);
    assert.match(tabGui, /const selectedCategory = categories\[selectedCategoryIndex\]/);
    assert.match(tabGui, /if \(!selectedCategory\)/);
    assert.match(tabGui, /selectedModuleIndex = Math\.min/);
});

test("TabGUI and notifications use shared reduced-motion durations", () => {
    const tabGui = read("elements/tabgui/TabGui.svelte");
    const category = read("elements/tabgui/Category.svelte");
    const notifications = read("elements/notifications/Notifications.svelte");

    for (const source of [tabGui, category, notifications]) {
        assert.match(source, /prefersReducedMotion/);
        assert.match(source, /hudMotionDuration/);
        assert.doesNotMatch(source, /matchMedia\(/);
    }

    assert.match(tabGui, /motionOffset = variant === "modern" \? -8 : -10/);
    assert.match(tabGui, /transition:fly=\{\{ x: motionOffset, duration: motionDuration \}\}/);
    assert.match(category, /transition:fade=\{\{ duration: motionDuration \}\}/);
    assert.match(notifications, /animate:flip=\{\{ duration: motionDuration \}\}/);
    assert.match(notifications, /in:fly=\{\{ x: motionOffset, duration: motionDuration \}\}/);
    assert.match(notifications, /out:fly=\{\{ x: motionOffset, duration: motionDuration \}\}/);
});
