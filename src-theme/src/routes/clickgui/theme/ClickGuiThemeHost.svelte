<script lang="ts">
    import {onMount} from "svelte";
    import {fade} from "svelte/transition";
    import type {ClickGuiValueChangeEvent} from "../../../integration/events";
    import {listen} from "../../../integration/ws";
    import TabbedClickGui from "../TabbedClickGui.svelte";
    import ModernTabbedClickGui from "../themes/modern/ModernTabbedClickGui.svelte";
    import {clickGuiThemeSession} from "./themeSession";

    const THEME_CROSS_FADE_MS = 140;

    let reduceMotion = $state(false);
    const transitionDuration = $derived(reduceMotion ? 0 : THEME_CROSS_FADE_MS);

    listen("clickGuiValueChange", (event: ClickGuiValueChangeEvent) => {
        if (event.configurable.name === "ClickGUI") {
            clickGuiThemeSession.synchronize(event.configurable);
        }
    });

    onMount(() => {
        void clickGuiThemeSession.load();

        const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
        const updateMotionPreference = () => {
            reduceMotion = motionPreference.matches;
        };

        updateMotionPreference();
        motionPreference.addEventListener("change", updateMotionPreference);

        return () => {
            motionPreference.removeEventListener("change", updateMotionPreference);
        };
    });
</script>

<div class="clickgui-theme-host">
    {#if $clickGuiThemeSession.loading && !$clickGuiThemeSession.settings}
        <div class="theme-status" role="status" aria-live="polite">
            <span>Loading ClickGUI…</span>
        </div>
    {:else if !$clickGuiThemeSession.settings}
        <div class="theme-status theme-error" role="alert">
            <strong>ClickGUI could not be loaded</strong>
            <span>
                {$clickGuiThemeSession.loadError ?? "The ClickGUI settings are unavailable."}
            </span>
            <button type="button" onclick={() => clickGuiThemeSession.load()}>
                Retry
            </button>
        </div>
    {:else}
        {#key $clickGuiThemeSession.theme}
            <div
                    class="theme-stage"
                    transition:fade={{duration: transitionDuration}}
            >
                {#if $clickGuiThemeSession.theme === "Modern"}
                    <ModernTabbedClickGui session={clickGuiThemeSession}/>
                {:else}
                    <TabbedClickGui/>
                {/if}
            </div>
        {/key}
    {/if}
</div>

<style lang="scss">
  .clickgui-theme-host,
  .theme-stage {
    position: absolute;
    inset: 0;
    overflow: hidden;
  }

  .clickgui-theme-host {
    background: #090b0f;
  }

  .theme-status {
    position: absolute;
    inset: 0;
    display: grid;
    place-content: center;
    justify-items: center;
    gap: 10px;
    padding: 24px;
    color: #f2f4f8;
    background:
      radial-gradient(circle at 50% 40%, rgba(255, 255, 255, 0.035), transparent 42%),
      #090b0f;
    text-align: center;
  }

  .theme-status span {
    max-width: 520px;
    color: #aeb4c0;
    font-size: 13px;
    line-height: 1.5;
  }

  .theme-error button {
    margin-top: 6px;
    padding: 8px 16px;
    color: #f8fafc;
    background: rgba(255, 255, 255, 0.08);
    border: 1px solid rgba(255, 255, 255, 0.14);
    border-radius: 8px;
    cursor: pointer;
  }

  .theme-error button:hover {
    background: rgba(255, 255, 255, 0.12);
  }

  .theme-error button:focus-visible {
    outline: 2px solid var(--accent-color);
    outline-offset: 2px;
  }
</style>
