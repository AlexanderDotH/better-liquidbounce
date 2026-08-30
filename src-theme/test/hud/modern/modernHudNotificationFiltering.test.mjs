import assert from "node:assert/strict";
import test from "node:test";

import {notificationSeverityEnabled} from "../../../src/routes/hud/elements/notifications/notificationModel.ts";

test("legacy notification settings keep every severity enabled", () => {
    for (const severity of ["INFO", "SUCCESS", "ERROR", "ENABLED", "DISABLED"]) {
        assert.equal(notificationSeverityEnabled(undefined, severity), true);
        assert.equal(notificationSeverityEnabled({}, severity), true);
    }
});

test("notification severity filtering follows the latest settings", () => {
    const errorsOnly = {severities: ["ERROR"]};
    const everything = {severities: ["INFO", "SUCCESS", "ERROR", "ENABLED", "DISABLED"]};

    assert.equal(notificationSeverityEnabled(errorsOnly, "INFO"), false);
    assert.equal(notificationSeverityEnabled(errorsOnly, "ERROR"), true);
    assert.equal(notificationSeverityEnabled(everything, "INFO"), true);
});
