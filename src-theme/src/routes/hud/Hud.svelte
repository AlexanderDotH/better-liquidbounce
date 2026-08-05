<script lang="ts">
    import ArrayList from "./elements/ArrayList.svelte";
    import TargetHud from "./elements/targethud/TargetHud.svelte";
    import Watermark from "./elements/Watermark.svelte";
    import Notifications from "./elements/notifications/Notifications.svelte";
    import TabGui from "./elements/tabgui/TabGui.svelte";
    import HotBar from "./elements/hotbar/HotBar.svelte";
    import Scoreboard from "./elements/Scoreboard.svelte";
    import {onMount, setContext} from "svelte";
    import {
        getClientInfo,
        getComponents,
        getGameWindow,
        getMetadata,
        getNativeComponents
    } from "../../integration/rest";
    import {listen} from "../../integration/ws";
    import type {HudComponent, Metadata} from "../../integration/types";
    import Taco from "./elements/taco/Taco.svelte";
    import type {ComponentsUpdateEvent, ScaleFactorChangeEvent} from "../../integration/events";
    import Keystrokes from "./elements/keystrokes/Keystrokes.svelte";
    import Effects from "./elements/Effects.svelte";
    import BlockCounter from "./elements/BlockCounter.svelte";
    import Text from "./elements/Text.svelte";
    import Coordinates from "./elements/Coordinates.svelte";
    import DraggableComponent from "./elements/DraggableComponent.svelte";
    import KeyBinds from "./elements/KeyBinds.svelte";
    import ClosedCaptions from "./elements/ClosedCaptions.svelte";
    import GenericPlayerInventory from "./elements/inventory/GenericPlayerInventory.svelte";
    import {os} from "../clickgui/clickgui_store";
    import InventoryStatistics from "./elements/inventory/InventoryStatistics.svelte";
    import ModernWatermark from "./themes/modern/ModernWatermark.svelte";
    import {hudThemeSession} from "./theme/themeSession";
    import type {HudValueChangeEvent} from "../../integration/events";
    import {
        HUD_EDITOR_ELEMENTS_CONTEXT,
        type HudEditorDragState
    } from "../clickgui/tabs/hud_editor/constants";
    import Image from "./elements/Image.svelte";

    export let inEditor = false;
    export let onDragStateChange: ((state: HudEditorDragState) => void) | undefined = undefined;
    export let magneticTargetIds: string[] = [];

    let zoom = 100;
    let metadata: Metadata;
    let nativeComponents: HudComponent[] = [];
    let themeComponents: HudComponent[] = [];
    let presentation: "classic" | "modern" = "classic";

    $: renderedComponents = inEditor ? [...nativeComponents, ...themeComponents] : themeComponents;
    $: presentation = $hudThemeSession.theme === "Modern" ? "modern" : "classic";

    setContext(HUD_EDITOR_ELEMENTS_CONTEXT, new Map<string, HTMLElement>());

    onMount(async () => {
        void hudThemeSession.load();
        $os = (await getClientInfo()).os;

        const gameWindow = await getGameWindow();
        zoom = gameWindow.scaleFactor * 50;

        metadata = await getMetadata();
        [nativeComponents, themeComponents] = await Promise.all([
            inEditor ? getNativeComponents() : Promise.resolve([]),
            getComponents(metadata.id)
        ]);
    });

    listen("scaleFactorChange", (data: ScaleFactorChangeEvent) => {
        zoom = data.scaleFactor * 50;
    });

    listen("componentsUpdate", (event: ComponentsUpdateEvent) => {
        if (inEditor && event.source === "native") {
            nativeComponents = event.components;
        }

        if (event.source === "theme" && event.themeId === metadata?.id) {
            themeComponents = event.components;
        }
    });

    listen("hudValueChange", (event: HudValueChangeEvent) => {
        if (event.configurable.name === "HUD") {
            hudThemeSession.synchronize(event.configurable);
        }
    });
</script>

<div
        class="hud"
        class:hud-theme--classic={$hudThemeSession.theme === "Classic"}
        class:hud-theme--modern={$hudThemeSession.theme === "Modern"}
        style="zoom: {zoom}%"
>
    {#each renderedComponents as c (c.id)}
        {#if c.settings.enabled}
            <DraggableComponent
                    {inEditor}
                    {onDragStateChange}
                    componentId={c.id}
                    componentName={c.name}
                    alignment={c.settings.alignment}
                    zIndex={c.settings.zIndex ?? 0}
                    magneticallyReferenced={magneticTargetIds.includes(c.id)}
                    width={c.width}
                    height={c.height}
            >
                {#if c.name === "Watermark"}
                    {#if $hudThemeSession.theme === "Modern"}
                        <ModernWatermark/>
                    {:else}
                        <Watermark/>
                    {/if}
                {:else if c.name === "ArrayList"}
                    <ArrayList settings={c.settings} variant={presentation}/>
                {:else if c.name === "TabGui"}
                    <TabGui variant={presentation}/>
                {:else if c.name === "Notifications"}
                    <Notifications variant={presentation}/>
                {:else if c.name === "TargetHud"}
                    <TargetHud {presentation}/>
                {:else if c.name === "BlockCounter"}
                    <BlockCounter settings={c.settings}/>
                {:else if c.name === "Hotbar"}
                    <HotBar presentation={presentation}/>
                {:else if c.name === "Scoreboard"}
                    <Scoreboard settings={c.settings}/>
                {:else if c.name === "ArmorItems"}
                    <GenericPlayerInventory
                            rowLength={c.settings.layout === "Horizontal" ? 4 : 1}
                            backgroundColor="transparent"
                            gap="2px"
                            getRenderedStacks={it => Array.from(it.armor).reverse()}
                            variant={presentation}
                            label={presentation === "modern" ? "Armor" : undefined}
                    />
                {:else if c.name === "InventoryStatistics"}
                    <InventoryStatistics
                            settings={c.settings}
                            variant={presentation}
                            label={presentation === "modern" ? "Resources" : undefined}
                    />
                {:else if c.name === "Inventory"}
                    <GenericPlayerInventory
                            rowLength={9}
                            getRenderedStacks={it => it.main.slice(9)}
                            variant={presentation}
                            label={presentation === "modern" ? "Inventory" : undefined}
                    />
                {:else if c.name === "CraftingInventory"}
                    <GenericPlayerInventory
                            rowLength={2}
                            getRenderedStacks={it => it.crafting}
                            variant={presentation}
                            label={presentation === "modern" ? "Crafting" : undefined}
                    />
                {:else if c.name === "EnderChestInventory"}
                    <GenericPlayerInventory
                            rowLength={9}
                            getRenderedStacks={it => it.enderChest}
                            variant={presentation}
                            label={presentation === "modern" ? "Ender Chest" : undefined}
                    />
                {:else if c.name === "Taco"}
                    <Taco/>
                {:else if c.name === "Keystrokes"}
                    <Keystrokes/>
                {:else if c.name === "Effects"}
                    <Effects/>
                {:else if c.name === "Text"}
                    <Text settings={c.settings}/>
                {:else if c.name === "Coordinates"}
                    <Coordinates/>
                {:else if c.name === "Image"}
                    <Image componentId={c.id} settings={c.settings}/>
                {:else if c.name === "KeyBinds"}
                    <KeyBinds/>
                {:else if c.name === "ClosedCaptions"}
                    <ClosedCaptions/>
                {:else if c.width !== undefined && c.height !== undefined}
                    <div></div>
                {/if}
            </DraggableComponent>
        {/if}
    {/each}
</div>

<style lang="scss">
  @use "./themes/modern/modernHud";

  .hud {
    height: 100vh;
    width: 100vw;
    background: transparent;
  }
</style>
