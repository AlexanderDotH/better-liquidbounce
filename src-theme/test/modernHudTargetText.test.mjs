import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const hudRoot = new URL("../src/routes/hud/", import.meta.url);
const componentRoot = new URL("../public/components/", import.meta.url);
const previewRoot = new URL("../src/dev/modern-hud-preview/", import.meta.url);

function readHud(relativePath) {
    return readFileSync(new URL(relativePath, hudRoot), "utf8");
}

test("Target HUD selects a compact Modern presentation without changing the Classic default", () => {
    const hud = readHud("Hud.svelte");
    const targetHud = readHud("elements/targethud/TargetHud.svelte");

    assert.match(
        hud,
        /let presentation:\s*"classic" \| "modern" = "classic"/,
    );
    assert.match(hud, /<TargetHud \{presentation\}\s*\/>/);
    assert.match(
        targetHud,
        /export let presentation:\s*"classic" \| "modern" = "classic"/,
    );
    assert.match(targetHud, /#if presentation === "modern"/);

    const modernMarkup = targetHud.match(
        /\{#if presentation === "modern"\}([\s\S]*?)\{:else\}/,
    )?.[1] ?? "";
    assert.match(
        targetHud,
        /class="targethud"[\s\S]*class:targethud--modern=\{presentation === "modern"\}/,
    );
    assert.match(modernMarkup, /class="avatar avatar--modern"/);
    assert.match(modernMarkup, /class="health-value"/);
    assert.match(modernMarkup, /compact=\{true\}/);
    assert.match(modernMarkup, /target\.absorption > 0/);
    assert.match(modernMarkup, /target\.armor > 0/);
    assert.doesNotMatch(modernMarkup, /<ArmorStatus/);

    assert.match(
        targetHud,
        /\{:else\}[\s\S]*class="main-wrapper"[\s\S]*<ArmorStatus[\s\S]*<HealthProgress maxHealth=\{target\.maxHealth \+ target\.absorption\} health=\{target\.actualHealth \+ target\.absorption\}/,
    );
});

test("compact Target HUD uses a 34px avatar, a 3px health bar, and reduced-motion-safe transitions", () => {
    const targetHud = readHud("elements/targethud/TargetHud.svelte");
    const healthProgress = readHud("elements/targethud/HealthProgress.svelte");

    assert.match(
        targetHud,
        /\.targethud\.targethud--modern[\s\S]*?background:\s*rgba\(15,\s*18,\s*23,\s*0\.78\)/,
    );
    assert.match(
        targetHud,
        /\.targethud\.targethud--modern[\s\S]*?border:\s*0/,
    );
    assert.match(
        targetHud,
        /\.avatar\.avatar--modern[\s\S]*?width:\s*34px[\s\S]*?height:\s*34px/,
    );
    assert.match(healthProgress, /export let compact = false/);
    assert.match(
        healthProgress,
        /\.health-progress\.health-progress--compact[\s\S]*?\.thumb[\s\S]*?height:\s*3px/,
    );
    assert.match(healthProgress, /@media\s*\(prefers-reduced-motion:\s*reduce\)/);
});

test("Text exposes an opt-in Plain or Pill container with a legacy-safe Plain fallback", () => {
    const definition = JSON.parse(
        readFileSync(new URL("text.json", componentRoot), "utf8"),
    );
    const text = readHud("elements/Text.svelte");
    const types = readHud("components.d.ts");

    const container = definition.values.find(value => value.name === "Container");
    assert.deepEqual(container, {
        type: "CHOOSE",
        name: "Container",
        value: "Plain",
        choices: ["Plain", "Pill"],
    });

    assert.match(types, /container\?:\s*"Plain" \| "Pill"/);
    assert.match(text, /\$:\s*container = cSettings\.container \?\? "Plain"/);
    assert.match(text, /\$:\s*processedText = processText\(cSettings\.text,\s*playerData\)/);
    assert.match(text, /class:text--pill=\{container === "Pill"\}/);
    assert.match(
        text,
        /\.text\.text--pill[\s\S]*?background:\s*var\(--modern-hud-surface-soft,\s*rgba\(15,\s*18,\s*23,\s*0\.84\)\)/,
    );
});

test("Modern HUD preview opts its coordinate Text into the Pill container", () => {
    const fixture = readFileSync(
        new URL("previewFixture.ts", previewRoot),
        "utf8",
    );

    assert.match(
        fixture,
        /Text:\s*\{[\s\S]*?text:\s*"XYZ \{blockPosition\.x\} \/ \{blockPosition\.y\} \/ \{blockPosition\.z\}"[\s\S]*?container:\s*"Pill"/,
    );
});
