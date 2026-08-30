<script lang="ts">
    import {generateAlteningAccount} from "../../../integration/rest.js";
    import {removeItem, setItem} from "../../../integration/persistent_storage";
    import IconTextButton from "../common/buttons/IconTextButton.svelte";
    import {notification} from "../common/header/notification_store";
    import TheAlteningApiKeyModal from "./TheAlteningApiKeyModal.svelte";

    export let onGenerated: () => Promise<void>;

    const API_KEY_STORAGE_KEY = "altmanager_thealtening_api_key";

    let modalVisible = false;
    let generating = false;

    async function generateWithSavedKey(): Promise<void> {
        if (generating) {
            return;
        }
        const apiKey = localStorage.getItem(API_KEY_STORAGE_KEY)?.trim();
        if (!apiKey) {
            modalVisible = true;
            return;
        }
        await generate(apiKey, false);
    }

    async function generateWithApiKey(event: CustomEvent<{apiKey: string}>): Promise<void> {
        await generate(event.detail.apiKey, true);
    }

    async function generate(apiKey: string, persistOnSuccess: boolean): Promise<void> {
        if (generating) {
            return;
        }
        generating = true;
        try {
            const result = await generateAlteningAccount(apiKey);
            if (result.status === "SUCCESS") {
                if (persistOnSuccess) {
                    await setItem(API_KEY_STORAGE_KEY, apiKey);
                }
                modalVisible = false;
                await onGenerated();
                return;
            }
            if (result.status === "CREDENTIALS_REQUIRED" || result.status === "ACCESS_DENIED") {
                await removeItem(API_KEY_STORAGE_KEY);
                modalVisible = true;
            }
            notification.set({title: "AltManager", message: result.message, error: true});
        } finally {
            generating = false;
        }
    }
</script>

<TheAlteningApiKeyModal bind:visible={modalVisible} loading={generating} on:generate={generateWithApiKey}/>
<IconTextButton
        icon="altmanager/icon-thealtening-white.svg"
        title="Generate"
        disabled={generating}
        on:click={generateWithSavedKey}
/>
