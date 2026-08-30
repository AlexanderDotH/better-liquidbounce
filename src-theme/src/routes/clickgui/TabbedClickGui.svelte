<script lang="ts">
    import ClickGui from "./ClickGui.svelte";
    import GlobalSettings from "./tabs/GlobalSettings.svelte";
    import Tabs from "./tabs/Tabs.svelte";
    import {
        clickGuiThemeSession,
        gridSize,
        os,
        scaleFactor,
        snappingEnabled,
        type ClickGuiView,
    } from "./clickgui_store";
    import type {ConfigurableSetting, TogglableSetting} from "../../integration/types";
    import {onMount} from "svelte";
    import {getClientInfo, getGameWindow, setHudEditorSelected, setTyping} from "../../integration/rest";
    import {listen} from "../../integration/ws";
    import type {ScaleFactorChangeEvent} from "../../integration/events";
    import HudEditor from "../../shared/hud-editor/HudEditor.svelte";

    const tabs = [
        {title: "ClickGUI", content: ClickGui},
        {title: "HUD Editor", content: HudEditor},
        {title: "Settings", content: GlobalSettings},
    ];

    let activeTab = $state(0);
    let minecraftScaleFactor = $state(2);
    let clickGuiScaleFactor = $state(1);

    $effect(() => {
        activeTab = viewToTab($clickGuiThemeSession.view);
    });

    $effect(() => {
        clickGuiThemeSession.setView(tabToView(activeTab));
    });

    $effect(() => {
        $scaleFactor = minecraftScaleFactor * clickGuiScaleFactor;
    });

    $effect(() => {
        if ($clickGuiThemeSession.settings) {
            applyValues($clickGuiThemeSession.settings);
        }
    });

    function applyValues(configurable: ConfigurableSetting) {
        const scaleValue = configurable.value.find(v => v.name === "Scale");
        const snappingValue = configurable.value.find(v => v.name === "Snapping") as TogglableSetting | undefined;

        if (scaleValue) {
            clickGuiScaleFactor = scaleValue.value as number;
        }

        if (snappingValue) {
            $snappingEnabled = snappingValue.value.find(v => v.name === "Enabled")?.value as boolean ?? true;
            $gridSize = snappingValue.value.find(v => v.name === "GridSize")?.value as number ?? 10;
        }
    }

    onMount(async () => {
        await setHudEditorSelected(false);

        $os = (await getClientInfo()).os;

        const gameWindow = await getGameWindow();
        minecraftScaleFactor = gameWindow.scaleFactor;

        await setTyping(false);
    });

    listen("scaleFactorChange", (e: ScaleFactorChangeEvent) => {
        minecraftScaleFactor = e.scaleFactor;
    });

    function viewToTab(view: ClickGuiView): number {
        if (view === "hud-editor") return 1;
        return view === "settings" ? 2 : 0;
    }

    function tabToView(tab: number): ClickGuiView {
        if (tab === 1) return "hud-editor";
        return tab === 2 ? "settings" : "clickgui";
    }
</script>

<div class="tabbed-clickgui">
    <Tabs {tabs} bind:activeTab/>
</div>

<style lang="scss">
  .tabbed-clickgui {
    background-color: transparent;
    overflow: hidden;
    position: absolute;
    inset: 0;
  }
</style>
