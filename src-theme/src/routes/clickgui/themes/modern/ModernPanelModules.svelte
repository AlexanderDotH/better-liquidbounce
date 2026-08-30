<script lang="ts">
    import {onDestroy, onMount, tick} from "svelte";
    import type {Module as ClickGuiModule} from "../../../../integration/types";
    import type {ModuleToggleEvent} from "../../../../integration/events";
    import {listen} from "../../../../integration/ws";
    import {description as descriptionStore} from "../../clickgui_store";
    import ModernModule from "./ModernModule.svelte";
    import {MODERN_MODULE_STAGGER_LIMIT} from "./model/modernMotion";
    import type {ModernPanelState} from "./model/modernPanelState";

    const MODERN_SCROLL_SETTLE_MS = 160;

    let {modules, panelState, scrollTop, resetVersion, onScrollTop, onScrollSettled} = $props<{
        modules: ClickGuiModule[];
        panelState: ModernPanelState;
        scrollTop: number;
        resetVersion: number;
        onScrollTop: (scrollTop: number) => void;
        onScrollSettled: () => void;
    }>();
    let element: HTMLElement;
    let scrolling = $state(false);
    let scrollSaveTimeout: number | null = null;
    let observedResetVersion: number | null = null;

    listen("moduleToggle", (event: ModuleToggleEvent) => {
        const module = modules.find((candidate: ClickGuiModule) => candidate.name === event.moduleName);
        if (module) module.enabled = event.enabled;
    });

    onMount(async () => {
        await tick();
        element.scrollTop = scrollTop;
    });

    onDestroy(() => {
        if (scrollSaveTimeout !== null) clearTimeout(scrollSaveTimeout);
    });

    $effect(() => {
        const requestedResetVersion = resetVersion;
        if (observedResetVersion === null) {
            observedResetVersion = requestedResetVersion;
            return;
        }
        if (requestedResetVersion === observedResetVersion) return;
        observedResetVersion = requestedResetVersion;
        if (scrollSaveTimeout !== null) clearTimeout(scrollSaveTimeout);
        scrolling = false;
        void tick().then(() => element.scrollTop = scrollTop);
    });

    function handleScroll(): void {
        onScrollTop(element.scrollTop);
        if (!scrolling) {
            scrolling = true;
            descriptionStore.set(null);
        }
        if (scrollSaveTimeout !== null) clearTimeout(scrollSaveTimeout);
        scrollSaveTimeout = window.setTimeout(() => {
            scrollSaveTimeout = null;
            scrolling = false;
            onScrollSettled();
        }, MODERN_SCROLL_SETTLE_MS);
    }
</script>

<div
        class="modules"
        class:expanded={panelState.expanded}
        class:scrolling
        aria-hidden={!panelState.expanded}
        inert={!panelState.expanded}
        bind:this={element}
        onscroll={handleScroll}
>
    {#each modules as {name, enabled, description, aliases, hasSettings}, moduleIndex (name)}
        <ModernModule
                {name}
                {enabled}
                {description}
                {aliases}
                {hasSettings}
                {moduleIndex}
                revealed={panelState.expanded && moduleIndex < MODERN_MODULE_STAGGER_LIMIT}
        />
    {/each}
</div>
