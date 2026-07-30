import assert from "node:assert/strict";
import test from "node:test";

import {ensureSuccessfulResponse} from "../src/routes/clickgui/themes/modern/model/checkedResponse.ts";

test("checked ClickGUI requests accept successful responses", async () => {
    await assert.doesNotReject(
        ensureSuccessfulResponse(new Response(null, {status: 204}), "save settings"),
    );
});

test("checked ClickGUI requests expose status and bounded server details", async () => {
    const details = "x".repeat(300);

    await assert.rejects(
        ensureSuccessfulResponse(
            new Response(details, {
                status: 500,
                statusText: "Server Error",
            }),
            "save settings",
        ),
        error => {
            assert.match(error.message, /500 Server Error/);
            assert.match(error.message, /x{240}$/);
            assert.doesNotMatch(error.message, /x{241}/);
            return true;
        },
    );
});
