<script lang="ts">
    import type {Snippet} from "svelte";
    import type {ClickGuiView} from "../../theme/clickGuiThemeState";
    import {moveClickGuiView} from "./modernShellState";

    interface Props {
        view: ClickGuiView;
        busy?: boolean;
        search?: Snippet;
        onViewChange: (view: ClickGuiView) => void;
        onResetLayout: () => void;
    }

    let {
        view,
        busy = false,
        search,
        onViewChange,
        onResetLayout,
    }: Props = $props();

    const tabs: readonly {view: ClickGuiView; label: string}[] = [
        {view: "clickgui", label: "ClickGUI"},
        {view: "settings", label: "Settings"},
    ];

    function handleTabKeydown(event: KeyboardEvent): void {
        const nextView = keyboardView(event.key);
        if (!nextView) {
            return;
        }

        event.preventDefault();
        onViewChange(nextView);

        requestAnimationFrame(() => {
            document.getElementById(`modern-command-tab-${nextView}`)?.focus();
        });
    }

    function keyboardView(key: string): ClickGuiView | null {
        if (key === "Home") {
            return "clickgui";
        }

        if (key === "End") {
            return "settings";
        }

        if (key !== "ArrowLeft" && key !== "ArrowRight") {
            return null;
        }

        return moveClickGuiView(view, key === "ArrowRight" ? 1 : -1);
    }
</script>

<header class="command-bar" aria-label="ClickGUI command bar">
    <div class="identity" aria-label="LiquidBounce">
        <span class="identity-mark" aria-hidden="true">L</span>
        <span class="identity-name">LiquidBounce</span>
    </div>

    <div class="tabs" role="tablist" aria-label="ClickGUI views">
        {#each tabs as tab (tab.view)}
            <button
                    id="modern-command-tab-{tab.view}"
                    class="tab"
                    class:active={view === tab.view}
                    type="button"
                    role="tab"
                    aria-selected={view === tab.view}
                    aria-controls="modern-{tab.view}-view"
                    tabindex={view === tab.view ? 0 : -1}
                    onclick={() => onViewChange(tab.view)}
                    onkeydown={handleTabKeydown}
            >
                {tab.label}
            </button>
        {/each}
    </div>

    <div
            class="search-region"
            class:hidden={view !== "clickgui"}
            aria-hidden={view !== "clickgui"}
            inert={view !== "clickgui"}
    >
        {#if search}
            {@render search()}
        {/if}
    </div>

    <div class="actions">
        {#if view === "clickgui"}
            <button
                    class="action-button"
                    type="button"
                    disabled={busy}
                    aria-label="Reset Modern panel layout"
                    title="Reset panel layout"
                    onclick={onResetLayout}
            >
                <svg aria-hidden="true" viewBox="0 0 20 20">
                    <path d="M15.9 5.2A7 7 0 1 0 17 11h-1.7a5.3 5.3 0 1 1-.8-2.8l-2.2.1V10H18V4.3h-1.7l-.4.9Z"/>
                </svg>
                <span>Reset layout</span>
            </button>
        {/if}
    </div>
</header>

<style lang="scss">
  .command-bar {
    position: absolute;
    z-index: 1000000;
    top: 16px;
    left: 20px;
    right: 20px;
    min-height: 52px;
    display: grid;
    grid-template-columns: minmax(150px, 1fr) auto minmax(240px, 1.4fr) minmax(150px, 1fr);
    align-items: center;
    gap: 14px;
    padding: 7px 9px;
    color: var(--modern-text-primary, #f4f6f8);
    background: var(--modern-surface-command, rgba(15, 18, 23, 0.96));
    border: 1px solid var(--modern-border, rgba(255, 255, 255, 0.1));
    border-radius: 14px;
    box-shadow: 0 10px 28px rgba(0, 0, 0, 0.24);
  }

  .identity {
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 9px;
    padding-left: 3px;
  }

  .identity-mark {
    width: 29px;
    height: 29px;
    flex: 0 0 auto;
    display: grid;
    place-items: center;
    color: #ffffff;
    background: color-mix(in srgb, var(--accent-color) 78%, #30343b);
    border: 1px solid color-mix(in srgb, var(--accent-color) 48%, white 10%);
    border-radius: 8px;
    font-size: 13px;
    font-weight: 700;
  }

  .identity-name {
    overflow: hidden;
    color: var(--modern-text-primary, #eef1f5);
    font-size: 13px;
    font-weight: 600;
    letter-spacing: -0.01em;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .tabs {
    display: flex;
    align-items: center;
    gap: 3px;
    padding: 3px;
    background: rgba(255, 255, 255, 0.045);
    border: 1px solid rgba(255, 255, 255, 0.07);
    border-radius: 9px;
  }

  .tab,
  .action-button {
    font-family: inherit;
  }

  .tab {
    min-height: 30px;
    padding: 0 12px;
    color: #929aa6;
    background: transparent;
    border: 1px solid transparent;
    border-radius: 7px;
    cursor: pointer;
    font-size: 12px;
    font-weight: 600;
    transition:
      color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      background-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      border-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease);
  }

  .tab:hover {
    color: #e7eaee;
    background: rgba(255, 255, 255, 0.055);
  }

  .tab.active {
    color: #ffffff;
    background: color-mix(in srgb, var(--accent-color) 14%, rgba(255, 255, 255, 0.055));
    border-color: color-mix(in srgb, var(--accent-color) 36%, transparent);
  }

  .search-region {
    min-width: 0;
    transition: opacity var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease);
  }

  .search-region.hidden {
    visibility: hidden;
    opacity: 0;
    pointer-events: none;
  }

  .actions {
    display: flex;
    justify-content: flex-end;
  }

  .action-button {
    min-height: 32px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 7px;
    padding: 0 10px;
    color: #aeb5bf;
    background: rgba(255, 255, 255, 0.045);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 8px;
    cursor: pointer;
    font-size: 11px;
    font-weight: 600;
    transition:
      color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      background-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      border-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease);
  }

  .action-button:hover:not(:disabled) {
    color: #f5f7fa;
    background: rgba(255, 255, 255, 0.075);
    border-color: rgba(255, 255, 255, 0.13);
  }

  .action-button:disabled {
    cursor: default;
    opacity: 0.5;
  }

  .action-button svg {
    width: 14px;
    height: 14px;
    fill: currentColor;
  }

  .tab:focus-visible,
  .action-button:focus-visible {
    outline: 2px solid var(--modern-focus-ring, color-mix(in srgb, var(--accent-color) 80%, white));
    outline-offset: 2px;
  }

  @media (max-width: 960px) {
    .command-bar {
      grid-template-columns: auto auto minmax(170px, 1fr) auto;
    }

    .identity-name,
    .action-button span {
      display: none;
    }
  }

  @media (max-width: 680px) {
    .command-bar {
      left: 10px;
      right: 10px;
      grid-template-columns: auto minmax(64px, 1fr) auto;
      gap: 6px;
      padding: 7px;
    }

    .identity {
      display: none;
    }

    .tabs {
      justify-self: start;
    }

    .tab {
      padding-right: 8px;
      padding-left: 8px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .tab,
    .action-button,
    .search-region {
      transition-duration: 0ms;
    }
  }
</style>
