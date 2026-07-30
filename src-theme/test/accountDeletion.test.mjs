import assert from "node:assert/strict";
import test from "node:test";

import {requiresFavoriteDeletionConfirmation} from "../src/routes/menu/altmanager/accountDeletion.ts";

test("favorite accounts require deletion confirmation", () => {
    assert.equal(requiresFavoriteDeletionConfirmation({favorite: true}), true);
});

test("non-favorite accounts can be deleted immediately", () => {
    assert.equal(requiresFavoriteDeletionConfirmation({favorite: false}), false);
});
