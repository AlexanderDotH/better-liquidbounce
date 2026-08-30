import assert from "node:assert/strict";
import test from "node:test";

import {
    filterAccounts,
    nonFavoriteAccountIds,
} from "../src/routes/menu/altmanager/accountListModel.ts";

const accounts = [
    {id: 1, username: "CrackedAlex", type: "Cracked", favorite: false},
    {id: 2, username: "MicrosoftAlex", type: "Microsoft", favorite: true},
    {id: 3, username: "AlteningAlex", type: "TheAltening", favorite: false},
];

test("account filters preserve source order across premium, favorite, type, and query filters", () => {
    assert.deepEqual(
        filterAccounts(accounts, {
            premiumOnly: true,
            favoritesOnly: false,
            accountTypes: ["Mojang"],
            searchQuery: "microsoft",
        }),
        [accounts[1]],
    );
    assert.deepEqual(
        filterAccounts(accounts, {
            premiumOnly: false,
            favoritesOnly: true,
            accountTypes: ["Mojang", "TheAltening"],
            searchQuery: "",
        }),
        [accounts[1]],
    );
});

test("non-favorite account ids are removed from highest index to lowest", () => {
    assert.deepEqual(nonFavoriteAccountIds(accounts), [3, 1]);
});
