<script lang="ts">
    import {
        deleteScreen,
        generateAlteningAccount,
        getAccounts,
        getServers,
        loginToAccount as loginToAccountRest,
        orderAccounts,
        removeAccount as restRemoveAccount,
        restoreSession,
        setAccountFavorite
    } from "../../../integration/rest.js";
    import {removeItem, setItem} from "../../../integration/persistent_storage";
    import BottomButtonWrapper from "../common/buttons/BottomButtonWrapper.svelte";
    import SwitchSetting from "../common/setting/SwitchSetting.svelte";
    import OptionBar from "../common/optionbar/OptionBar.svelte";
    import MenuListItem from "../common/menulist/MenuListItem.svelte";
    import ButtonContainer from "../common/buttons/ButtonContainer.svelte";
    import MenuListItemTag from "../common/menulist/MenuListItemTag.svelte";
    import MenuList from "../common/menulist/MenuList.svelte";
    import IconTextButton from "../common/buttons/IconTextButton.svelte";
    import Search from "../common/Search.svelte";
    import MenuListItemButton from "../common/menulist/MenuListItemButton.svelte";
    import type {Account, Server} from "../../../integration/types";
    import {onMount} from "svelte";
    import MultiSelect from "../common/setting/select/MultiSelect.svelte";
    import AddAccountModal from "./addaccount/AddAccountModal.svelte";
    import {listen} from "../../../integration/ws";
    import {notification} from "../common/header/notification_store";
    import type {
        AccountManagerAdditionEvent,
        AccountManagerLoginEvent,
    } from "../../../integration/events.js";
    import DirectLoginModal from "./directLogin/DirectLoginModal.svelte";
    import TheAlteningApiKeyModal from "./TheAlteningApiKeyModal.svelte";
    import {activeBans, availableServers, findServerIcon, formatRemainingBanTime} from "./banBadges.js";
    import {REST_BASE} from "../../../integration/host";
    import FavoriteAccountDeleteModal from "./FavoriteAccountDeleteModal.svelte";
    import {requiresFavoriteDeletionConfirmation} from "./accountDeletion.js";

    const THE_ALTENING_API_KEY_STORAGE_KEY = "altmanager_thealtening_api_key";

    let premiumOnly = false;
    let favoritesOnly = false;
    let accountTypes = ["Mojang", "TheAltening"];
    let accounts: Account[] = [];
    let servers: Server[] = [];
    let renderedAccounts: Account[] = [];
    let searchQuery = "";
    let banTime = Date.now();

    let addAccountModalVisible = false;
    let directLoginModalVisible = false;
    let alteningApiKeyModalVisible = false;
    let generatingAltening = false;
    let clearingNonFavorites = false;
    let favoriteAccountPendingDeletion: Account | null = null;
    let favoriteAccountDeleteModalVisible = false;
    let deletingFavoriteAccount = false;

    $: {
        let filteredAccounts = accounts;
        if (premiumOnly) {
            filteredAccounts = filteredAccounts.filter(a => a.type !== "Cracked");
        }
        if (favoritesOnly) {
            filteredAccounts = filteredAccounts.filter(a => a.favorite);
        }
        if (!accountTypes.includes("Mojang")) {
            filteredAccounts = filteredAccounts.filter(a => a.type !== "Cracked" && a.type !== "Microsoft")
        }
        if (!accountTypes.includes("TheAltening")) {
            filteredAccounts = filteredAccounts.filter(a => a.type !== "TheAltening")
        }
        if (searchQuery) {
            filteredAccounts = filteredAccounts.filter(a => a.username.toLowerCase().includes(searchQuery.toLowerCase()));
        }
        renderedAccounts = filteredAccounts;
    }

    async function refreshAccounts() {
        accounts = await getAccounts();
    }

    async function initializeAccountManager() {
        [accounts, servers] = await Promise.all([
            getAccounts(),
            getServers().catch(() => []),
        ]);
        renderedAccounts = accounts;
    }

    onMount(() => {
        void initializeAccountManager();
        const banTimer = window.setInterval(() => banTime = Date.now(), 60_000);

        return () => window.clearInterval(banTimer);
    });

    function serverIcon(serverName: string): string {
        const icon = findServerIcon(serverName, servers);
        return icon
            ? `data:image/png;base64,${icon}`
            : `${REST_BASE}/api/v1/client/resource?id=minecraft:textures/misc/unknown_server.png`;
    }

    function handleSearch(e: CustomEvent<{ query: string }>) {
        searchQuery = e.detail.query;
    }

    async function handleAccountSort(e: CustomEvent<{ newOrder: number[] }>) {
        await orderAccounts(e.detail.newOrder);
        await refreshAccounts();
        renderedAccounts = accounts;
    }

    async function removeAccount(id: number) {
        await restRemoveAccount(id);
        await refreshAccounts();
    }

    function requestAccountRemoval(account: Account) {
        if (!requiresFavoriteDeletionConfirmation(account)) {
            void removeAccount(account.id);
            return;
        }

        favoriteAccountPendingDeletion = account;
        favoriteAccountDeleteModalVisible = true;
    }

    async function confirmFavoriteAccountRemoval() {
        const account = favoriteAccountPendingDeletion;
        if (!account || deletingFavoriteAccount) {
            return;
        }

        deletingFavoriteAccount = true;
        try {
            await removeAccount(account.id);
            favoriteAccountDeleteModalVisible = false;
            favoriteAccountPendingDeletion = null;
        } finally {
            deletingFavoriteAccount = false;
        }
    }

    function cancelFavoriteAccountRemoval() {
        if (!deletingFavoriteAccount) {
            favoriteAccountPendingDeletion = null;
        }
    }

    async function clearNonFavoriteAccounts() {
        if (clearingNonFavorites) {
            return;
        }

        const accountIds = accounts
            .filter(account => !account.favorite)
            .map(account => account.id)
            .sort((left, right) => right - left);

        if (accountIds.length === 0) {
            return;
        }

        clearingNonFavorites = true;
        try {
            for (const accountId of accountIds) {
                await restRemoveAccount(accountId);
            }
            await refreshAccounts();
        } finally {
            clearingNonFavorites = false;
        }
    }

    async function loginToRandomAccount() {
        const account = renderedAccounts[Math.floor(Math.random() * renderedAccounts.length)];
        if (account) {
            await loginToAccount(account.id);
        }
    }

    async function toggleFavorite(index: number, favorite: boolean) {
        await setAccountFavorite(index, favorite);
        await refreshAccounts();
    }

    async function loginToAccount(id: number) {
        notification.set({
            title: "AltManager",
            message: "Logging in...",
            error: false
        });
        await loginToAccountRest(id);
    }

    async function handleGenerateTheAlteningClick() {
        if (generatingAltening) {
            return;
        }

        const apiKey = localStorage.getItem(THE_ALTENING_API_KEY_STORAGE_KEY)?.trim();
        if (!apiKey) {
            alteningApiKeyModalVisible = true;
            return;
        }

        await generateTheAlteningAccount(apiKey, false);
    }

    async function handleGenerateTheAlteningWithApiKey(e: CustomEvent<{ apiKey: string }>) {
        await generateTheAlteningAccount(e.detail.apiKey, true);
    }

    async function generateTheAlteningAccount(apiKey: string, persistOnSuccess: boolean) {
        if (generatingAltening) {
            return;
        }

        generatingAltening = true;
        try {
            const result = await generateAlteningAccount(apiKey);
            switch (result.status) {
                case "SUCCESS":
                    if (persistOnSuccess) {
                        await setItem(THE_ALTENING_API_KEY_STORAGE_KEY, apiKey);
                    }
                    alteningApiKeyModalVisible = false;
                    await refreshAccounts();
                    break;
                case "CREDENTIALS_REQUIRED":
                case "ACCESS_DENIED":
                    await removeItem(THE_ALTENING_API_KEY_STORAGE_KEY);
                    alteningApiKeyModalVisible = true;
                    showTheAlteningError(result.message);
                    break;
                case "ERROR":
                    showTheAlteningError(result.message);
                    break;
            }
        } finally {
            generatingAltening = false;
        }
    }

    function showTheAlteningError(message: string) {
        notification.set({
            title: "AltManager",
            message,
            error: true
        });
    }

    listen("accountManagerAddition", (e: AccountManagerAdditionEvent) => {
        addAccountModalVisible = false;
        refreshAccounts();
    });

    listen("accountManagerLogin", (e: AccountManagerLoginEvent) => {
        directLoginModalVisible = false;
        refreshAccounts();
    });
</script>

<DirectLoginModal bind:visible={directLoginModalVisible}/>

<AddAccountModal bind:visible={addAccountModalVisible}/>
<TheAlteningApiKeyModal
        bind:visible={alteningApiKeyModalVisible}
        loading={generatingAltening}
        on:generate={handleGenerateTheAlteningWithApiKey}/>
<FavoriteAccountDeleteModal
        bind:visible={favoriteAccountDeleteModalVisible}
        username={favoriteAccountPendingDeletion?.username ?? ""}
        loading={deletingFavoriteAccount}
        on:confirm={confirmFavoriteAccountRemoval}
        on:cancel={cancelFavoriteAccountRemoval}/>

<OptionBar>
    <Search on:search={handleSearch}/>
    <SwitchSetting title="Premium Only" bind:value={premiumOnly}/>
    <SwitchSetting title="Favorites Only" bind:value={favoritesOnly}/>
    <MultiSelect title="Account Type" options={["Mojang", "TheAltening"]} bind:values={accountTypes}/>
</OptionBar>

<MenuList sortable={accounts.length === renderedAccounts.length} elementCount={accounts.length}
          on:sort={handleAccountSort}>
    {#key accounts}
        {#each renderedAccounts as account}
            <MenuListItem
                    image={account.avatar}
                    title={account.username}
                    favorite={account.favorite}
                    on:dblclick={() => loginToAccount(account.id)}>
                <svelte:fragment slot="subtitle">
                    <pre class="uuid">{account.uuid}</pre>
                </svelte:fragment>

                <svelte:fragment slot="tag">
                    <MenuListItemTag text={account.type}/>
                    {#each availableServers(account.workingServers, account.bans, banTime) as serverName (serverName)}
                        <MenuListItemTag
                                text="Works"
                                icon={serverIcon(serverName)}
                                tooltip={`Works on ${serverName}`}
                                success={true}/>
                    {/each}
                    {#each activeBans(account.bans, banTime) as ban (ban.serverName)}
                        <MenuListItemTag
                                text={formatRemainingBanTime(ban.bannedUntil, banTime)}
                                icon={serverIcon(ban.serverName)}
                                tooltip={`${ban.serverName}: ${ban.reason}`}/>
                    {/each}
                </svelte:fragment>

                <svelte:fragment slot="active-visible">
                    <MenuListItemButton title="Delete" icon="trash"
                                        on:click={() => requestAccountRemoval(account)}/>
                    <MenuListItemButton title="Favorite" icon={account.favorite ? "favorite-filled" : "favorite" }
                                        on:click={() => toggleFavorite(account.id, !account.favorite)}/>
                </svelte:fragment>

                <svelte:fragment slot="always-visible">
                    <MenuListItemButton title="Login" icon="play" on:click={() => loginToAccount(account.id)}/>
                </svelte:fragment>
            </MenuListItem>
        {/each}
    {/key}
</MenuList>

<BottomButtonWrapper>
    <ButtonContainer>
        <IconTextButton icon="icon-plus-circle.svg" title="Add" on:click={() => addAccountModalVisible = true}/>
        <IconTextButton icon="icon-plane.svg" title="Direct" on:click={() => directLoginModalVisible = true}/>
        <IconTextButton icon="icon-random.svg" disabled={renderedAccounts.length === 0} title="Random"
                        on:click={loginToRandomAccount}/>
        <IconTextButton icon="icon-refresh.svg" title="Restore" on:click={restoreSession}/>
    </ButtonContainer>

    <ButtonContainer>
        <IconTextButton icon="altmanager/icon-thealtening-white.svg" title="Generate" disabled={generatingAltening}
                        on:click={handleGenerateTheAlteningClick}/>
    </ButtonContainer>

    <ButtonContainer>
        <IconTextButton icon="icon-trash.svg" title="Clear Non-Favorites"
                        disabled={clearingNonFavorites || accounts.every(account => account.favorite)}
                        on:click={clearNonFavoriteAccounts}/>
    </ButtonContainer>

    <ButtonContainer>
        <IconTextButton icon="icon-back.svg" title="Back" on:click={() => deleteScreen()}/>
    </ButtonContainer>
</BottomButtonWrapper>

<style lang="scss">
  .uuid {
    font-family: monospace;
  }
</style>
