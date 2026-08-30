<script lang="ts">
    import OptionBar from "../common/optionbar/OptionBar.svelte";
    import MenuList from "../common/menulist/MenuList.svelte";
    import BottomButtonWrapper from "../common/buttons/BottomButtonWrapper.svelte";
    import ButtonContainer from "../common/buttons/ButtonContainer.svelte";
    import IconTextButton from "../common/buttons/IconTextButton.svelte";
    import Search from "../common/Search.svelte";
    import {onDestroy, onMount} from "svelte";
    import {
        browse,
        getClientInfo,
        getLanServers,
        getModule,
        getProtocols,
        getSelectedProtocol,
        getServers,
        getSpooferSettings,
        openScreen,
        orderServers,
        reconnectFritzBox,
        removeServer as removeServerRest,
        setModuleEnabled,
        setSelectedProtocol,
        setSpooferSettings
    } from "../../../integration/rest";
    import type {ClientInfo, ConfigurableSetting, Protocol, Server} from "../../../integration/types";
    import {listen} from "../../../integration/ws";
    import SingleSelect from "../common/setting/select/SingleSelect.svelte";
    import AddServerModal from "./AddServerModal.svelte";
    import DirectConnectModal from "./DirectConnectModal.svelte";
    import EditServerModal from "./EditServerModal.svelte";
    import FritzBoxPasswordModal from "./FritzBoxPasswordModal.svelte";
    import type {ServerPingedEvent} from "../../../integration/events";
    import ButtonSetting from "../common/setting/ButtonSetting.svelte";
    import Divider from "../common/optionbar/Divider.svelte";
    import WrappedSetting from "../common/setting/WrappedSetting.svelte";
    import SwitchSetting from "../common/setting/SwitchSetting.svelte";
    import {
        filterMultiplayerServers,
        formatFritzBoxError,
        updatePingedServer,
    } from "./multiplayerModel.ts";
    import MultiplayerServerItem from "./MultiplayerServerItem.svelte";

    let onlineOnly = false;
    let searchQuery = "";
    let addServerModalVisible = false;
    let directConnectModalVisible = false;

    let editServerModalVisible = false;
    let currentEditServer: Server | null = null;

    let clientInfo: ClientInfo | null = null;
    let autoConfig = false;
    let fritzBoxReconnectLoading = false;
    let fritzBoxPasswordModalVisible = false;
    let fritzBoxStatus: string | null = null;
    let spooferConfigurable: ConfigurableSetting | null = null;
    let servers: Server[] = [];
    let lanServers: Server[] = [];
    let renderedServers: Server[] = [];
    let protocols: Protocol[] = [];
    let selectedProtocol: Protocol = {name: "", version: -1};

    $: renderedServers = filterMultiplayerServers(servers, lanServers, {
        onlineOnly,
        searchQuery,
    });

    // The amount of times the server list has been sorted.
    // It is only used in the key-block below to cause a full re-render after the server have been sorted.
    // This is necessary because LiquidBounce references servers by their index (the id).
    // The id does not change when the element is being sorted.
    // I'm not keying on 'servers' because I don't want to re-render the entire list every time a ping event is received.
    // This is a hack and there should be a better solution.
    let timesSorted = 0;

    let lanPollInterval: ReturnType<typeof setInterval> | null = null;

    onMount(async () => {
        clientInfo = await getClientInfo();
        spooferConfigurable = await getSpooferSettings();
        autoConfig = (await getModule("AutoConfig")).enabled;
        await refreshServers();
        await refreshLanServers();
        renderedServers = [...servers, ...lanServers];
        protocols = await getProtocols();
        selectedProtocol = await getSelectedProtocol();

        // Poll for LAN servers every 3 seconds
        lanPollInterval = setInterval(refreshLanServers, 3000);
    });

    listen("serverPinged", (pingedEvent: ServerPingedEvent) => {
        servers = updatePingedServer(servers, pingedEvent.server);
    });

    async function refreshServers() {
        servers = await getServers();
        await refreshLanServers();
    }

    async function refreshLanServers() {
        lanServers = await getLanServers();
    }

    onDestroy(() => {
        if (lanPollInterval !== null) {
            clearInterval(lanPollInterval);
        }
    });

    async function removeServer(index: number) {
        await removeServerRest(index);
        await refreshServers();
    }

    async function changeProtocolVersion(e: CustomEvent<{ value: string }>) {
        const p = protocols.find(p => p.name == e.detail.value);
        if (!p) {
            return;
        }

        await setSelectedProtocol(p);
        selectedProtocol = await getSelectedProtocol();
    }

    async function handleServerSort(e: CustomEvent<{ newOrder: number[] }>) {
        await orderServers(e.detail.newOrder);
        await refreshServers();
        renderedServers = [...servers, ...lanServers];
        timesSorted++; // See declaration
    }

    function editServer(server: Server) {
        currentEditServer = server;
        editServerModalVisible = true;
    }

    async function updateSpooferSettings() {
        if (!spooferConfigurable) {
            return;
        }

        await setSpooferSettings(spooferConfigurable);
        spooferConfigurable = await getSpooferSettings();
    }

    async function updateAutoConfigState() {
        await setModuleEnabled("AutoConfig", autoConfig);
    }

    function openFritzBoxPasswordPrompt() {
        if (fritzBoxReconnectLoading) {
            return;
        }

        fritzBoxStatus = null;
        fritzBoxPasswordModalVisible = true;
    }

    async function changeFritzBoxIp(e: CustomEvent<{ password: string }>) {
        if (fritzBoxReconnectLoading) {
            return;
        }

        fritzBoxReconnectLoading = true;
        fritzBoxStatus = null;

        try {
            const result = await reconnectFritzBox(e.detail.password);
            fritzBoxStatus = result.newIp && result.newIp !== result.oldIp ? "Changed" : "Done";
        } catch (error) {
            fritzBoxStatus = formatFritzBoxError(error);
        } finally {
            fritzBoxReconnectLoading = false;
        }
    }

</script>

<AddServerModal bind:visible={addServerModalVisible} on:serverAdd={refreshServers}/>

{#if currentEditServer}
    <EditServerModal bind:visible={editServerModalVisible} address={currentEditServer.address}
                     name={currentEditServer.name} on:serverEdit={refreshServers} id={currentEditServer.id}
                     resourcePackPolicy={currentEditServer.resourcePackPolicy}/>
{/if}

<DirectConnectModal bind:visible={directConnectModalVisible}/>
<FritzBoxPasswordModal bind:visible={fritzBoxPasswordModalVisible} on:reconnect={changeFritzBoxIp}/>

<OptionBar>
    <Search on:search={(event) => searchQuery = event.detail.query}/>

    <SwitchSetting title="Online only" bind:value={onlineOnly}/>
    <Divider/>
    <SwitchSetting title="Auto Config" bind:value={autoConfig} on:change={updateAutoConfigState}/>
    <ButtonSetting title={fritzBoxStatus ?? "FritzBox IP"} loading={fritzBoxReconnectLoading}
                   disabled={fritzBoxReconnectLoading} on:click={openFritzBoxPasswordPrompt}/>
    {#if spooferConfigurable}
        <WrappedSetting bind:value={spooferConfigurable} on:change={updateSpooferSettings} path="multiplayer.spoofer"/>
    {/if}
    {#if clientInfo && clientInfo.viaFabricPlus}
        <SingleSelect title="Version" value={selectedProtocol.name} options={protocols.map(p => p.name)}
                      on:change={changeProtocolVersion}/>
        <ButtonSetting title="ViaFabricPlus" on:click={() => openScreen("viafabricplus_protocol_selection")}/>
    {:else}
        <ButtonSetting title="Install ViaFabricPlus" on:click={() => browse("VIAFABRICPLUS")}/>
    {/if}
</OptionBar>

<MenuList sortable={renderedServers.length === servers.length && lanServers.length === 0} elementCount={servers.length}
          on:sort={handleServerSort}>
    {#key timesSorted}
        {#each renderedServers as server}
            <MultiplayerServerItem {server} onRemove={removeServer} onEdit={editServer}/>
        {/each}
    {/key}
</MenuList>

<BottomButtonWrapper>
    <ButtonContainer>
        <IconTextButton icon="icon-plus-circle.svg" title="Add" on:click={() => addServerModalVisible = true}/>
        <IconTextButton icon="icon-plane.svg" title="Direct" on:click={() => directConnectModalVisible = true}/>
        <IconTextButton icon="icon-refresh.svg" title="Refresh" on:click={refreshServers}/>
    </ButtonContainer>

    <ButtonContainer>
        <IconTextButton icon="icon-back.svg" title="Back" on:click={() => openScreen("title")}/>
    </ButtonContainer>
</BottomButtonWrapper>
