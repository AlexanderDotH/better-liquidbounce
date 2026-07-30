import type {Account} from "../../../integration/types.js";

export function requiresFavoriteDeletionConfirmation(account: Pick<Account, "favorite">): boolean {
    return account.favorite;
}
