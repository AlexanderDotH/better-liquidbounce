<script lang="ts">
    import type {ModernPanelState} from "./model/modernPanelState";

    let {category, moduleCount, panelState, onDragStart, onDragFinish, onToggle} = $props<{
        category: string;
        moduleCount: number;
        panelState: ModernPanelState;
        onDragStart: (event: PointerEvent) => void;
        onDragFinish: (event: PointerEvent) => void;
        onToggle: () => void;
    }>();

    function handleContextMenu(event: MouseEvent): void {
        event.preventDefault();
        if (!(event.target as HTMLElement).closest("button")) onToggle();
    }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<header class="header" onpointerdown={onDragStart} onlostpointercapture={onDragFinish} oncontextmenu={handleContextMenu}>
    <span class="category-icon" aria-hidden="true">
        <img src="img/clickgui/icon-{category.toLowerCase()}.svg" alt="" draggable="false"/>
    </span>
    <span class="heading">
        <strong>{category}</strong><span>{moduleCount} {moduleCount === 1 ? "module" : "modules"}</span>
    </span>
    <button
            class="expand-toggle"
            class:expanded={panelState.expanded}
            type="button"
            aria-label="{panelState.expanded ? "Collapse" : "Expand"} {category}"
            aria-expanded={panelState.expanded}
            onclick={onToggle}
    >
        <svg aria-hidden="true" viewBox="0 0 16 16"><path d="m4.1 6 3.9 4 3.9-4"/></svg>
    </button>
</header>
