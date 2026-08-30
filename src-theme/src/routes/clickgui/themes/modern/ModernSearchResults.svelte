<script lang="ts">
    import type {Module} from "../../../../integration/types";
    import {MODERN_RESULT_STAGGER_LIMIT, motionStaggerIndex} from "./model/modernMotion";
    import {modernSearchDisplayName} from "./model/modernSearchRuntime.ts";

    let {query, modules, selectedIndex, pendingNames, spacedNames, onSelect, onHover, onLocate} = $props<{
        query: string;
        modules: Module[];
        selectedIndex: number;
        pendingNames: string[];
        spacedNames: boolean;
        onSelect: (module: Module) => void;
        onHover: (index: number) => void;
        onLocate: (name: string) => void;
    }>();
    const displayName = (value: string) => modernSearchDisplayName(value, spacedNames);
</script>

<div id="modern-search-results" class="results" role="listbox" aria-label="Module search results">
    {#if modules.length > 0}
        {#each modules as module, index (module.name)}
            <button
                    id="modern-search-result-{index}"
                    class="result"
                    class:selected={selectedIndex === index}
                    class:enabled={module.enabled}
                    type="button"
                    role="option"
                    tabindex="-1"
                    aria-selected={selectedIndex === index}
                    aria-busy={pendingNames.includes(module.name)}
                    disabled={pendingNames.includes(module.name)}
                    style:--modern-result-enter-index={motionStaggerIndex(index, MODERN_RESULT_STAGGER_LIMIT)}
                    onclick={() => onSelect(module)}
                    onmouseenter={() => onHover(index)}
                    oncontextmenu={event => {
                        event.preventDefault();
                        onLocate(module.name);
                    }}
            >
                <span class="result-state" aria-hidden="true"></span>
                <span class="result-copy">
                    <strong>{displayName(module.name)}</strong>
                    {#if module.aliases.length > 0}<span>{module.aliases.map(displayName).join(", ")}</span>{/if}
                </span>
                <span class="result-action">{module.enabled ? "On" : "Off"}</span>
            </button>
        {/each}
    {:else}
        <div class="empty-result" role="status">No modules match “{query}”</div>
    {/if}
    <div class="result-footer" aria-hidden="true"><span>↑↓ Select</span><span>Enter Toggle</span><span>Tab Locate</span></div>
</div>
