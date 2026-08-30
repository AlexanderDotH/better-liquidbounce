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

    function handleTabKeydown(event: KeyboardEvent, currentView: ClickGuiView): void {
        const nextView = keyboardView(event.key, currentView);
        if (!nextView) {
            return;
        }

        event.preventDefault();
        onViewChange(nextView);

        requestAnimationFrame(() => {
            document.getElementById(`modern-command-tab-${nextView}`)?.focus();
        });
    }

    function keyboardView(key: string, currentView: ClickGuiView): ClickGuiView | null {
        if (key === "Home") {
            return "clickgui";
        }

        if (key === "End") {
            return "settings";
        }

        if (key !== "ArrowLeft" && key !== "ArrowRight") {
            return null;
        }

        return moveClickGuiView(currentView, key === "ArrowRight" ? 1 : -1);
    }
</script>

<div class="command-dock">
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
                        tabindex={view === tab.view || (view === "hud-editor" && tab.view === "clickgui") ? 0 : -1}
                        onclick={() => onViewChange(tab.view)}
                        onkeydown={event => handleTabKeydown(event, tab.view)}
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

    <button
            id="modern-command-hud-editor"
            class="hud-editor-island"
            class:active={view === "hud-editor"}
            type="button"
            aria-pressed={view === "hud-editor"}
            aria-controls="modern-hud-editor-view"
            title="Open HUD editor"
            onclick={() => onViewChange("hud-editor")}
    >
        <svg aria-hidden="true" viewBox="0 0 20 20">
            <path d="M3 3h6v6H3V3Zm8 0h6v4h-6V3ZM3 11h4v6H3v-6Zm6-2h8v8H9V9Zm2 2v4h4v-4h-4Z"/>
        </svg>
        <span class="hud-editor-label">HUD editor</span>
    </button>
</div>

<style lang="scss">
  @use "./ModernCommandBar.styles";
</style>
