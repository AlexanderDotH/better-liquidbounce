<script lang="ts">
    import {BARITONE_TABS, type BaritoneTabId} from "../../integration/baritone";
    import {nextBaritoneTabIndex} from "./baritoneDashboardPresentation.ts";

    let {activeTab, stateRevision, routeRevision, onSelect} = $props<{
        activeTab: BaritoneTabId;
        stateRevision: number;
        routeRevision: number;
        onSelect: (tab: BaritoneTabId) => void;
    }>();

    function handleKeydown(event: KeyboardEvent, index: number): void {
        const nextIndex = nextBaritoneTabIndex(event.key, index, BARITONE_TABS.length - 1);
        if (nextIndex === null) return;
        event.preventDefault();
        const nextTab = BARITONE_TABS[nextIndex];
        onSelect(nextTab.id);
        requestAnimationFrame(() => document.getElementById(`baritone-tab-${nextTab.id}`)?.focus());
    }
</script>

<nav class="tab-rail" aria-label="Baritone workflows">
    <div role="tablist" aria-orientation="vertical">
        {#each BARITONE_TABS as tab, index (tab.id)}
            <button
                    id={`baritone-tab-${tab.id}`}
                    type="button"
                    role="tab"
                    aria-selected={activeTab === tab.id}
                    aria-controls="baritone-active-panel"
                    tabindex={activeTab === tab.id ? 0 : -1}
                    class:active={activeTab === tab.id}
                    onclick={() => onSelect(tab.id)}
                    onkeydown={event => handleKeydown(event, index)}
            >
                <span class="tab-icon" aria-hidden="true">{index + 1}</span>
                <span>{tab.label}</span>
            </button>
        {/each}
    </div>
    <div class="rail-footer"><span>State rev. {stateRevision}</span><span>Route rev. {routeRevision}</span></div>
</nav>
