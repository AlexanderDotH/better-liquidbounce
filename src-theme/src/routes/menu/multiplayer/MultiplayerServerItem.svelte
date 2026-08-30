<script lang="ts">
    import {connectToServer} from "../../../integration/rest";
    import {REST_BASE} from "../../../integration/host";
    import type {Server} from "../../../integration/types";
    import MenuListItem from "../common/menulist/MenuListItem.svelte";
    import MenuListItemButton from "../common/menulist/MenuListItemButton.svelte";
    import MenuListItemTag from "../common/menulist/MenuListItemTag.svelte";
    import TextComponent from "../../../components/text/TextComponent.svelte";
    import {multiplayerPingColor} from "./multiplayerModel.ts";

    export let server: Server;
    export let onRemove: (id: number) => void;
    export let onEdit: (server: Server) => void;
</script>

<MenuListItem
        imageText={server.ping > 0 ? `${server.ping}ms` : null}
        imageTextBackgroundColor={multiplayerPingColor(server.ping)}
        image={server.ping < 0 || !server.icon
            ? `${REST_BASE}/api/v1/client/resource?id=minecraft:textures/misc/unknown_server.png`
            : `data:image/png;base64,${server.icon}`}
        title={server.name}
        on:dblclick={() => connectToServer(server.address)}
>
    <TextComponent
            allowPreformatting={true}
            preFormattingMonospace={false}
            slot="subtitle"
            fontSize={18}
            textComponent={server.ping <= 0 ? "§CCan't connect to server" : server.label}
    />

    <svelte:fragment slot="tag">
        {#if server.lan}<MenuListItemTag text="LAN"/>{/if}
        {#if server.ping > 0}
            <MenuListItemTag text="{server.players.online}/{server.players.max} Players"/>
            <MenuListItemTag text={server.version}/>
        {/if}
    </svelte:fragment>

    <svelte:fragment slot="active-visible">
        {#if !server.lan}
            <MenuListItemButton title="Remove" icon="trash" on:click={() => onRemove(server.id)}/>
            <MenuListItemButton title="Edit" icon="pen-2" on:click={() => onEdit(server)}/>
        {/if}
    </svelte:fragment>

    <svelte:fragment slot="always-visible">
        <MenuListItemButton title="Join" icon="play" on:click={() => connectToServer(server.address)}/>
    </svelte:fragment>
</MenuListItem>
