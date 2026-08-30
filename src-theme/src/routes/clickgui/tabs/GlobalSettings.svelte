<script lang="ts">
    import {onMount} from "svelte";
    import type {ConfigurableSetting as ConfigurableSettingData} from "../../../integration/types";
    import HudThemeSelector from "../../../shared/hud-theme/HudThemeSelector.svelte";
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
  @use "./GlobalSettings.styles";
</style>
