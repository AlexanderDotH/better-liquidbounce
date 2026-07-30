<script lang="ts">
    import {onMount} from "svelte";
    import type {HudVisualTheme} from "./hudThemeState";
    import {hudThemeSession} from "./themeSession";

    let {variant = "card"} = $props<{
        variant?: "card" | "compact";
    }>();

    const themeOptions: readonly {
        value: HudVisualTheme;
        eyebrow: string;
        title: string;
        description: string;
    }[] = [
        {
            value: "Modern",
            eyebrow: "Modern",
            title: "Graphite Glass",
            description: "Compact glass surfaces designed to match the Modern ClickGUI.",
        },
        {
            value: "Classic",
            eyebrow: "Classic",
            title: "Original HUD",
            description: "The familiar LiquidBounce widgets with their current presentation.",
        },
    ];

    onMount(() => {
        if (!$hudThemeSession.settings) {
            void hudThemeSession.load();
        }
    });

    function selectTheme(theme: HudVisualTheme): void {
        void hudThemeSession.selectTheme(theme);
    }

    function retryThemeSave(): void {
        void hudThemeSession.retryThemeSave();
    }
</script>

<section
        class={`hud-theme-selector hud-theme-selector--${variant}`}
        aria-label="HUD appearance"
>
    <header class="selector-heading">
        <div class="selector-copy">
            <span class="selector-overline">In-game interface</span>
            <h2>HUD Appearance</h2>
            <p>Change the HUD presentation while keeping every widget, position, and setting.</p>
        </div>

        {#if $hudThemeSession.saving}
            <span class="selector-state" role="status">
                <span class="spinner" aria-hidden="true"></span>
                Applying
            </span>
        {:else}
            <span class="selector-state">HUD theme</span>
        {/if}
    </header>

    {#if $hudThemeSession.loading && !$hudThemeSession.settings}
        <div class="selector-message" role="status">
            <span class="spinner" aria-hidden="true"></span>
            Loading HUD themes
        </div>
    {:else if $hudThemeSession.loadError && !$hudThemeSession.settings}
        <div class="selector-message selector-message--error" role="alert">
            <span>{$hudThemeSession.loadError}</span>
            <button type="button" onclick={() => hudThemeSession.load()}>Try again</button>
        </div>
    {:else}
        <div class="theme-options" role="radiogroup" aria-label="HUD theme">
            {#each themeOptions as option (option.value)}
                <button
                        type="button"
                        role="radio"
                        class="theme-option"
                        class:selected={$hudThemeSession.theme === option.value}
                        aria-checked={$hudThemeSession.theme === option.value}
                        disabled={$hudThemeSession.loading || $hudThemeSession.saving}
                        onclick={() => selectTheme(option.value)}
                >
                    <span
                            class="theme-preview"
                            class:theme-preview--modern={option.value === "Modern"}
                            aria-hidden="true"
                    >
                        <span class="preview-watermark"></span>
                        <span class="preview-list">
                            <span></span>
                            <span></span>
                            <span></span>
                        </span>
                        <span class="preview-hotbar"></span>
                    </span>

                    <span class="theme-copy">
                        <span class="theme-eyebrow">{option.eyebrow}</span>
                        <strong>{option.title}</strong>
                        <span>{option.description}</span>
                    </span>

                    <span class="selection-indicator" aria-hidden="true">
                        {#if $hudThemeSession.theme === option.value}
                            <svg viewBox="0 0 16 16">
                                <path d="m3.2 8.3 3 3.1 6.7-6.8 1.2 1.2-7.9 8-4.2-4.3 1.2-1.2Z"/>
                            </svg>
                        {/if}
                    </span>
                </button>
            {/each}
        </div>
    {/if}

    {#if $hudThemeSession.saveError}
        <div class="selector-message selector-message--error" role="alert">
            <span>{$hudThemeSession.saveError}</span>
            {#if $hudThemeSession.failedTheme}
                <button
                        type="button"
                        disabled={$hudThemeSession.saving}
                        onclick={retryThemeSave}
                >
                    Retry {$hudThemeSession.failedTheme}
                </button>
            {/if}
        </div>
    {/if}
</section>

<style lang="scss">
  .hud-theme-selector {
    --hud-selector-surface: rgba(15, 18, 23, 0.84);
    --hud-selector-surface-raised: rgba(255, 255, 255, 0.04);
    --hud-selector-border: rgba(255, 255, 255, 0.09);
    --hud-selector-text: #eef1f5;
    --hud-selector-muted: #8e96a2;

    color: var(--hud-selector-text);
  }

  .hud-theme-selector--card {
    padding: 22px;
    background: var(--hud-selector-surface);
    border: 1px solid var(--hud-selector-border);
    border-radius: 12px;
  }

  .hud-theme-selector--compact {
    display: grid;
    grid-template-columns: minmax(190px, 0.8fr) minmax(300px, 1.2fr);
    align-items: center;
    gap: 12px 20px;
    margin: 2px 0 18px;
    padding: 14px;
    color: var(--clickgui-text-color, var(--hud-selector-text));
    background: var(--clickgui-window-header-background-color, var(--hud-selector-surface));
    border: 1px solid var(--clickgui-global-settings-divider-color, var(--hud-selector-border));
    border-radius: 5px;
  }

  .selector-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 18px;
    margin-bottom: 15px;
  }

  .hud-theme-selector--compact .selector-heading {
    margin: 0;
  }

  .selector-copy {
    display: grid;
    gap: 4px;
  }

  .selector-overline,
  .theme-eyebrow {
    color: color-mix(in srgb, var(--accent-color) 72%, #c8cdd5);
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  h2 {
    margin: 0;
    color: inherit;
    font-size: 16px;
    font-weight: 650;
  }

  .selector-copy p,
  .theme-copy > span:last-child,
  .selector-state {
    color: var(--hud-selector-muted);
    font-size: 11px;
    line-height: 1.4;
  }

  .hud-theme-selector--compact .selector-copy p,
  .hud-theme-selector--compact .theme-copy > span:last-child,
  .hud-theme-selector--compact .selector-state {
    color: var(--clickgui-text-dimmed-color, var(--hud-selector-muted));
  }

  .selector-copy p {
    max-width: 520px;
    margin: 0;
  }

  .selector-state {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex: 0 0 auto;
    padding: 6px 9px;
    background: var(--hud-selector-surface-raised);
    border: 1px solid var(--hud-selector-border);
    border-radius: 7px;
  }

  .hud-theme-selector--compact .selector-overline,
  .hud-theme-selector--compact .selector-state {
    display: none;
  }

  .theme-options {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 9px;
  }

  .theme-option {
    position: relative;
    display: grid;
    grid-template-columns: 94px minmax(0, 1fr) 20px;
    align-items: center;
    gap: 12px;
    min-width: 0;
    padding: 11px;
    color: inherit;
    background: var(--hud-selector-surface-raised);
    border: 1px solid transparent;
    border-radius: 9px;
    text-align: left;
    cursor: pointer;
    transition:
      background-color 160ms ease,
      border-color 160ms ease,
      transform 160ms ease;
  }

  .theme-option:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.065);
    transform: translateY(-1px);
  }

  .theme-option.selected {
    background: color-mix(in srgb, var(--accent-color) 8%, rgba(255, 255, 255, 0.04));
    border-color: color-mix(in srgb, var(--accent-color) 58%, transparent);
  }

  .theme-option:focus-visible {
    outline: 2px solid var(--accent-color);
    outline-offset: 2px;
  }

  .theme-option:disabled {
    cursor: wait;
    opacity: 0.7;
  }

  .theme-preview {
    position: relative;
    display: block;
    width: 94px;
    height: 54px;
    overflow: hidden;
    background: rgba(5, 8, 12, 0.78);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 6px;
  }

  .preview-watermark,
  .preview-list span,
  .preview-hotbar {
    position: absolute;
    display: block;
    background: rgba(255, 255, 255, 0.26);
  }

  .preview-watermark {
    top: 6px;
    left: 6px;
    width: 27px;
    height: 5px;
  }

  .preview-list {
    position: absolute;
    top: 7px;
    right: 6px;
    display: grid;
    gap: 3px;
    justify-items: end;
  }

  .preview-list span {
    position: static;
    width: 26px;
    height: 3px;
  }

  .preview-list span:nth-child(2) {
    width: 20px;
  }

  .preview-list span:nth-child(3) {
    width: 23px;
  }

  .preview-hotbar {
    bottom: 5px;
    left: 50%;
    width: 48px;
    height: 8px;
    transform: translateX(-50%);
  }

  .theme-preview--modern .preview-watermark,
  .theme-preview--modern .preview-hotbar {
    background: rgba(21, 25, 31, 0.92);
    border: 1px solid rgba(255, 255, 255, 0.15);
    border-radius: 4px;
  }

  .theme-preview--modern .preview-list span {
    background: color-mix(in srgb, var(--accent-color) 48%, rgba(255, 255, 255, 0.3));
    border-radius: 2px;
  }

  .theme-copy {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  .theme-copy strong {
    font-size: 13px;
    font-weight: 650;
  }

  .selection-indicator {
    display: grid;
    place-items: center;
    width: 18px;
    height: 18px;
    border: 1px solid rgba(255, 255, 255, 0.14);
    border-radius: 50%;
  }

  .selected .selection-indicator {
    color: #fff;
    background: var(--accent-color);
    border-color: var(--accent-color);
  }

  .selection-indicator svg {
    width: 11px;
    fill: currentColor;
  }

  .hud-theme-selector--compact .theme-options {
    gap: 7px;
  }

  .hud-theme-selector--compact .theme-option {
    grid-template-columns: minmax(0, 1fr) 18px;
    gap: 8px;
    padding: 8px 10px;
    background: var(--clickgui-tabs-background-color, var(--hud-selector-surface-raised));
    border-radius: 5px;
  }

  .hud-theme-selector--compact .theme-option:hover:not(:disabled) {
    background: var(--clickgui-tab-hover-background-color, rgba(255, 255, 255, 0.065));
    transform: none;
  }

  .hud-theme-selector--compact .theme-option.selected {
    background: var(--clickgui-tab-active-background-color, rgba(255, 255, 255, 0.07));
    border-color: var(--clickgui-tab-active-border-color, var(--accent-color));
  }

  .hud-theme-selector--compact .theme-preview,
  .hud-theme-selector--compact .theme-eyebrow,
  .hud-theme-selector--compact .theme-copy > span:last-child {
    display: none;
  }

  .hud-theme-selector--compact .theme-copy strong {
    font-size: 12px;
    font-weight: 600;
  }

  .selector-message {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 9px;
    margin-top: 11px;
    padding: 9px 11px;
    color: var(--hud-selector-muted);
    background: var(--hud-selector-surface-raised);
    border: 1px solid var(--hud-selector-border);
    border-radius: 7px;
    font-size: 11px;
  }

  .hud-theme-selector--compact .selector-message {
    grid-column: 1 / -1;
    margin-top: 0;
  }

  .selector-message--error {
    justify-content: space-between;
    color: #ffaaaa;
  }

  .selector-message button {
    flex: 0 0 auto;
    padding: 5px 8px;
    color: inherit;
    background: rgba(255, 255, 255, 0.06);
    border: 1px solid currentColor;
    border-radius: 5px;
    cursor: pointer;
  }

  .spinner {
    width: 10px;
    height: 10px;
    border: 1px solid currentColor;
    border-right-color: transparent;
    border-radius: 50%;
    animation: selector-spin 700ms linear infinite;
  }

  @keyframes selector-spin {
    to {
      transform: rotate(360deg);
    }
  }

  @media (max-width: 760px) {
    .hud-theme-selector--compact {
      grid-template-columns: 1fr;
    }

    .hud-theme-selector--card .theme-options {
      grid-template-columns: 1fr;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .theme-option {
      transition: none;
    }

    .spinner {
      animation: none;
    }
  }
</style>
