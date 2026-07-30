<script lang="ts">
    import {onMount} from "svelte";
    import type {Module} from "../../../../integration/types";
    import {groupByCategory} from "../../../../integration/util";
    import {removeItem} from "../../../../integration/persistent_storage";
    import {
        highlightModuleName,
        maxPanelZIndex,
        scaleFactor,
    } from "../../clickgui_store";
    import ModernPanel from "./ModernPanel.svelte";
    import {productionClickGuiDataSource} from "./model/clickGuiDataSource";
    import {
        arrangeModernPanels,
        findModernPanelStateKeys,
        type ModernPanelState,
    } from "./model/modernPanelState";
    import {logicalViewportDimension} from "./modernShellState";

    interface Props {
        resetLayoutVersion: number;
    }

    type CategoryModules = [category: string, modules: Module[]];

    let {resetLayoutVersion}: Props = $props();

    let categoryModules = $state<CategoryModules[]>([]);
    let initialPanelStates = $state<ModernPanelState[]>([]);
    let panelResetVersion = $state(0);
    let loading = $state(true);
    let loadError = $state<string | null>(null);
    let loadRequest = 0;
    let observedResetLayoutVersion: number | null = null;

    $effect(() => {
        const requestedVersion = resetLayoutVersion;
        if (observedResetLayoutVersion === null) {
            observedResetLayoutVersion = requestedVersion;
            return;
        }

        if (requestedVersion === observedResetLayoutVersion) {
            return;
        }

        observedResetLayoutVersion = requestedVersion;
        void resetModernPanelLayout();
    });

    onMount(() => {
        void loadModules();

        return () => {
            loadRequest += 1;
        };
    });

    async function loadModules(): Promise<void> {
        const request = ++loadRequest;
        loading = true;
        loadError = null;

        try {
            const modules = await productionClickGuiDataSource.getModules();
            if (request !== loadRequest) {
                return;
            }

            categoryModules = Object.entries(groupByCategory(modules));
            arrangeCurrentCategories();
        } catch (error) {
            if (request !== loadRequest) {
                return;
            }

            loadError = errorMessage(error);
        } finally {
            if (request === loadRequest) {
                loading = false;
            }
        }
    }

    async function resetModernPanelLayout(): Promise<void> {
        const keys = findModernPanelStateKeys(localStorageKeys());
        await Promise.all(keys.map(key => removeItem(key)));

        $highlightModuleName = null;
        $maxPanelZIndex = 0;
        arrangeCurrentCategories();
        panelResetVersion += 1;
    }

    function arrangeCurrentCategories(): void {
        initialPanelStates = arrangeModernPanels(
            categoryModules,
            logicalViewportWidth(),
        );
    }

    function localStorageKeys(): string[] {
        const keys: string[] = [];

        for (let index = 0; index < localStorage.length; index += 1) {
            const key = localStorage.key(index);
            if (key) {
                keys.push(key);
            }
        }

        return keys;
    }

    function logicalViewportWidth(): number {
        return logicalViewportDimension(window.innerWidth, $scaleFactor);
    }

    function errorMessage(error: unknown): string {
        return error instanceof Error
            ? error.message
            : "The module list is unavailable.";
    }

</script>

<div class="modern-clickgui" aria-busy={loading}>
    {#if loading}
        <div class="status" role="status" aria-live="polite">
            <span class="status-indicator" aria-hidden="true"></span>
            <span>Loading modules…</span>
        </div>
    {:else if loadError}
        <div class="status error" role="alert">
            <strong>Modules could not be loaded</strong>
            <span>{loadError}</span>
            <button type="button" onclick={loadModules}>Retry</button>
        </div>
    {:else if categoryModules.length === 0}
        <div class="status" role="status">
            <strong>No modules available</strong>
            <span>The client did not return any ClickGUI modules.</span>
        </div>
    {:else}
        {#each categoryModules as [category, modules], panelIndex (category)}
            <ModernPanel
                    {category}
                    {modules}
                    initialState={initialPanelStates[panelIndex]}
                    resetVersion={panelResetVersion}
            />
        {/each}
    {/if}
</div>

<style lang="scss">
  .modern-clickgui {
    position: absolute;
    inset: 0;
    overflow: hidden;
    color: var(--modern-text-primary, #f3f5f7);
  }

  .modern-clickgui:focus-visible {
    outline: none;
  }

  .status {
    position: absolute;
    top: 112px;
    left: 50%;
    width: min(360px, calc(100% - 40px));
    min-width: 0;
    display: grid;
    justify-items: center;
    gap: 8px;
    padding: 18px 22px;
    transform: translateX(-50%);
    color: var(--modern-text-secondary, #aeb5bf);
    background: var(--modern-surface-panel, rgba(18, 21, 27, 0.97));
    border: 1px solid var(--modern-border, rgba(255, 255, 255, 0.1));
    border-radius: 12px;
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.24);
    text-align: center;
  }

  .status strong {
    color: var(--modern-text-primary, #f3f5f7);
    font-size: 13px;
  }

  .status span {
    max-width: 360px;
    font-size: 12px;
    line-height: 1.45;
  }

  .status-indicator {
    width: 18px;
    height: 18px;
    border: 2px solid rgba(255, 255, 255, 0.12);
    border-top-color: color-mix(in srgb, var(--accent-color) 75%, white);
    border-radius: 50%;
    animation: modern-panel-loading 700ms linear infinite;
  }

  .status button {
    min-height: 30px;
    margin-top: 4px;
    padding: 0 12px;
    color: var(--modern-text-primary, #f3f5f7);
    background: rgba(255, 255, 255, 0.055);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 7px;
    cursor: pointer;
    font: inherit;
    font-size: 12px;
    font-weight: 600;
  }

  .status button:hover {
    background: rgba(255, 255, 255, 0.09);
  }

  .status button:focus-visible {
    outline: 2px solid var(--modern-focus-ring, color-mix(in srgb, var(--accent-color) 80%, white));
    outline-offset: 2px;
  }

  @keyframes modern-panel-loading {
    to {
      transform: rotate(360deg);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .status-indicator {
      animation: none;
      border-color: color-mix(in srgb, var(--accent-color) 65%, white);
    }
  }
</style>
