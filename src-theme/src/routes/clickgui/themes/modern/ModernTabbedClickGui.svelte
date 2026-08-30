<script lang="ts">
    import {onMount} from "svelte";
    import type {
        ClickGuiThemeSession,
        ClickGuiView,
    } from "../../theme/clickGuiThemeState";
    import type {
        ConfigurableSetting,
        TogglableSetting,
    } from "../../../../integration/types";
    import type {ScaleFactorChangeEvent} from "../../../../integration/events";
    import {
        getClientInfo,
        getGameWindow,
        setTyping,
    } from "../../../../integration/rest";
    import {listen} from "../../../../integration/ws";
    import {
        gridSize,
        os,
        scaleFactor,
        shiftHeld,
        showGrid,
        snappingEnabled,
    } from "../../clickgui_store";
    import Description from "../../Description.svelte";
    import ModernClickGui from "./ModernClickGui.svelte";
    import ModernCommandBar from "./ModernCommandBar.svelte";
    import ModernSearch from "./ModernSearch.svelte";
    import ModernSettings from "./ModernSettings.svelte";
    import HudEditor from "../../../../shared/hud-editor/HudEditor.svelte";
    import {
        MODERN_ANIMATION_STALL_GUARD_MS,
        MODERN_LAYOUT_RESET_DURATION_MS,
        shouldSettleModernAnimation,
    } from "./model/modernMotion";
    import {normalizeClickGuiScaleFactor} from "./modernShellState";

    let {
        session,
        nativeTextInput = false,
    } = $props<{
        session: ClickGuiThemeSession;
        nativeTextInput?: boolean;
    }>();

    let minecraftScaleFactor = $state(2);
    let clickGuiScaleFactor = $state(1);
    let resetLayoutVersion = $state(0);
    let clickGuiElement: HTMLElement;
    let renderScaleFactor = $derived(normalizeClickGuiScaleFactor($scaleFactor));
    let hudEditorActive = $derived($session.view === "hud-editor");
    let viewportTransform = $derived(hudEditorActive ? "none" : `scale(${renderScaleFactor / 2})`);
    let viewportWidth = $derived(hudEditorActive ? "100vw" : `${2 / renderScaleFactor * 100}vw`);
    let viewportHeight = $derived(hudEditorActive ? "100vh" : `${2 / renderScaleFactor * 100}vh`);

    $effect(() => {
        $scaleFactor = minecraftScaleFactor * clickGuiScaleFactor;
    });

    $effect(() => {
        if ($session.settings) {
            applyClickGuiValues($session.settings);
        }
    });

    listen("scaleFactorChange", (event: ScaleFactorChangeEvent) => {
        minecraftScaleFactor = event.scaleFactor;
    });

    onMount(() => {
        void initializeClientState();

        const stallGuardTimeout = window.setTimeout(
            settleStalledAnimations,
            MODERN_ANIMATION_STALL_GUARD_MS,
        );

        return () => window.clearTimeout(stallGuardTimeout);
    });

    async function initializeClientState(): Promise<void> {
        const [clientInfo, gameWindow] = await Promise.all([
            getClientInfo(),
            getGameWindow(),
        ]);

        $os = clientInfo.os;
        minecraftScaleFactor = gameWindow.scaleFactor;
        await setTyping(false);
    }

    function applyClickGuiValues(configurable: ConfigurableSetting): void {
        const scaleValue = configurable.value.find(value => value.name === "Scale");
        const snappingValue = configurable.value.find(
            value => value.name === "Snapping",
        ) as TogglableSetting | undefined;

        if (scaleValue && typeof scaleValue.value === "number") {
            clickGuiScaleFactor = scaleValue.value;
        }

        if (!snappingValue) {
            return;
        }

        const enabled = snappingValue.value.find(value => value.name === "Enabled")?.value;
        const size = snappingValue.value.find(value => value.name === "GridSize")?.value;
        $snappingEnabled = typeof enabled === "boolean" ? enabled : true;
        $gridSize = typeof size === "number" ? size : 10;
    }

    function changeView(view: ClickGuiView): void {
        session.setView(view);
    }

    function resetLayout(): void {
        resetLayoutVersion += 1;
    }

    function settleStalledAnimations(): void {
        for (const animation of clickGuiElement.getAnimations({subtree: true})) {
            const currentTime = typeof animation.currentTime === "number"
                ? animation.currentTime
                : null;
            const endTime = Number(
                animation.effect?.getComputedTiming().endTime
                ?? Number.POSITIVE_INFINITY,
            );

            if (!shouldSettleModernAnimation(
                animation.playState,
                currentTime,
                endTime,
            )) {
                continue;
            }

            try {
                animation.finish();
            } catch {
                animation.cancel();
            }
        }
    }

    function handleShiftKeyDown(event: KeyboardEvent): void {
        if (event.key === "Shift" || event.shiftKey) {
            shiftHeld.set(true);
        }
    }

    function handleShiftKeyUp(event: KeyboardEvent): void {
        shiftHeld.set(event.getModifierState("Shift"));
    }

    function clearShiftState(): void {
        shiftHeld.set(false);
    }
</script>

<svelte:window
        onkeydown={handleShiftKeyDown}
        onkeyup={handleShiftKeyUp}
        onblur={clearShiftState}
/>

<div
        class="modern-clickgui"
        class:grid={$showGrid}
        bind:this={clickGuiElement}
        style="
          transform: {viewportTransform};
          width: {viewportWidth};
          height: {viewportHeight};
          background-size: {$gridSize}px {$gridSize}px;
          --modern-logical-viewport-height: {2 / renderScaleFactor * 100}vh;
          --modern-motion-layout-duration: {MODERN_LAYOUT_RESET_DURATION_MS}ms;
        "
>
    <ModernCommandBar
            view={$session.view}
            busy={$session.saving}
            resetVersion={resetLayoutVersion}
            onViewChange={changeView}
            onResetLayout={resetLayout}
    >
        {#snippet search()}
            <ModernSearch allowNativeInput={nativeTextInput}/>
        {/snippet}
    </ModernCommandBar>

    {#if $session.view === "clickgui"}
        <div
                id="modern-clickgui-view"
                class="view-stage"
                role="tabpanel"
                aria-labelledby="modern-command-tab-clickgui"
        >
            <Description/>
            <ModernClickGui {resetLayoutVersion}/>
        </div>
    {:else if $session.view === "hud-editor"}
        <div
                id="modern-hud-editor-view"
                class="view-stage"
                role="region"
                aria-labelledby="modern-command-hud-editor"
        >
            <HudEditor/>
        </div>
    {:else}
        <ModernSettings {session}/>
    {/if}
</div>

<style lang="scss">
  @use "./ModernTabbedClickGui.styles";
</style>
