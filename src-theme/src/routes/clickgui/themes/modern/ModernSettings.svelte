<script lang="ts">
    import {onMount} from "svelte";
    import type {ClickGuiThemeSession} from "../../theme/clickGuiThemeState";
    import type {ConfigurableSetting as ConfigurableSettingData} from "../../../../integration/types";
    import ConfigurableSetting from "../../setting/ConfigurableSetting.svelte";
    import {createLatestValueSaveQueue} from "../../theme/latestValueSaveQueue";
    import HudThemeSelector from "../../../../shared/hud-theme/HudThemeSelector.svelte";
    import {productionGlobalSettingsDataSource} from "./model/clickGuiDataSource";
    import {
        MODERN_SETTING_STAGGER_LIMIT,
        motionStaggerIndex,
    } from "./model/modernMotion";
    import {
        describeModernSettingsError,
        MODERN_THEME_OPTIONS,
    } from "./model/modernSettingsModel";

    let {session} = $props<{session: ClickGuiThemeSession}>();

    let globalSettings = $state<ConfigurableSettingData | null>(null);
    let globalLoading = $state(true);
    let globalSaving = $state(false);
    let globalLoadError = $state<string | null>(null);
    let globalSaveError = $state<string | null>(null);

    const globalSettingsSaveQueue = createLatestValueSaveQueue<ConfigurableSettingData>({
        save: settings => productionGlobalSettingsDataSource.setGlobalSettings(settings),
        reload: () => productionGlobalSettingsDataSource.getGlobalSettings(),
        onConfirmed: settings => {
            globalSettings = settings;
        },
        onStateChange: state => {
            globalSaving = state.saving;
            globalSaveError = state.error
                ? describeModernSettingsError(state.error, "Unable to save global settings.")
                : null;
        },
    });

    onMount(() => {
        void fetchGlobalSettings();
    });

    async function fetchGlobalSettings(): Promise<void> {
        globalLoading = true;
        globalLoadError = null;

        try {
            globalSettings = await productionGlobalSettingsDataSource.getGlobalSettings();
        } catch (error) {
            globalLoadError = describeModernSettingsError(error, "Unable to load global settings.");
        } finally {
            globalLoading = false;
        }
    }

    function scheduleGlobalSettingsSave(): void {
        if (!globalSettings) {
            return;
        }

        globalSaveError = null;
        globalSettingsSaveQueue.enqueue(
            structuredClone($state.snapshot(globalSettings)),
        );
    }

</script>

<div
        id="modern-settings-view"
        class="settings-view"
        role="tabpanel"
        aria-labelledby="modern-command-tab-settings"
>
    <div class="settings-window">
        <div class="window-heading">
            <div>
                <p class="overline">ClickGUI</p>
                <h1>Settings</h1>
                <p class="heading-copy">
                    Choose how the ClickGUI and in-game HUD feel, then tune shared client behavior.
                </p>
            </div>

            <div class="save-state" aria-live="polite">
                {#if $session.saving}
                    <span class="spinner" aria-hidden="true"></span>
                    Applying theme
                {:else if globalSaving}
                    <span class="spinner" aria-hidden="true"></span>
                    Saving changes
                {:else}
                    Changes save automatically
                {/if}
            </div>
        </div>

        <div class="settings-content">
            <section class="settings-section" aria-labelledby="appearance-heading">
                <div class="section-heading">
                    <div>
                        <h2 id="appearance-heading">ClickGUI Appearance</h2>
                        <p>Switch the entire ClickGUI layout without changing your color theme.</p>
                    </div>
                    <span class="section-label">Interface theme</span>
                </div>

                <div class="theme-options" role="radiogroup" aria-labelledby="appearance-heading">
                    {#each MODERN_THEME_OPTIONS as option, optionIndex (option.value)}
                        <button
                                class="theme-option"
                                class:selected={$session.theme === option.value}
                                type="button"
                                role="radio"
                                disabled={$session.loading || $session.saving}
                                aria-checked={$session.theme === option.value}
                                style:--modern-theme-option-index={motionStaggerIndex(optionIndex, MODERN_SETTING_STAGGER_LIMIT)}
                                onclick={() => void session.selectTheme(option.value)}
                        >
                            <span class="theme-preview" class:modern={option.value === "Modern"} aria-hidden="true">
                                <span class="preview-bar"></span>
                                <span class="preview-panel preview-panel-primary"></span>
                                <span class="preview-panel preview-panel-secondary"></span>
                            </span>

                            <span class="theme-copy">
                                <span class="theme-eyebrow">{option.eyebrow}</span>
                                <strong>{option.title}</strong>
                                <span>{option.description}</span>
                            </span>

                            <span class="selection-indicator" aria-hidden="true">
                                {#if $session.theme === option.value}
                                    <svg viewBox="0 0 16 16">
                                        <path d="m3.2 8.3 3 3.1 6.7-6.8 1.2 1.2-7.9 8-4.2-4.3 1.2-1.2Z"/>
                                    </svg>
                                {/if}
                            </span>
                        </button>
                    {/each}
                </div>

                {#if $session.saveError}
                    <div class="inline-message error" role="alert">
                        <span>{$session.saveError}</span>
                        {#if $session.failedTheme}
                            <button type="button" disabled={$session.saving} onclick={() => void session.retryThemeSave()}>
                                Retry
                            </button>
                        {/if}
                    </div>
                {/if}
            </section>

            <div class="settings-section hud-appearance-section">
                <HudThemeSelector variant="card" />
            </div>

            <section class="settings-section" aria-labelledby="global-heading">
                <div class="section-heading">
                    <div>
                        <h2 id="global-heading">Global settings</h2>
                        <p>Client-wide targeting, command, chat, and presence preferences.</p>
                    </div>
                    {#if globalSaving}
                        <span class="section-label">Saving</span>
                    {/if}
                </div>

                {#if globalLoading && !globalSettings}
                    <div class="empty-state" role="status">
                        <span class="spinner" aria-hidden="true"></span>
                        Loading global settings
                    </div>
                {:else if globalLoadError && !globalSettings}
                    <div class="empty-state error" role="alert">
                        <span>{globalLoadError}</span>
                        <button type="button" onclick={() => fetchGlobalSettings()}>Try again</button>
                    </div>
                {:else if globalSettings}
                    <div class="settings-grid">
                        {#each globalSettings.value as _, index (globalSettings.value[index].name)}
                            {#if globalSettings.value[index].valueType === "CONFIGURABLE"
                            || globalSettings.value[index].valueType === "TOGGLEABLE"}
                                <div
                                        class="setting-card"
                                        style:--modern-setting-card-index={motionStaggerIndex(index, MODERN_SETTING_STAGGER_LIMIT)}
                                >
                                    <ConfigurableSetting
                                            path="clickgui.global"
                                            bind:setting={globalSettings.value[index]}
                                            hideExpandControl={true}
                                            on:change={scheduleGlobalSettingsSave}
                                    />
                                </div>
                            {/if}
                        {/each}
                    </div>

                    {#if globalSaveError}
                        <div class="inline-message error" role="alert">
                            <span>{globalSaveError}</span>
                            <button type="button" onclick={() => globalSettingsSaveQueue.retry()}>
                                Retry save
                            </button>
                        </div>
                    {/if}
                {/if}
            </section>
        </div>
    </div>
</div>

<style lang="scss">
  @use "./ModernSettings.styles";
</style>
