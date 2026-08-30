<script lang="ts">
    import {
        deleteScreen,
        getAccounts,
        getServers,
        loginToAccount as loginToAccountRest,
        orderAccounts,
        removeAccount as restRemoveAccount,
        restoreSession,
        setAccountFavorite
    } from "../../../integration/rest.js";
    import BottomButtonWrapper from "../common/buttons/BottomButtonWrapper.svelte";
    import SwitchSetting from "../common/setting/SwitchSetting.svelte";
    import OptionBar from "../common/optionbar/OptionBar.svelte";
    import ButtonContainer from "../common/buttons/ButtonContainer.svelte";
    import MenuList from "../common/menulist/MenuList.svelte";
    import IconTextButton from "../common/buttons/IconTextButton.svelte";
    import Search from "../common/Search.svelte";
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
    import {filterAccounts, nonFavoriteAccountIds} from "./accountListModel.ts";
    import AccountListItem from "./AccountListItem.svelte";
    import FavoriteAccountDeletionFlow from "./FavoriteAccountDeletionFlow.svelte";
    import TheAlteningGenerator from "./TheAlteningGenerator.svelte";

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
    let clearingNonFavorites = false;
    let favoriteAccountDeletionFlow: FavoriteAccountDeletionFlow;

    $: renderedAccounts = filterAccounts(accounts, {
        premiumOnly,
        favoritesOnly,
        accountTypes,
        searchQuery,
    });

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

    async function clearNonFavoriteAccounts() {
        if (clearingNonFavorites) {
            return;
        }

        const accountIds = nonFavoriteAccountIds(accounts);

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
<FavoriteAccountDeletionFlow bind:this={favoriteAccountDeletionFlow} onRemove={removeAccount}/>

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
            <AccountListItem
                    {account}
                    {servers}
                    {banTime}
                    onRequestRemoval={account => favoriteAccountDeletionFlow.request(account)}
                    onToggleFavorite={toggleFavorite}
                    onLogin={loginToAccount}
            />
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
        <TheAlteningGenerator onGenerated={refreshAccounts}/>
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
