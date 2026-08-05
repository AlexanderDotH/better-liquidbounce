import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const themeRoot = new URL("../", import.meta.url);

function read(relativePath) {
    return readFileSync(new URL(relativePath, themeRoot), "utf8");
}

test("Coordinates is an opt-in singleton at the requested default position", () => {
    const metadata = JSON.parse(read("public/metadata.json"));
    const definition = JSON.parse(read("public/components/coordinates.json"));
    const hud = read("src/routes/hud/Hud.svelte");

    assert.ok(metadata.components.includes("Coordinates"));
    assert.deepEqual(definition, {
        name: "Coordinates",
        description: "Displays your current block coordinates.",
        enabled: false,
        singleton: true,
        alignment: {
            horizontalAlignment: "Left",
            horizontalOffset: 15,
            verticalAlignment: "Top",
            verticalOffset: 60,
        },
    });
    assert.match(hud, /import Coordinates from "\.\/elements\/Coordinates\.svelte"/);
    assert.match(
        hud,
        /c\.name === "Coordinates"[\s\S]*?<Coordinates\s*\/>/,
    );
});

test("Coordinates refreshes its snapshot and clears stale player data safely", () => {
    const coordinates = read("src/routes/hud/elements/Coordinates.svelte");

    assert.match(coordinates, /import\s+\{getPlayerData\}\s+from\s+"\.\.\/\.\.\/\.\.\/integration\/rest"/);
    assert.match(coordinates, /listen\("clientPlayerData"/);
    assert.match(coordinates, /playerData\s*=\s*event\.playerData/);
    assert.match(coordinates, /listen\("socketReady"[\s\S]*refreshPlayerData\(\)/);
    assert.match(coordinates, /listen\("disconnect"[\s\S]*playerData\s*=\s*null/);
    assert.match(coordinates, /onMount\(refreshPlayerData\)/);
    assert.match(coordinates, /try\s*\{[\s\S]*await getPlayerData\(\)[\s\S]*\}\s*catch\s*\{[\s\S]*playerData\s*=\s*null/);
});

test("Coordinates renders exactly three horizontal block-position axes in one Graphite pill", () => {
    const coordinates = read("src/routes/hud/elements/Coordinates.svelte");

    assert.match(coordinates, /playerData\?\.blockPosition/);
    assert.match(coordinates, /Math\.trunc/);
    assert.equal((coordinates.match(/class="coordinate-axis"/g) ?? []).length, 3);
    assert.equal((coordinates.match(/class="coordinate-pill"/g) ?? []).length, 1);
    assert.match(coordinates, /<span class="axis-label">X<\/span>/);
    assert.match(coordinates, /<span class="axis-label">Y<\/span>/);
    assert.match(coordinates, /<span class="axis-label">Z<\/span>/);
    assert.match(coordinates, /const EMPTY_COORDINATE = "—"/);
    assert.match(coordinates, /display:\s*inline-flex/);
    assert.match(coordinates, /font-variant-numeric:\s*tabular-nums/);
    assert.match(coordinates, /rgba\(15,\s*18,\s*23,\s*0\.84\)/);
    assert.doesNotMatch(coordinates, /flex-direction:\s*column/);
});
