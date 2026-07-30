<script lang="ts">
    import ArrayList from "./elements/ArrayList.svelte";
    import TargetHud from "./elements/targethud/TargetHud.svelte";
    import Watermark from "./elements/Watermark.svelte";
    import Notifications from "./elements/notifications/Notifications.svelte";
    import TabGui from "./elements/tabgui/TabGui.svelte";
    import HotBar from "./elements/hotbar/HotBar.svelte";
    import Scoreboard from "./elements/Scoreboard.svelte";
    import {onMount} from "svelte";
    import {getClientInfo, getComponents, getGameWindow, getMetadata} from "../../integration/rest";
    import {listen} from "../../integration/ws";
    import type {HudComponent, Metadata} from "../../integration/types";
    import Taco from "./elements/taco/Taco.svelte";
    import type {ComponentsUpdateEvent, ScaleFactorChangeEvent} from "../../integration/events";
    import Keystrokes from "./elements/keystrokes/Keystrokes.svelte";
    import Effects from "./elements/Effects.svelte";
    import BlockCounter from "./elements/BlockCounter.svelte";
    import Text from "./elements/Text.svelte";
    import DraggableComponent from "./elements/DraggableComponent.svelte";
    import KeyBinds from "./elements/KeyBinds.svelte";
    import GenericPlayerInventory from "./elements/inventory/GenericPlayerInventory.svelte";
    import {os} from "../clickgui/clickgui_store";
    import InventoryStatistics from "./elements/inventory/InventoryStatistics.svelte";
    import ModernWatermark from "./themes/modern/ModernWatermark.svelte";
    import {hudThemeSession} from "./theme/themeSession";
    import type {HudValueChangeEvent} from "../../integration/events";

    let zoom = 100;
    let metadata: Metadata;
    let components: HudComponent[] = [];
    let presentation: "classic" | "modern" = "classic";

    $: presentation = $hudThemeSession.theme === "Modern" ? "modern" : "classic";

    onMount(async () => {
        void hudThemeSession.load();
        $os = (await getClientInfo()).os;

        const gameWindow = await getGameWindow();
        zoom = gameWindow.scaleFactor * 50;

        metadata = await getMetadata();
        components = await getComponents(metadata.id);
    });

    listen("scaleFactorChange", (data: ScaleFactorChangeEvent) => {
        zoom = data.scaleFactor * 50;
    });

    listen("componentsUpdate", (data: ComponentsUpdateEvent) => {
        if (data.id != metadata.id) {
            // reject
            return;
        }

        // force update to re-render
        components = [];
        components = data.components;
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
    {#each components as c}
        {#if c.settings.enabled}
            <DraggableComponent alignment={c.settings.alignment} componentName={c.name}>
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
                    <HotBar/>
                {:else if c.name === "Scoreboard"}
                    <Scoreboard settings={c.settings}/>
                {:else if c.name === "ArmorItems"}
                    <GenericPlayerInventory
                            rowLength={1}
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
                    <Text settings={c.settings} />
                {:else if c.name === "Image"}
                    <img alt="" src="{c.settings.uRL}" style="scale: {c.settings.scale};">
                {:else if c.name === "KeyBinds"}
                    <KeyBinds/>
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
