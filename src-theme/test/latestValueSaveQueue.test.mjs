import assert from "node:assert/strict";
import test from "node:test";

import {createLatestValueSaveQueue} from "../src/routes/clickgui/theme/latestValueSaveQueue.ts";

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((nextResolve, nextReject) => {
        resolve = nextResolve;
        reject = nextReject;
    });

    return {promise, resolve, reject};
}

test("coalesces edits made during a save and confirms only the latest value", async () => {
    const firstSave = deferred();
    const saved = [];
    const confirmed = [];
    let stored = 0;
    const queue = createLatestValueSaveQueue({
        save: async value => {
            saved.push(value);
            if (value === 1) {
                await firstSave.promise;
            }
            stored = value;
        },
        reload: async () => stored,
        onConfirmed: value => confirmed.push(value),
    });

    queue.enqueue(1);
    queue.enqueue(2);
    firstSave.resolve();
    await queue.whenIdle();

    assert.deepEqual(saved, [1, 2]);
    assert.deepEqual(confirmed, [2]);
    assert.equal(queue.hasPending(), false);
});

test("does not overwrite an edit queued while confirmation is loading", async () => {
    const firstReload = deferred();
    const confirmed = [];
    let reloadCount = 0;
    let stored = 0;
    const queue = createLatestValueSaveQueue({
        save: async value => {
            stored = value;
        },
        reload: async () => {
            reloadCount += 1;
            if (reloadCount === 1) {
                await firstReload.promise;
            }
            return stored;
        },
        onConfirmed: value => confirmed.push(value),
    });

    queue.enqueue(1);
    await Promise.resolve();
    queue.enqueue(2);
    firstReload.resolve();
    await queue.whenIdle();

    assert.deepEqual(confirmed, [2]);
    assert.equal(reloadCount, 2);
});

test("keeps the failed latest value pending until an explicit retry", async () => {
    let fail = true;
    const states = [];
    const queue = createLatestValueSaveQueue({
        save: async () => {
            if (fail) {
                throw new Error("rejected");
            }
        },
        reload: async () => 4,
        onConfirmed: () => {},
        onStateChange: state => states.push(state),
    });

    queue.enqueue(4);
    await queue.whenIdle();

    assert.equal(queue.hasPending(), true);
    assert.equal(states.at(-1).saving, false);
    assert.match(states.at(-1).error.message, /rejected/);

    fail = false;
    queue.retry();
    await queue.whenIdle();

    assert.equal(queue.hasPending(), false);
    assert.equal(states.at(-1).error, null);
});

test("a failed confirmation can retry the latest successful write", async () => {
    let reloadFails = true;
    const saved = [];
    const queue = createLatestValueSaveQueue({
        save: async value => {
            saved.push(value);
        },
        reload: async () => {
            if (reloadFails) {
                throw new Error("confirmation unavailable");
            }

            return 7;
        },
        onConfirmed: () => {},
    });

    queue.enqueue(7);
    await queue.whenIdle();
    assert.equal(queue.hasPending(), true);

    reloadFails = false;
    queue.retry();
    await queue.whenIdle();

    assert.deepEqual(saved, [7, 7]);
    assert.equal(queue.hasPending(), false);
});
