<script lang="ts">
    import {onMount} from "svelte";
    import type {ConfigurableSetting as ConfigurableSettingData} from "../../../integration/types";
    import HudThemeSelector from "../../hud/theme/HudThemeSelector.svelte";
    import ConfigurableSetting from "../setting/ConfigurableSetting.svelte";
    import WindowPanel from "./WindowPanel.svelte";
    import {
        clickGuiThemeSession,
        type ClickGuiVisualTheme,
    } from "../clickgui_store";
    import {createLatestValueSaveQueue} from "../theme/latestValueSaveQueue";
    import {productionGlobalSettingsDataSource} from "../themes/modern/model/clickGuiDataSource";
    import ScaledClickGuiContent from "../ScaledClickGuiContent.svelte";

    let globalSettings = $state<ConfigurableSettingData | null>(null);
    let globalLoading = $state(true);
    let globalSaving = $state(false);
    let globalLoadError = $state<string | null>(null);
    let globalSaveError = $state<string | null>(null);
    const visualThemes: {
        name: ClickGuiVisualTheme;
        description: string;
    }[] = [
        {
            name: "Classic",
            description: "The original LiquidBounce ClickGUI.",
        },
        {
            name: "Modern",
            description: "Graphite Glass with balanced floating panels.",
        },
    ];

    const globalSettingsSaveQueue = createLatestValueSaveQueue<ConfigurableSettingData>({
        save: settings => productionGlobalSettingsDataSource.setGlobalSettings(settings),
        reload: () => productionGlobalSettingsDataSource.getGlobalSettings(),
        onConfirmed: settings => {
            globalSettings = settings;
        },
        onStateChange: state => {
            globalSaving = state.saving;
            globalSaveError = state.error
                ? describeError(state.error, "Unable to save global settings.")
                : null;
        },
    });

    async function fetchGlobalSettings(): Promise<void> {
        globalLoading = true;
        globalLoadError = null;

        try {
            globalSettings = await productionGlobalSettingsDataSource.getGlobalSettings();
        } catch (error) {
            globalLoadError = describeError(error, "Unable to load global settings.");
        } finally {
            globalLoading = false;
        }
    }

    function updateGlobalSettings(): void {
        if (!globalSettings) return;

        globalSaveError = null;
        globalSettingsSaveQueue.enqueue(
            structuredClone($state.snapshot(globalSettings)),
        );
    }

    onMount(() => {
        fetchGlobalSettings();
    });

    function describeError(error: unknown, fallback: string): string {
        if (!(error instanceof Error) || !error.message.trim()) {
            return fallback;
        }

        return `${fallback} ${error.message}`;
    }
</script>

<ScaledClickGuiContent>
  <WindowPanel title="Global Settings" icon="client">
    <section class="appearance-setting" aria-labelledby="clickgui-theme-title">
        <div class="appearance-copy">
            <span id="clickgui-theme-title" class="appearance-title">ClickGUI Theme</span>
            <span class="appearance-description">
                Change the complete ClickGUI presentation. Your module settings stay intact.
            </span>
        </div>

        <div class="theme-options" role="radiogroup" aria-labelledby="clickgui-theme-title">
            {#each visualThemes as visualTheme (visualTheme.name)}
                <button
                        type="button"
                        role="radio"
                        aria-checked={$clickGuiThemeSession.theme === visualTheme.name}
                        class:active={$clickGuiThemeSession.theme === visualTheme.name}
                        disabled={$clickGuiThemeSession.saving}
                        onclick={() => clickGuiThemeSession.selectTheme(visualTheme.name)}
                >
                    <span>{visualTheme.name}</span>
                    <small>{visualTheme.description}</small>
                </button>
            {/each}
        </div>

        {#if $clickGuiThemeSession.saveError}
            <div class="theme-save-error" role="alert">
                <span>{$clickGuiThemeSession.saveError}</span>
                {#if $clickGuiThemeSession.failedTheme}
                    <button
                            type="button"
                            disabled={$clickGuiThemeSession.saving}
                            onclick={() => clickGuiThemeSession.retryThemeSave()}
                    >
                        Retry {$clickGuiThemeSession.failedTheme}
                    </button>
                {/if}
            </div>
        {:else if $clickGuiThemeSession.saving}
            <span class="theme-save-status" role="status">Saving theme…</span>
        {/if}
    </section>

    <HudThemeSelector variant="compact" />

    {#if globalLoading && !globalSettings}
        <div class="global-status" role="status">Loading global settings…</div>
    {:else if globalLoadError && !globalSettings}
        <div class="global-status global-error" role="alert">
            <span>{globalLoadError}</span>
            <button type="button" onclick={() => fetchGlobalSettings()}>Try again</button>
        </div>
    {/if}

    <div class="settings-grid">
        {#if globalSettings}
            {#each globalSettings.value as _, i (globalSettings.value[i].name)}
                {#if globalSettings.value[i].valueType === "CONFIGURABLE" ||
                globalSettings.value[i].valueType === "TOGGLEABLE"}
                    <div class="setting-item">
                        <ConfigurableSetting
                                path="clickgui.global"
                                bind:setting={globalSettings.value[i]}
                                hideExpandControl={true}
                                on:change={updateGlobalSettings}
                        />
                    </div>
                {/if}
            {/each}
        {/if}
    </div>

    {#if globalSaveError}
        <div class="global-status global-error" role="alert">
            <span>{globalSaveError}</span>
            <button type="button" onclick={() => globalSettingsSaveQueue.retry()}>
                Retry save
            </button>
        </div>
    {:else if globalSaving}
        <div class="global-status" role="status">Saving global settings…</div>
    {/if}
  </WindowPanel>
</ScaledClickGuiContent>

<style lang="scss">

  .appearance-setting {
    display: grid;
    grid-template-columns: minmax(190px, 0.8fr) minmax(300px, 1.2fr);
    align-items: center;
    gap: 12px 20px;
    margin: 2px 0 18px;
    padding: 14px;
    border: 1px solid var(--clickgui-global-settings-divider-color);
    border-radius: 5px;
    background: var(--clickgui-window-header-background-color);
  }

  .appearance-copy {
    display: grid;
    gap: 4px;
  }

  .appearance-title {
    color: var(--clickgui-text-color);
    font-size: 14px;
    font-weight: 600;
  }

  .appearance-description,
  .theme-options small,
  .theme-save-status {
    color: var(--clickgui-text-dimmed-color);
    font-size: 11px;
    line-height: 1.35;
  }

  .theme-options {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 7px;
  }

  .theme-options button {
    display: grid;
    gap: 2px;
    min-width: 0;
    padding: 8px 10px;
    color: var(--clickgui-text-color);
    background: var(--clickgui-tabs-background-color);
    border: 1px solid transparent;
    border-radius: 5px;
    text-align: left;
    cursor: pointer;
    transition: ease background-color 0.2s, ease border-color 0.2s;

    &:hover:not(:disabled) {
      background: var(--clickgui-tab-hover-background-color);
    }

    &.active {
      background: var(--clickgui-tab-active-background-color);
      border-color: var(--clickgui-tab-active-border-color);
    }

    &:focus-visible {
      outline: 2px solid var(--accent-color);
      outline-offset: 2px;
    }

    &:disabled {
      cursor: wait;
      opacity: 0.7;
    }
  }

  .theme-options button > span {
    font-size: 12px;
    font-weight: 600;
  }

  .theme-options small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .theme-save-error,
  .theme-save-status {
    grid-column: 1 / -1;
  }

  .theme-save-error {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    color: #ff9e9e;
    font-size: 11px;
  }

  .theme-save-error button {
    flex: 0 0 auto;
    padding: 5px 9px;
    color: var(--clickgui-text-color);
    background: var(--clickgui-tabs-background-color);
    border: 1px solid var(--clickgui-tab-active-border-color);
    border-radius: 4px;
    cursor: pointer;
  }

  .global-status {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    margin: 0 0 14px;
    padding: 9px 12px;
    color: var(--clickgui-text-dimmed-color);
    background: var(--clickgui-window-header-background-color);
    border: 1px solid var(--clickgui-global-settings-divider-color);
    border-radius: 5px;
    font-size: 11px;
  }

  .global-status.global-error {
    color: #ff9e9e;
  }

  .global-status button {
    padding: 5px 9px;
    color: var(--clickgui-text-color);
    background: var(--clickgui-tabs-background-color);
    border: 1px solid var(--clickgui-tab-active-border-color);
    border-radius: 4px;
    cursor: pointer;
  }

  .settings-grid {
    column-count: 2;
    column-gap: 25px;
    column-rule: 1px solid var(--clickgui-global-settings-divider-color);
    column-fill: balance;
    overflow: visible;
  }

  @media (max-width: 900px) {
    .appearance-setting {
      grid-template-columns: 1fr;
    }

    .theme-save-error,
    .theme-save-status {
      grid-column: auto;
    }

    .settings-grid {
      column-count: 1;
    }
  }

  .setting-item {
    break-inside: avoid;
    display: inline-block;
    width: 100%;
    margin-bottom: 15px;
  }
</style>
