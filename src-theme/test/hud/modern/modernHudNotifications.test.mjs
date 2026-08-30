import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";

const notificationsRoot = new URL(
    "../../../src/routes/hud/elements/notifications/",
    import.meta.url,
);

function read(relativePath) {
    return readFileSync(new URL(relativePath, notificationsRoot), "utf8");
}

test("Modern module toggles use a compact one-line pill while other notifications keep their details", () => {
    const notification = read("Notification.svelte");

    assert.match(notification, /export let variant:\s*"classic"\s*\|\s*"modern"/);
    assert.match(notification, /variant === "modern" && isModuleToggle/);
    assert.match(notification, /class="notification module-toggle-notification/);
    assert.match(notification, /class="status-dot"/);
    assert.match(notification, /class="module-name">\{message\}/);
    assert.match(notification, /class="state">\{title\}/);
    assert.match(notification, /class="icon \{severity\.toString\(\)\.toLowerCase\(\)\}"/);
});

test("Repeated module toggles reuse a stable key and restart one expiry timer", () => {
    const notifications = read("Notifications.svelte");

    assert.doesNotMatch(notifications, /Date\.now\(\)/);
    assert.doesNotMatch(notifications, /animationKey/);
    assert.match(notifications, /let nextNotificationId = 1/);
    assert.match(notifications, /const expiryTimers = new Map/);
    assert.match(notifications, /clearTimeout\(existingTimer\)/);
    assert.match(notifications, /existingNotification\.id/);
    assert.match(notifications, /\(notification\.id\)/);
    assert.match(notifications, /onDestroy\(\(\) =>/);
});

test("Modern notification transitions use 160ms motion while Classic keeps its original profile", () => {
    const notifications = read("Notifications.svelte");

    assert.match(notifications, /prefersReducedMotion/);
    assert.match(notifications, /hudMotionDuration\(variant,\s*\$prefersReducedMotion\)/);
    assert.doesNotMatch(notifications, /matchMedia\(/);
    assert.match(notifications, /motionOffset = variant === "modern" \? 18 : 30/);
    assert.match(notifications, /animate:flip=\{\{ duration: motionDuration \}\}/);
    assert.match(notifications, /in:fly=\{\{ x: motionOffset, duration: motionDuration \}\}/);
    assert.match(notifications, /out:fly=\{\{ x: motionOffset, duration: motionDuration \}\}/);
});
