import assert from "node:assert/strict";
import test from "node:test";

import {
    activeBans,
    availableServers,
    findServerIcon,
    formatRemainingBanTime,
} from "../src/routes/menu/altmanager/banBadges.ts";

const NOW = Date.UTC(2026, 6, 18, 12);
const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

test("formats a temporary ban as days and hours", () => {
    assert.equal(formatRemainingBanTime(NOW + (2 * DAY) + (5 * HOUR), NOW), "2 days and 5 hours");
});

test("uses singular time units", () => {
    assert.equal(formatRemainingBanTime(NOW + DAY + HOUR, NOW), "1 day and 1 hour");
});

test("rounds a partial final hour up", () => {
    assert.equal(formatRemainingBanTime(NOW + 1, NOW), "1 hour");
});

test("formats a permanent ban", () => {
    assert.equal(formatRemainingBanTime(-1, NOW), "Permanent");
});

test("returns only permanent and unexpired bans", () => {
    const bans = {
        expired: {serverName: "expired.example", reason: "", bannedUntil: NOW - 1},
        temporary: {serverName: "temporary.example", reason: "", bannedUntil: NOW + HOUR},
        permanent: {serverName: "permanent.example", reason: "", bannedUntil: -1},
    };

    assert.deepEqual(activeBans(bans, NOW).map(ban => ban.serverName), [
        "temporary.example",
        "permanent.example",
    ]);
});

test("matches a saved server icon by domain while ignoring its port", () => {
    const servers = [{
        address: "mc.hypixel.net:25565",
        name: "Hypixel",
        icon: "server-icon",
    }];

    assert.equal(findServerIcon("hypixel.net", servers), "server-icon");
});

test("hides a working indicator while the same server has an active ban", () => {
    const bans = {
        example: {serverName: "example.net", reason: "Banned", bannedUntil: NOW + HOUR},
    };

    assert.deepEqual(availableServers(["play.example.net", "other.net"], bans, NOW), ["other.net"]);
});
