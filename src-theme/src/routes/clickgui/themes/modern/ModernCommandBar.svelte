<script lang="ts">
    import type {Snippet} from "svelte";
    import type {ClickGuiView} from "../../theme/clickGuiThemeState";
    import {moveClickGuiView} from "./modernShellState";

    interface Props {
        view: ClickGuiView;
        busy?: boolean;
        resetVersion: number;
        search?: Snippet;
        onViewChange: (view: ClickGuiView) => void;
        onResetLayout: () => void;
    }

    let {
        view,
        busy = false,
        resetVersion,
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
    <span class="command-bar-sheen" aria-hidden="true"></span>

    <div class="identity" aria-label="LiquidBounce">
        <span class="identity-mark" aria-hidden="true">L</span>
        <span class="identity-name">LiquidBounce</span>
    </div>

    <div
            class="tabs"
            class:settings-active={view === "settings"}
            role="tablist"
            aria-label="ClickGUI views"
    >
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
                {#key resetVersion}
                    <svg
                            class="reset-icon"
                            class:resetting={resetVersion > 0}
                            aria-hidden="true"
                            viewBox="0 0 20 20"
                    >
                        <path d="M15.9 5.2A7 7 0 1 0 17 11h-1.7a5.3 5.3 0 1 1-.8-2.8l-2.2.1V10H18V4.3h-1.7l-.4.9Z"/>
                    </svg>
                {/key}
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
    right: 0;
    left: 0;
    width: min(960px, calc(100% - 32px));
    min-height: 52px;
    margin-inline: auto;
    display: grid;
    grid-template-columns: minmax(150px, 1fr) auto minmax(240px, 1.4fr) minmax(150px, 1fr);
    align-items: center;
    gap: 14px;
    padding: 7px 9px;
    color: var(--modern-text-primary, #f4f6f8);
    background: var(--modern-surface-command, rgba(15, 18, 23, 0.96));
    border: 1px solid var(--modern-border, rgba(255, 255, 255, 0.1));
    border-radius: 999px;
    box-shadow: 0 10px 28px rgba(0, 0, 0, 0.24);
    overflow: visible;
    animation:
      modern-command-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
  }

  .command-bar-sheen {
    position: absolute;
    z-index: 0;
    inset: 0;
    overflow: hidden;
    border-radius: inherit;
    pointer-events: none;
  }

  .command-bar-sheen::after {
    position: absolute;
    top: -40%;
    bottom: -40%;
    left: 0;
    width: 34%;
    content: "";
    background: linear-gradient(
      90deg,
      transparent,
      color-mix(in srgb, var(--accent-color) 18%, white 3%),
      transparent
    );
    opacity: 0;
    transform: translateX(380%) skewX(-14deg);
    animation:
      modern-command-sheen
      720ms
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      140ms
      backwards;
  }

  .identity {
    position: relative;
    z-index: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 9px;
    padding-left: 3px;
    animation:
      modern-command-item-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      30ms
      backwards;
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
    position: relative;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: center;
    padding: 3px;
    background: rgba(255, 255, 255, 0.045);
    border: 1px solid rgba(255, 255, 255, 0.07);
    border-radius: 9px;
    z-index: 1;
    isolation: isolate;
    animation:
      modern-command-item-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      54ms
      backwards;
  }

  .tabs::before {
    position: absolute;
    z-index: 0;
    top: 3px;
    bottom: 3px;
    left: 3px;
    width: calc((100% - 6px) / 2);
    content: "";
    background: color-mix(in srgb, var(--accent-color) 14%, rgba(255, 255, 255, 0.055));
    border: 1px solid color-mix(in srgb, var(--accent-color) 36%, transparent);
    border-radius: 7px;
    transform: translateX(0);
  }

  .tabs.settings-active::before {
    transform: translateX(100%);
  }

  .tab,
  .action-button {
    font-family: inherit;
  }

  .tab {
    position: relative;
    z-index: 1;
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
    background: transparent;
    border-color: transparent;
  }

  .search-region {
    position: relative;
    z-index: 1;
    min-width: 0;
    animation:
      modern-command-item-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      78ms
      backwards;
  }

  .search-region.hidden {
    visibility: hidden;
    pointer-events: none;
  }

  .actions {
    position: relative;
    z-index: 1;
    display: flex;
    justify-content: flex-end;
    animation:
      modern-command-item-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      102ms
      backwards;
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
      border-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      transform var(--modern-motion-fast, 100ms) var(--modern-motion-easing, ease);
  }

  .action-button:hover:not(:disabled) {
    color: #f5f7fa;
    background: rgba(255, 255, 255, 0.075);
    border-color: rgba(255, 255, 255, 0.13);
  }

  .action-button:active:not(:disabled) {
    transform: translateY(1px);
  }

  .action-button:disabled {
    cursor: default;
    opacity: 0.5;
  }

  .action-button svg {
    width: 14px;
    height: 14px;
    fill: currentColor;
    transition:
      transform
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .action-button:active:not(:disabled) svg {
    transform: rotate(-90deg);
  }

  .reset-icon.resetting {
    animation:
      modern-reset-confirm
      var(--modern-motion-layout-duration, 360ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1));
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
      width: calc(100% - 20px);
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

  @keyframes modern-command-enter {
    from {
      transform: translateY(-8px);
    }
  }

  @keyframes modern-command-item-enter {
    from {
      transform: translateY(-3px);
    }
  }

  @keyframes modern-command-sheen {
    0% {
      opacity: 0;
      transform: translateX(-120%) skewX(-14deg);
    }

    42% {
      opacity: 0.72;
    }

    100% {
      opacity: 0;
      transform: translateX(380%) skewX(-14deg);
    }
  }

  @keyframes modern-reset-confirm {
    from {
      transform: rotate(0);
    }

    to {
      transform: rotate(-360deg);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .tab,
    .action-button,
    .search-region,
    .tabs::before,
    .action-button svg {
      transition-duration: 0ms;
    }

    .command-bar-sheen::after,
    .reset-icon.resetting,
    .command-bar,
    .identity,
    .tabs,
    .search-region,
    .actions {
      animation: none;
    }
  }
</style>
