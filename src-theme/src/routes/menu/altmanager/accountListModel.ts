import type {Account} from "../../../integration/types";

export interface AccountListFilters {
    premiumOnly: boolean;
    favoritesOnly: boolean;
    accountTypes: readonly string[];
    searchQuery: string;
}

export function filterAccounts(
    accounts: readonly Account[],
    filters: AccountListFilters,
): Account[] {
    let filteredAccounts = [...accounts];
    if (filters.premiumOnly) {
        filteredAccounts = filteredAccounts.filter(account => account.type !== "Cracked");
    }
    if (filters.favoritesOnly) {
        filteredAccounts = filteredAccounts.filter(account => account.favorite);
    }
    if (!filters.accountTypes.includes("Mojang")) {
        filteredAccounts = filteredAccounts.filter(account =>
            account.type !== "Cracked" && account.type !== "Microsoft",
        );
    }
    if (!filters.accountTypes.includes("TheAltening")) {
        filteredAccounts = filteredAccounts.filter(account => account.type !== "TheAltening");
    }
    if (filters.searchQuery) {
        const query = filters.searchQuery.toLocaleLowerCase();
        filteredAccounts = filteredAccounts.filter(account =>
            account.username.toLocaleLowerCase().includes(query),
        );
    }
    return filteredAccounts;
}

export function nonFavoriteAccountIds(accounts: readonly Account[]): number[] {
    return accounts
        .filter(account => !account.favorite)
        .map(account => account.id)
        .sort((left, right) => right - left);
}
