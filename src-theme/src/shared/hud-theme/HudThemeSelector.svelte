<script lang="ts">
    import {onMount} from "svelte";
    import type {HudVisualTheme} from "../../routes/hud/theme/hudThemeState";
    import {hudThemeSession} from "../../routes/hud/theme/themeSession";

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
  @use "../../routes/hud/theme/HudThemeSelector.styles";
</style>
