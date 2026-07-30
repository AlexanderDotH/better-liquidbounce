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
    let renderScaleFactor = $derived(normalizeClickGuiScaleFactor($scaleFactor));

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
        style="
          transform: scale({renderScaleFactor / 2});
          width: {2 / renderScaleFactor * 100}vw;
          height: {2 / renderScaleFactor * 100}vh;
          background-size: {$gridSize}px {$gridSize}px;
          --modern-logical-viewport-height: {2 / renderScaleFactor * 100}vh;
        "
>
    <ModernCommandBar
            view={$session.view}
            busy={$session.saving}
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
    --modern-motion-duration: 140ms;
    --modern-motion-easing: ease;

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
    background:
      linear-gradient(145deg, rgba(18, 22, 28, 0.88), rgba(7, 9, 12, 0.96) 52%, rgba(10, 12, 16, 0.94)),
      rgba(6, 8, 11, 0.94);
    transform-origin: top left;
    isolation: isolate;
  }

  .modern-clickgui::before {
    position: absolute;
    inset: 0;
    z-index: -1;
    content: "";
    background:
      linear-gradient(rgba(255, 255, 255, 0.012) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.012) 1px, transparent 1px);
    background-size: 64px 64px;
    opacity: 0.55;
    pointer-events: none;
  }

  .modern-clickgui.grid {
    background-image:
      linear-gradient(to right, color-mix(in srgb, var(--clickgui-grid-color) 52%, transparent) 1px, transparent 1px),
      linear-gradient(to bottom, color-mix(in srgb, var(--clickgui-grid-color) 52%, transparent) 1px, transparent 1px),
      linear-gradient(145deg, rgba(18, 22, 28, 0.88), rgba(7, 9, 12, 0.96) 52%, rgba(10, 12, 16, 0.94));
  }

  .view-stage {
    position: absolute;
    inset: 0;
  }

  @media (prefers-reduced-motion: reduce) {
    .modern-clickgui {
      --modern-motion-duration: 0ms;
      scroll-behavior: auto;
    }
  }
</style>
