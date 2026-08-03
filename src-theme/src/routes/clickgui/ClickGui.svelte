<script lang="ts">
    import type {GroupedModules, Module} from "../../integration/types";
    import Panel from "./Panel.svelte";
    import Search from "./Search.svelte";
    import Description from "./Description.svelte";
    import {fade} from "svelte/transition";
    import {onMount} from "svelte";
    import {getModules} from "../../integration/rest";
    import {groupByCategory} from "../../integration/util";
    import {gridSize, shiftHeld, showGrid} from "./clickgui_store";
    import ScaledClickGuiContent from "./ScaledClickGuiContent.svelte";

    const CLICKGUI_FADE_MS = 120;

    let categories = $state<GroupedModules>({});
    let modules = $state<Module[]>([]);

    onMount(async () => {
        modules = await getModules();
        categories = groupByCategory(modules);
    });

    function handleShiftKeyDown(event: KeyboardEvent) {
        if (event.key === "Shift" || event.shiftKey) {
            shiftHeld.set(true);
        }
    }

    function handleShiftKeyUp(event: KeyboardEvent) {
        shiftHeld.set(event.getModifierState("Shift"));
    }

    function handleWindowBlur() {
        shiftHeld.set(false);
    }
</script>

<svelte:window
        on:keydown={handleShiftKeyDown}
        on:keyup={handleShiftKeyUp}
        on:blur={handleWindowBlur}
/>

<ScaledClickGuiContent>
    <div
            class="clickgui"
            class:grid={$showGrid}
            style="background-size: {$gridSize}px {$gridSize}px;"
            transition:fade|global={{duration: CLICKGUI_FADE_MS}}
    >
        <Description/>
        <Search modules={structuredClone($state.snapshot(modules))}/>

        {#each Object.entries(categories) as [category, modules], panelIndex (category)}
            <Panel {category} {modules} {panelIndex}/>
        {/each}
    </div>
</ScaledClickGuiContent>

<style lang="scss">
  .clickgui {
    position: absolute;
    inset: 0;

    &.grid {
      background-image: linear-gradient(to right, var(--clickgui-grid-color) 1px, transparent 1px),
      linear-gradient(to bottom, var(--clickgui-grid-color) 1px, transparent 1px);
    }
  }
</style>
