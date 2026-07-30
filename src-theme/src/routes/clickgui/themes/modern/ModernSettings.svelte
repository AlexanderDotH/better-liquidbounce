<script lang="ts">
    import {onMount} from "svelte";
    import type {
        ClickGuiThemeSession,
        ClickGuiVisualTheme,
    } from "../../theme/clickGuiThemeState";
    import type {ConfigurableSetting as ConfigurableSettingData} from "../../../../integration/types";
    import ConfigurableSetting from "../../setting/ConfigurableSetting.svelte";
    import {createLatestValueSaveQueue} from "../../theme/latestValueSaveQueue";
    import {productionGlobalSettingsDataSource} from "./model/clickGuiDataSource";
    import {
        MODERN_SETTING_STAGGER_LIMIT,
        motionStaggerIndex,
    } from "./model/modernMotion";

    let {session} = $props<{session: ClickGuiThemeSession}>();

    const themeOptions: readonly {
        value: ClickGuiVisualTheme;
        title: string;
        eyebrow: string;
        description: string;
    }[] = [
        {
            value: "Modern",
            title: "Graphite Glass",
            eyebrow: "Modern",
            description: "Balanced floating panels, a compact command bar, and restrained motion.",
        },
        {
            value: "Classic",
            title: "Original",
            eyebrow: "Classic",
            description: "The familiar LiquidBounce ClickGUI with its existing layout and interactions.",
        },
    ];

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
                ? describeError(state.error, "Unable to save global settings.")
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
            globalLoadError = describeError(error, "Unable to load global settings.");
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

    function selectTheme(theme: ClickGuiVisualTheme): void {
        void session.selectTheme(theme);
    }

    function retryThemeSave(): void {
        void session.retryThemeSave();
    }

    function describeError(error: unknown, fallback: string): string {
        if (!(error instanceof Error) || !error.message.trim()) {
            return fallback;
        }

        return `${fallback} ${error.message}`;
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
                    Choose how the interface feels, then tune shared client behavior.
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
                        <h2 id="appearance-heading">Appearance</h2>
                        <p>Switch the entire ClickGUI layout without changing your color theme.</p>
                    </div>
                    <span class="section-label">Interface theme</span>
                </div>

                <div class="theme-options" role="radiogroup" aria-labelledby="appearance-heading">
                    {#each themeOptions as option, optionIndex (option.value)}
                        <button
                                class="theme-option"
                                class:selected={$session.theme === option.value}
                                type="button"
                                role="radio"
                                disabled={$session.loading || $session.saving}
                                aria-checked={$session.theme === option.value}
                                style:--modern-theme-option-index={motionStaggerIndex(optionIndex, MODERN_SETTING_STAGGER_LIMIT)}
                                onclick={() => selectTheme(option.value)}
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
                            <button type="button" disabled={$session.saving} onclick={retryThemeSave}>
                                Retry
                            </button>
                        {/if}
                    </div>
                {/if}
            </section>

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
  .settings-view {
    position: absolute;
    inset: 84px 20px 20px;
    overflow: auto;
    padding: 0 0 20px;
    color: var(--modern-text-primary, #eef1f5);
    scrollbar-gutter: stable;
  }

  .settings-window {
    width: min(980px, 100%);
    margin: 0 auto;
    overflow: hidden;
    background: var(--modern-surface-command, rgba(15, 18, 23, 0.96));
    border: 1px solid var(--modern-border, rgba(255, 255, 255, 0.1));
    border-radius: 16px;
    box-shadow: 0 18px 48px rgba(0, 0, 0, 0.3);
    animation:
      settings-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
  }

  .window-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 24px;
    padding: 25px 28px 22px;
    background: rgba(255, 255, 255, 0.018);
    border-bottom: 1px solid rgba(255, 255, 255, 0.075);
  }

  .overline,
  .theme-eyebrow {
    color: color-mix(in srgb, var(--accent-color) 72%, #c8cdd5);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.09em;
    text-transform: uppercase;
  }

  h1 {
    margin-top: 4px;
    color: var(--modern-text-primary, #f5f7fa);
    font-size: 23px;
    font-weight: 650;
    letter-spacing: -0.035em;
  }

  .heading-copy,
  .section-heading p {
    color: var(--modern-text-muted, #8e96a2);
    font-size: 12px;
    line-height: 1.55;
  }

  .heading-copy {
    max-width: 510px;
    margin-top: 6px;
  }

  .save-state {
    min-height: 29px;
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 7px 10px;
    color: var(--modern-text-muted, #8e96a2);
    background: rgba(255, 255, 255, 0.035);
    border: 1px solid rgba(255, 255, 255, 0.065);
    border-radius: 8px;
    font-size: 10px;
    white-space: nowrap;
  }

  .spinner {
    width: 11px;
    height: 11px;
    flex: 0 0 auto;
    border: 2px solid rgba(255, 255, 255, 0.18);
    border-top-color: var(--accent-color);
    border-radius: 50%;
    animation: spin 700ms linear infinite;
  }

  .settings-content {
    display: grid;
    gap: 1px;
  }

  .settings-section {
    padding: 24px 28px 28px;
    background: rgba(255, 255, 255, 0.009);
    animation:
      modern-settings-section-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
  }

  .settings-section:nth-child(1) {
    animation-delay: 54ms;
  }

  .settings-section:nth-child(2) {
    animation-delay: 94ms;
  }

  .settings-section + .settings-section {
    border-top: 1px solid rgba(255, 255, 255, 0.07);
  }

  .section-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 20px;
    margin-bottom: 17px;
  }

  .section-heading h2 {
    margin-bottom: 4px;
    color: var(--modern-text-primary, #edf0f4);
    font-size: 14px;
    font-weight: 650;
    letter-spacing: -0.015em;
  }

  .section-label {
    flex: 0 0 auto;
    padding: 4px 7px;
    color: #7f8792;
    background: rgba(255, 255, 255, 0.035);
    border-radius: 5px;
    font-size: 9px;
    font-weight: 600;
    letter-spacing: 0.035em;
    text-transform: uppercase;
  }

  .theme-options {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .theme-option {
    position: relative;
    min-width: 0;
    display: grid;
    grid-template-columns: 108px minmax(0, 1fr) 20px;
    align-items: center;
    gap: 14px;
    padding: 12px;
    color: #f3f5f7;
    text-align: left;
    background: rgba(255, 255, 255, 0.025);
    border: 1px solid rgba(255, 255, 255, 0.075);
    border-radius: 11px;
    cursor: pointer;
    font-family: inherit;
    transition:
      background-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      border-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      box-shadow var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      transform var(--modern-motion-fast, 100ms) var(--modern-motion-easing, ease);
    animation:
      modern-theme-option-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
    animation-delay:
      calc(
        78ms
        + var(--modern-theme-option-index, 0)
        * var(--modern-motion-stagger, 24ms)
      );
  }

  .theme-option:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.045);
    border-color: rgba(255, 255, 255, 0.13);
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.14);
    transform: translateY(-2px);
  }

  .theme-option:active:not(:disabled) {
    transform: translateY(0);
  }

  .theme-option.selected {
    background: color-mix(in srgb, var(--accent-color) 7%, rgba(255, 255, 255, 0.026));
    border-color: color-mix(in srgb, var(--accent-color) 40%, rgba(255, 255, 255, 0.1));
  }

  .theme-option:disabled {
    cursor: default;
    opacity: 0.58;
  }

  .theme-option:focus-visible,
  .inline-message button:focus-visible,
  .empty-state button:focus-visible {
    outline: 2px solid var(--modern-focus-ring, color-mix(in srgb, var(--accent-color) 80%, white));
    outline-offset: 2px;
  }

  .theme-preview {
    position: relative;
    height: 67px;
    display: block;
    overflow: hidden;
    background: #0a0c10;
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 7px;
  }

  .theme-preview .preview-bar {
    position: absolute;
    top: 7px;
    left: 7px;
    right: 7px;
    height: 8px;
    background: rgba(255, 255, 255, 0.11);
    border-radius: 2px;
    transition:
      transform
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .theme-preview .preview-panel {
    position: absolute;
    top: 23px;
    bottom: 7px;
    background: rgba(255, 255, 255, 0.075);
    border-top: 2px solid var(--accent-color);
    border-radius: 2px;
    transition:
      transform
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .theme-preview .preview-panel-primary {
    left: 8px;
    width: 39px;
  }

  .theme-preview .preview-panel-secondary {
    left: 54px;
    right: 8px;
  }

  .theme-preview.modern .preview-bar {
    height: 10px;
    background: rgba(255, 255, 255, 0.085);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 4px;
  }

  .theme-preview.modern .preview-panel {
    top: 25px;
    background: rgba(255, 255, 255, 0.055);
    border: 1px solid rgba(255, 255, 255, 0.095);
    border-radius: 4px;
  }

  .theme-option.selected .preview-bar {
    transform: translateY(1px);
  }

  .theme-option.selected .preview-panel-primary {
    transform: translateY(-2px);
  }

  .theme-option.selected .preview-panel-secondary {
    transform: translateY(2px);
  }

  .theme-copy {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .theme-copy strong {
    color: #f2f4f7;
    font-size: 13px;
    font-weight: 650;
  }

  .theme-copy > span:last-child {
    color: var(--modern-text-muted, #858e9a);
    font-size: 10px;
    line-height: 1.45;
  }

  .selection-indicator {
    width: 18px;
    height: 18px;
    display: grid;
    place-items: center;
    color: #ffffff;
    background: rgba(255, 255, 255, 0.035);
    border: 1px solid rgba(255, 255, 255, 0.11);
    border-radius: 50%;
    transition:
      background-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      border-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .selected .selection-indicator {
    background: var(--accent-color);
    border-color: var(--accent-color);
  }

  .selection-indicator svg {
    width: 11px;
    height: 11px;
    fill: currentColor;
    animation:
      modern-selection-confirm
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
  }

  .settings-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
    gap: 12px;
  }

  .setting-card {
    min-width: 0;
    padding: 8px 13px 11px;
    background: rgba(255, 255, 255, 0.022);
    border: 1px solid rgba(255, 255, 255, 0.065);
    border-radius: 10px;
    animation:
      modern-setting-card-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
    animation-delay:
      calc(
        116ms
        + var(--modern-setting-card-index, 0)
        * var(--modern-motion-stagger, 24ms)
      );
    transition:
      background-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      border-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      box-shadow
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      transform
      var(--modern-motion-fast, 100ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .setting-card:hover {
    background: rgba(255, 255, 255, 0.03);
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 0 8px 22px rgba(0, 0, 0, 0.13);
    transform: translateY(-2px);
  }

  .inline-message,
  .empty-state {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    margin-top: 12px;
    padding: 12px 14px;
    color: #a7afba;
    background: rgba(255, 255, 255, 0.025);
    border: 1px solid rgba(255, 255, 255, 0.065);
    border-radius: 9px;
    font-size: 11px;
  }

  .empty-state {
    min-height: 92px;
    margin-top: 0;
  }

  .inline-message.error,
  .empty-state.error {
    color: #efaca5;
    background: rgba(210, 63, 48, 0.08);
    border-color: rgba(235, 103, 89, 0.22);
  }

  .inline-message button,
  .empty-state button {
    flex: 0 0 auto;
    padding: 5px 8px;
    color: #f0f2f5;
    background: rgba(255, 255, 255, 0.065);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 6px;
    cursor: pointer;
    font-family: inherit;
    font-size: 10px;
    font-weight: 600;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }

  @keyframes settings-enter {
    from {
      transform: translateY(8px);
      opacity: 0;
    }

    to {
      transform: translateY(0);
      opacity: 1;
    }
  }

  @keyframes modern-settings-section-enter {
    from {
      opacity: 0;
      transform: translateY(5px);
    }
  }

  @keyframes modern-theme-option-enter {
    from {
      opacity: 0;
      transform: translateY(6px);
    }
  }

  @keyframes modern-setting-card-enter {
    from {
      opacity: 0;
      transform: translateY(7px);
    }
  }

  @keyframes modern-selection-confirm {
    from {
      opacity: 0;
      transform: scale(0.45) rotate(-18deg);
    }
  }

  @media (max-width: 800px) {
    .settings-view {
      right: 10px;
      left: 10px;
    }

    .theme-options,
    .settings-grid {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 560px) {
    .window-heading,
    .section-heading {
      flex-direction: column;
    }

    .window-heading,
    .settings-section {
      padding-right: 18px;
      padding-left: 18px;
    }

    .save-state {
      align-self: stretch;
    }

    .theme-option {
      grid-template-columns: 88px minmax(0, 1fr) 20px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .settings-window,
    .spinner,
    .theme-option,
    .theme-preview .preview-bar,
    .theme-preview .preview-panel,
    .selection-indicator {
      animation-duration: 0ms;
      transition-duration: 0ms;
    }

    .settings-section,
    .theme-option,
    .selection-indicator svg,
    .setting-card {
      animation: none;
    }

    .theme-option:hover:not(:disabled),
    .theme-option:active:not(:disabled),
    .setting-card:hover {
      transform: none;
    }
  }
</style>
