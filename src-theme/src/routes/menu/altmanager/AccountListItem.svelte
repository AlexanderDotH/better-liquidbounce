<script lang="ts">
    import type {Account, Server} from "../../../integration/types";
    import {REST_BASE} from "../../../integration/host";
    import MenuListItem from "../common/menulist/MenuListItem.svelte";
    import MenuListItemButton from "../common/menulist/MenuListItemButton.svelte";
    import MenuListItemTag from "../common/menulist/MenuListItemTag.svelte";
    import {activeBans, availableServers, findServerIcon, formatRemainingBanTime} from "./banBadges.js";

    export let account: Account;
    export let servers: Server[];
    export let banTime: number;
    export let onRequestRemoval: (account: Account) => void;
    export let onToggleFavorite: (id: number, favorite: boolean) => void;
    export let onLogin: (id: number) => void;

    function serverIcon(serverName: string): string {
        const icon = findServerIcon(serverName, servers);
        return icon
            ? `data:image/png;base64,${icon}`
            : `${REST_BASE}/api/v1/client/resource?id=minecraft:textures/misc/unknown_server.png`;
    }
</script>

<MenuListItem
        image={account.avatar}
        title={account.username}
        favorite={account.favorite}
        on:dblclick={() => onLogin(account.id)}
>
    <svelte:fragment slot="subtitle"><pre class="uuid">{account.uuid}</pre></svelte:fragment>
    <svelte:fragment slot="tag">
        <MenuListItemTag text={account.type}/>
        {#each availableServers(account.workingServers, account.bans, banTime) as serverName (serverName)}
            <MenuListItemTag text="Works" icon={serverIcon(serverName)}
                             tooltip={`Works on ${serverName}`} success={true}/>
        {/each}
        {#each activeBans(account.bans, banTime) as ban (ban.serverName)}
            <MenuListItemTag text={formatRemainingBanTime(ban.bannedUntil, banTime)}
                             icon={serverIcon(ban.serverName)} tooltip={`${ban.serverName}: ${ban.reason}`}/>
        {/each}
    </svelte:fragment>
    <svelte:fragment slot="active-visible">
        <MenuListItemButton title="Delete" icon="trash" on:click={() => onRequestRemoval(account)}/>
        <MenuListItemButton title="Favorite" icon={account.favorite ? "favorite-filled" : "favorite"}
                            on:click={() => onToggleFavorite(account.id, !account.favorite)}/>
    </svelte:fragment>
    <svelte:fragment slot="always-visible">
        <MenuListItemButton title="Login" icon="play" on:click={() => onLogin(account.id)}/>
    </svelte:fragment>
</MenuListItem>

<style>
  .uuid {
    font-family: monospace;
  }
</style>
