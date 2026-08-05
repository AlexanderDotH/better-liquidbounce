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
    import HudEditor from "../../tabs/hud_editor/HudEditor.svelte";
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
  .modern-clickgui {
    --modern-text-primary: #eef1f5;
    --modern-text-secondary: #aeb5bf;
    --modern-text-muted: #8d96a3;
    --modern-surface-command: rgba(15, 18, 23, 0.96);
    --modern-surface-panel: rgba(17, 20, 26, 0.97);
    --modern-surface-panel-header: rgba(255, 255, 255, 0.035);
    --modern-surface-panel-body: rgba(8, 10, 14, 0.2);
    --modern-surface-raised: rgba(255, 255, 255, 0.045);
    --modern-surface-raised-hover: rgba(255, 255, 255, 0.075);
    --modern-border: rgba(255, 255, 255, 0.1);
    --modern-border-strong: rgba(255, 255, 255, 0.13);
    --modern-divider: rgba(255, 255, 255, 0.075);
    --modern-focus-ring: color-mix(in srgb, var(--accent-color) 80%, white);
    --modern-panel-radius: 12px;
    --modern-motion-fast: 100ms;
    --modern-motion-duration: 140ms;
    --modern-motion-entrance-duration: 260ms;
    --modern-motion-stagger: 24ms;
    --modern-motion-easing: cubic-bezier(0.2, 0.8, 0.2, 1);
    --modern-motion-entrance-easing: cubic-bezier(0.16, 1, 0.3, 1);

    --clickgui-text-color: var(--modern-text-primary);
    --clickgui-text-dimmed-color: #959da8;
    --clickgui-panel-shadow-color: rgba(0, 0, 0, 0.3);
    --clickgui-panel-header-background-color: rgba(19, 23, 29, 0.94);
    --clickgui-panel-header-border-color: rgba(255, 255, 255, 0.09);
    --clickgui-panel-body-background-color: rgba(13, 16, 21, 0.91);
    --clickgui-module-hover-background-color: rgba(255, 255, 255, 0.045);
    --clickgui-module-highlight-color: color-mix(in srgb, var(--accent-color) 72%, white);
    --clickgui-module-enabled-color: color-mix(in srgb, var(--accent-color) 82%, white);
    --clickgui-module-settings-background-color: rgba(255, 255, 255, 0.025);
    --clickgui-module-settings-border-color: color-mix(in srgb, var(--accent-color) 58%, transparent);
    --clickgui-description-background-color: rgba(15, 18, 23, 0.96);
    --clickgui-description-shadow-color: rgba(0, 0, 0, 0.28);
    --clickgui-description-arrow-color: rgba(15, 18, 23, 0.96);
    --clickgui-input-background-color: rgba(255, 255, 255, 0.045);
    --clickgui-input-border-color: color-mix(in srgb, var(--accent-color) 66%, rgba(255, 255, 255, 0.12));
    --clickgui-button-background-color: color-mix(in srgb, var(--accent-color) 76%, #242931);
    --clickgui-button-hover-background-color: color-mix(in srgb, var(--accent-color) 86%, #2d333c);
    --clickgui-setting-group-border-color: color-mix(in srgb, var(--accent-color) 52%, rgba(255, 255, 255, 0.08));
    --clickgui-dropdown-trigger-background-color: rgba(255, 255, 255, 0.055);
    --clickgui-dropdown-background-color: #11151a;
    --clickgui-dropdown-border-color: rgba(255, 255, 255, 0.11);
    --clickgui-dropdown-option-color: #a3aab4;
    --clickgui-dropdown-option-hover-color: #f3f5f7;
    --clickgui-dropdown-option-selected-color: color-mix(in srgb, var(--accent-color) 76%, white);
    --clickgui-selection-chip-background-color: rgba(255, 255, 255, 0.04);
    --clickgui-selection-chip-selected-background-color: color-mix(in srgb, var(--accent-color) 13%, transparent);
    --clickgui-switch-track-color: #3d434c;
    --clickgui-switch-thumb-color: #d9dde2;
    --clickgui-switch-track-active-color: color-mix(in srgb, var(--accent-color) 46%, #282d34);
    --clickgui-switch-thumb-active-color: color-mix(in srgb, var(--accent-color) 82%, white);
    --clickgui-slider-track-color: rgba(255, 255, 255, 0.11);
    --clickgui-slider-handle-color: color-mix(in srgb, var(--accent-color) 78%, white);
    --clickgui-slider-fill-color: color-mix(in srgb, var(--accent-color) 70%, white);
    --clickgui-control-font-size: 11px;
    --clickgui-control-radius: 7px;
    --clickgui-dropdown-radius: 7px;
    --clickgui-control-padding: 6px 9px;
    --clickgui-input-padding: 7px 9px;
    --clickgui-setting-padding: 6px 0;
    --clickgui-slider-setting-padding: 7px 0;
    --clickgui-setting-label-gap: 8px;
    --clickgui-setting-control-gap: 8px;
    --clickgui-setting-expanded-gap: 9px;
    --clickgui-setting-group-padding: 8px;
    --clickgui-setting-group-border-width: 1px;
    --clickgui-control-border-width: 1px;
    --clickgui-dropdown-border-width: 1px;
    --clickgui-control-transition-duration: var(--modern-motion-duration);
    --clickgui-setting-transition-duration: var(--modern-motion-duration);
    --clickgui-switch-transition-duration: var(--modern-motion-duration);

    position: absolute;
    inset: 0;
    overflow: hidden;
    color: var(--clickgui-text-color);
    background: transparent;
    transform-origin: top left;
    isolation: isolate;
  }

  .modern-clickgui::after {
    position: absolute;
    inset: 0;
    content: "";
    pointer-events: none;
  }

  .modern-clickgui::after {
    z-index: -1;
    background-image:
      linear-gradient(to right, color-mix(in srgb, var(--clickgui-grid-color) 52%, transparent) 1px, transparent 1px),
      linear-gradient(to bottom, color-mix(in srgb, var(--clickgui-grid-color) 52%, transparent) 1px, transparent 1px);
    background-size: inherit;
    opacity: 0;
    transition: opacity var(--modern-motion-duration) var(--modern-motion-easing);
  }

  .modern-clickgui.grid::after {
    opacity: 0.72;
    animation:
      modern-grid-engage
      var(--modern-motion-entrance-duration)
      var(--modern-motion-entrance-easing);
  }

  .view-stage {
    position: absolute;
    inset: 0;
    animation:
      modern-view-enter
      var(--modern-motion-entrance-duration)
      var(--modern-motion-entrance-easing)
      backwards;
  }

  @keyframes modern-view-enter {
    from {
      transform: translateY(5px);
    }
  }

  @keyframes modern-grid-engage {
    from {
      transform: scale(1.012);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .modern-clickgui {
      --modern-motion-fast: 0ms;
      --modern-motion-duration: 0ms;
      --modern-motion-entrance-duration: 0ms;
      --modern-motion-stagger: 0ms;
      scroll-behavior: auto;
    }

    .modern-clickgui::after {
      transition-duration: 0ms;
    }

    .modern-clickgui.grid::after,
    .view-stage {
      animation: none;
    }
  }
</style>
