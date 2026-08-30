<script lang="ts">
    import type {Account} from "../../../integration/types";
    import FavoriteAccountDeleteModal from "./FavoriteAccountDeleteModal.svelte";
    import {requiresFavoriteDeletionConfirmation} from "./accountDeletion.js";

    export let onRemove: (id: number) => Promise<void>;

    let pendingAccount: Account | null = null;
    let visible = false;
    let deleting = false;

    export function request(account: Account): void {
        if (!requiresFavoriteDeletionConfirmation(account)) {
            void onRemove(account.id);
            return;
        }
        pendingAccount = account;
        visible = true;
    }

    async function confirm(): Promise<void> {
        const account = pendingAccount;
        if (!account || deleting) {
            return;
        }
        deleting = true;
        try {
            await onRemove(account.id);
            visible = false;
            pendingAccount = null;
        } finally {
            deleting = false;
        }
    }

    function cancel(): void {
        if (!deleting) {
            pendingAccount = null;
        }
    }
</script>

<FavoriteAccountDeleteModal
        bind:visible
        username={pendingAccount?.username ?? ""}
        loading={deleting}
        on:confirm={confirm}
        on:cancel={cancel}
/>
