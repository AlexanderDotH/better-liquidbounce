<script lang="ts">
    import {onMount} from "svelte";
    import type {ConfigurableSetting} from "../../../../integration/types";
    import type {
        ClickGuiValueChangeEvent,
        ModuleToggleEvent,
    } from "../../../../integration/events";
    import {listen} from "../../../../integration/ws";
    import {setItem} from "../../../../integration/persistent_storage";
    import {
        convertToSpacedString,
        spaceSeperatedNames,
    } from "../../../../theme/theme_config";
    import {
        description as descriptionStore,
        highlightModuleName,
        scaleFactor,
    } from "../../clickgui_store";
    import GenericSetting from "../../setting/common/GenericSetting.svelte";
    import type {ClickGuiDataSource} from "./model/clickGuiDataSource";
    import {productionClickGuiDataSource} from "./model/clickGuiDataSource";
    import {modernModuleExpansionKey} from "./model/modernInteractionState";
    import {
        MODERN_MODULE_STAGGER_LIMIT,
        MODERN_SETTING_STAGGER_LIMIT,
        motionStaggerIndex,
    } from "./model/modernMotion";
    import {createLatestValueSaveQueue} from "../../theme/latestValueSaveQueue";
    import {
        logicalViewportDimension,
        motionAwareScrollBehavior,
    } from "./modernShellState";

    interface Props {
        name: string;
        enabled: boolean;
        description: string;
        aliases: string[];
        moduleIndex: number;
        revealed: boolean;
        dataSource?: ClickGuiDataSource;
    }

    let {
        name,
        enabled,
        description,
        aliases,
        moduleIndex,
        revealed,
        dataSource = productionClickGuiDataSource,
    }: Props = $props();

    let moduleButton = $state<HTMLButtonElement>();
    let configurable = $state<ConfigurableSetting | null>(null);
    let liveEnabled = $state(false);
    let expanded = $state(false);
    let loadingSettings = $state(true);
    let togglePending = $state(false);
    let savePending = $state(false);
    let interactionError = $state<string | null>(null);
    let settingsSaveError = $state<string | null>(null);

    let settingsPath = $derived(modernModuleExpansionKey(name));
    let hasSettings = $derived(
        configurable?.value.some(setting =>
            setting.name !== "Bind" && setting.name !== "Hidden"
        ) ?? false,
    );

    const settingsSaveQueue = createLatestValueSaveQueue<ConfigurableSetting>({
        save: settings => dataSource.setModuleSettings(name, settings),
        reload: () => dataSource.getModuleSettings(name),
        onConfirmed: settings => {
            configurable = settings;
        },
        onStateChange: state => {
            savePending = state.saving;
            settingsSaveError = state.error
                ? describeError(state.error, "Settings could not be saved.")
                : null;
        },
    });

    $effect(() => {
        liveEnabled = enabled;
    });

    $effect(() => {
        if ($highlightModuleName !== name || !moduleButton) {
            return;
        }

        const timeout = window.setTimeout(() => {
            moduleButton?.scrollIntoView({
                behavior: motionAwareScrollBehavior(prefersReducedMotion()),
                block: "center",
            });
            moduleButton?.focus({preventScroll: true});
        }, 90);

        return () => window.clearTimeout(timeout);
    });

    listen("moduleToggle", (event: ModuleToggleEvent) => {
        if (event.moduleName === name) {
            liveEnabled = event.enabled;
        }
    });

    listen("clickGuiValueChange", (event: ClickGuiValueChangeEvent) => {
        if (event.configurable.name !== name) {
            return;
        }

        if (settingsSaveQueue.isSaving() || settingsSaveQueue.hasPending()) {
            return;
        }

        configurable = structuredClone(event.configurable);
        loadingSettings = false;
    });

    onMount(() => {
        expanded = localStorage.getItem(settingsPath) === "true";
        void refreshSettings();
    });

    async function refreshSettings(): Promise<void> {
        loadingSettings = true;

        try {
            configurable = await dataSource.getModuleSettings(name);
            if (!hasConfigurableSettings(configurable)) {
                expanded = false;
            }
            interactionError = null;
        } catch (error) {
            interactionError = describeError(error, "Settings could not be loaded.");
        } finally {
            loadingSettings = false;
        }
    }

    async function toggleModule(): Promise<void> {
        if (togglePending) {
            return;
        }

        const previousEnabled = liveEnabled;
        liveEnabled = !previousEnabled;
        togglePending = true;
        interactionError = null;

        try {
            await dataSource.setModuleEnabled(name, liveEnabled);
        } catch (error) {
            liveEnabled = previousEnabled;
            interactionError = describeError(error, "Module state could not be changed.");
        } finally {
            togglePending = false;
        }
    }

    function toggleExpanded(): void {
        if (!hasSettings) {
            return;
        }

        expanded = !expanded;
        void persistExpansion();
    }

    async function persistExpansion(): Promise<void> {
        try {
            await setItem(settingsPath, expanded.toString());
        } catch (error) {
            interactionError = describeError(error, "Expansion state could not be saved.");
        }
    }

    function scheduleSettingsSave(): void {
        if (!configurable) {
            return;
        }

        interactionError = null;
        settingsSaveError = null;
        settingsSaveQueue.enqueue(
            structuredClone($state.snapshot(configurable)),
        );
    }

    function handleModuleKeydown(event: KeyboardEvent): void {
        if (!hasSettings) {
            return;
        }

        if (event.key === "ArrowRight" && !expanded) {
            event.preventDefault();
            toggleExpanded();
        }

        if (event.key === "ArrowLeft" && expanded) {
            event.preventDefault();
            toggleExpanded();
        }

        if (event.key === "ContextMenu") {
            event.preventDefault();
            toggleExpanded();
        }
    }

    function showDescription(): void {
        if (!moduleButton) {
            return;
        }

        const bounds = moduleButton.getBoundingClientRect();
        const aliasText = aliases.length > 0
            ? ` (aka ${aliases.map(displayName).join(", ")})`
            : "";
        const moduleDescription = `${description}${aliasText}`;

        if (window.innerWidth - bounds.right > 300) {
            descriptionStore.set({
                x: logicalViewportDimension(bounds.right, $scaleFactor),
                y: logicalViewportDimension(
                    bounds.top + bounds.height / 2,
                    $scaleFactor,
                ),
                anchor: "right",
                description: moduleDescription,
            });
            return;
        }

        descriptionStore.set({
            x: logicalViewportDimension(bounds.left, $scaleFactor),
            y: logicalViewportDimension(
                bounds.top + bounds.height / 2,
                $scaleFactor,
            ),
            anchor: "left",
            description: moduleDescription,
        });
    }

    function displayName(value: string): string {
        return $spaceSeperatedNames ? convertToSpacedString(value) : value;
    }

    function hasConfigurableSettings(settings: ConfigurableSetting): boolean {
        return settings.value.some(setting =>
            setting.name !== "Bind" && setting.name !== "Hidden"
        );
    }

    function describeError(error: unknown, fallback: string): string {
        if (!(error instanceof Error) || !error.message.trim()) {
            return fallback;
        }

        return `${fallback} ${error.message}`;
    }

    function prefersReducedMotion(): boolean {
        return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    }
</script>

<article
        class="module"
        class:enabled={liveEnabled}
        class:expanded
        class:highlighted={$highlightModuleName === name}
        class:pending={togglePending}
        class:revealed
        style:--modern-module-enter-index={motionStaggerIndex(moduleIndex, MODERN_MODULE_STAGGER_LIMIT)}
>
    <button
            class="module-row"
            type="button"
            aria-pressed={liveEnabled}
            aria-busy={togglePending}
            aria-expanded={hasSettings ? expanded : undefined}
            aria-controls={hasSettings ? `modern-module-settings-${name}` : undefined}
            title={hasSettings ? "Left-click to toggle · Right-click to open settings" : "Click to toggle"}
            bind:this={moduleButton}
            onclick={toggleModule}
            oncontextmenu={(event) => {
                event.preventDefault();
                toggleExpanded();
            }}
            onkeydown={handleModuleKeydown}
            onmouseenter={showDescription}
            onmouseleave={() => descriptionStore.set(null)}
            onfocus={showDescription}
            onblur={() => descriptionStore.set(null)}
    >
        <span class="state-dot" aria-hidden="true"></span>
        <span class="module-name">{displayName(name)}</span>
        <span class="module-state">{liveEnabled ? "On" : "Off"}</span>

        {#if hasSettings}
            <svg class="expand-mark" class:expanded aria-hidden="true" viewBox="0 0 16 16">
                <path d="m5.7 3.3 4.7 4.7-4.7 4.7 1.2 1.2L12.8 8 6.9 2.1 5.7 3.3Z"/>
            </svg>
        {/if}
    </button>

    {#if expanded}
        <div
                id="modern-module-settings-{name}"
                class="module-settings"
                role="region"
                aria-label="{displayName(name)} settings"
                aria-busy={loadingSettings || savePending}
        >
            {#if loadingSettings && !configurable}
                <div class="settings-status" role="status">
                    <span class="spinner" aria-hidden="true"></span>
                    Loading settings
                </div>
            {:else if configurable}
                {#each configurable.value as _, index (configurable.value[index].name)}
                    <div
                            class="modern-setting-shell"
                            style:--modern-setting-enter-index={motionStaggerIndex(index, MODERN_SETTING_STAGGER_LIMIT)}
                    >
                        <GenericSetting
                                path={settingsPath}
                                bind:setting={configurable.value[index]}
                                on:change={scheduleSettingsSave}
                        />
                    </div>
                {/each}
            {/if}

            {#if interactionError || settingsSaveError}
                <div class="settings-error" role="alert">
                    <span>{settingsSaveError ?? interactionError}</span>
                    {#if !configurable}
                        <button type="button" onclick={refreshSettings}>Retry</button>
                    {:else if settingsSaveError}
                        <button type="button" onclick={() => settingsSaveQueue.retry()}>
                            Retry save
                        </button>
                    {/if}
                </div>
            {/if}
        </div>
    {/if}
</article>

<style lang="scss">
  .module {
    position: relative;
    color: var(--clickgui-text-dimmed-color);
    border-bottom: 1px solid rgba(255, 255, 255, 0.055);
  }

  .module.revealed {
    animation:
      modern-module-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
    animation-delay:
      calc(
        var(--modern-module-enter-index, 0)
        * var(--modern-motion-stagger, 24ms)
      );
  }

  .module:last-child {
    border-bottom: 0;
  }

  .module-row {
    width: 100%;
    min-height: 42px;
    display: grid;
    grid-template-columns: 7px minmax(0, 1fr) auto auto;
    align-items: center;
    gap: 9px;
    padding: 0 12px;
    color: inherit;
    background: transparent;
    border: 0;
    cursor: pointer;
    font-family: inherit;
    text-align: left;
    transition:
      color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      background-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease);
  }

  .module-row:hover {
    color: var(--clickgui-text-color);
    background: rgba(255, 255, 255, 0.045);
  }

  .module-row:focus-visible {
    position: relative;
    z-index: 1;
    outline: 2px solid var(--modern-focus-ring, color-mix(in srgb, var(--accent-color) 78%, white));
    outline-offset: -2px;
  }

  .state-dot {
    width: 6px;
    height: 6px;
    background: #58606b;
    border-radius: 50%;
    transition:
      background-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      transform
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .module-name {
    min-width: 0;
    overflow: hidden;
    color: #bdc3cc;
    font-size: 12px;
    font-weight: 560;
    letter-spacing: -0.005em;
    text-overflow: ellipsis;
    white-space: nowrap;
    transition: color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease);
  }

  .module-state {
    color: var(--modern-text-muted, #8d96a3);
    font-size: 9px;
    font-weight: 650;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    transition:
      color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      opacity
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .expand-mark {
    width: 12px;
    height: 12px;
    fill: currentColor;
    opacity: 0.52;
    transition:
      opacity var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      transform var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease);
  }

  .expand-mark.expanded {
    opacity: 0.9;
    transform: rotate(90deg);
  }

  .module.enabled .state-dot {
    background: color-mix(in srgb, var(--accent-color) 82%, white);
    animation:
      modern-state-confirm
      calc(var(--modern-motion-duration, 140ms) * 2)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1));
  }

  .module.enabled .module-name {
    color: color-mix(in srgb, var(--accent-color) 78%, white);
  }

  .module.enabled .module-state {
    color: color-mix(in srgb, var(--accent-color) 68%, white);
  }

  .module.highlighted::after {
    position: absolute;
    inset: 2px;
    content: "";
    border: 1px solid color-mix(in srgb, var(--accent-color) 68%, white);
    border-radius: 7px;
    pointer-events: none;
    animation:
      modern-locate-confirm
      calc(var(--modern-motion-entrance-duration, 260ms) * 2)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
  }

  .module.pending .module-row {
    cursor: progress;
    opacity: 0.72;
  }

  .module-settings {
    padding: 4px 12px 9px;
    color: var(--clickgui-text-color);
    background: rgba(255, 255, 255, 0.022);
    border-left: 2px solid color-mix(in srgb, var(--accent-color) 46%, transparent);
    animation:
      settings-open
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
  }

  // GenericSetting's shared entrance transition can measure zero while its
  // nested control mounts inside this newly expanded region. Keep the fix
  // scoped to Modern so the measured zero frame cannot remain authoritative.
  .modern-setting-shell > :global(div) {
    height: auto !important;
    overflow: visible !important;
    opacity: 1 !important;
  }

  .modern-setting-shell {
    animation:
      modern-setting-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
    animation-delay:
      calc(
        var(--modern-setting-enter-index, 0)
        * var(--modern-motion-stagger, 24ms)
      );
  }

  .settings-status,
  .settings-error {
    min-height: 34px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: #8f98a4;
    font-size: 10px;
  }

  .settings-error {
    justify-content: space-between;
    color: #e5a5a5;
  }

  .settings-error button {
    padding: 4px 7px;
    color: #eceff3;
    background: rgba(255, 255, 255, 0.06);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 6px;
    cursor: pointer;
    font: inherit;
  }

  .settings-error button:focus-visible {
    outline: 2px solid var(--modern-focus-ring, color-mix(in srgb, var(--accent-color) 78%, white));
    outline-offset: 2px;
  }

  .spinner {
    width: 10px;
    height: 10px;
    border: 1px solid rgba(255, 255, 255, 0.18);
    border-top-color: color-mix(in srgb, var(--accent-color) 78%, white);
    border-radius: 50%;
    animation: spin 700ms linear infinite;
  }

  @keyframes settings-open {
    from {
      opacity: 0;
      transform: translateY(-6px);
    }
  }

  @keyframes modern-module-enter {
    from {
      opacity: 0;
      transform: translateX(-6px);
    }
  }

  @keyframes modern-setting-enter {
    from {
      opacity: 0;
      transform: translateX(-4px);
    }
  }

  @keyframes modern-state-confirm {
    0% {
      opacity: 0.45;
      transform: scale(0.55);
    }

    58% {
      transform: scale(1.35);
    }

    100% {
      opacity: 1;
      transform: scale(1);
    }
  }

  @keyframes modern-locate-confirm {
    from {
      opacity: 0;
      transform: scale(0.975);
    }
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .module-row,
    .state-dot,
    .module-name,
    .module-state,
    .expand-mark {
      transition-duration: 0.01ms;
    }

    .module.revealed,
    .module.enabled .state-dot,
    .module.highlighted::after,
    .module-settings,
    .modern-setting-shell {
      animation: none;
    }

    .spinner {
      animation: none;
      border-color: color-mix(in srgb, var(--accent-color) 65%, white);
    }
  }
</style>
