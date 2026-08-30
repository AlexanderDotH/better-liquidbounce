import assert from "node:assert/strict";
import test from "node:test";

import {
    filterMultiplayerServers,
    formatFritzBoxError,
    multiplayerPingColor,
    updatePingedServer,
} from "../src/routes/menu/multiplayer/multiplayerModel.ts";

const savedServer = {
    id: 4,
    name: "Saved name",
    address: "play.example.net",
    resourcePackPolicy: "PROMPT",
    ping: -1,
};

test("server filtering preserves saved-before-LAN ordering and applies both filters", () => {
    const lanServer = {id: 9, name: "LAN party", address: "192.168.1.2", ping: 3, lan: true};
    const rendered = filterMultiplayerServers(
        [savedServer, {...savedServer, id: 5, name: "Offline", address: "offline.test", ping: -1}],
        [lanServer],
        {onlineOnly: true, searchQuery: "party"},
    );

    assert.deepEqual(rendered, [lanServer]);
});

test("ping updates retain saved identity fields without mutating the server list", () => {
    const pinged = {...savedServer, id: 99, name: "Remote name", resourcePackPolicy: "DISABLED", ping: 42};
    const original = [savedServer];
    const updated = updatePingedServer(original, pinged);

    assert.notEqual(updated, original);
    assert.deepEqual(updated[0], {...pinged, id: 4, name: "Saved name", resourcePackPolicy: "PROMPT"});
    assert.equal(original[0].ping, -1);
});

test("ping colors and FritzBox errors retain the menu labels", () => {
    assert.equal(multiplayerPingColor(-1), "#E84C3D");
    assert.equal(multiplayerPingColor(50), "#2DCC70");
    assert.equal(multiplayerPingColor(100), "#F1C40F");
    assert.equal(multiplayerPingColor(101), "#E84C3D");
    assert.equal(formatFritzBoxError(new Error("login failed")), "Login failed");
    assert.equal(formatFritzBoxError(new Error("HTTP 503")), "HTTP failed");
    assert.equal(formatFritzBoxError("unknown"), "Failed");
});
