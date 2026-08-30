<script lang="ts">
    import type {Module as ClickGuiModule} from "../../../../integration/types";
    import ModernPanelHeader from "./ModernPanelHeader.svelte";
    import ModernPanelModules from "./ModernPanelModules.svelte";
    import type {ModernPanelDragSession} from "./model/modernPanelDragSession.ts";
    import {
        MODERN_PANEL_HEADER_HEIGHT,
        MODERN_PANEL_WIDTH,
        type ModernPanelPosition,
        type ModernPanelState,
    } from "./model/modernPanelState";
    import {MODERN_PANEL_STAGGER_LIMIT, motionStaggerIndex} from "./model/modernMotion";

    let {
        category, modules, panelIndex, panelState, visiblePosition, maximumModulesHeight, moving, resetting,
        resetVersion, scrollTop, dragSession, onResize, onToggle, onScrollTop, onScrollSettled,
    } = $props<{
        category: string;
        modules: ClickGuiModule[];
        panelIndex: number;
        panelState: ModernPanelState;
        visiblePosition: ModernPanelPosition;
        maximumModulesHeight: number;
        moving: boolean;
        resetting: boolean;
        resetVersion: number;
        scrollTop: number;
        dragSession: ModernPanelDragSession;
        onResize: () => void;
        onToggle: () => void;
        onScrollTop: (value: number) => void;
        onScrollSettled: () => void;
    }>();
</script>

<svelte:window
        onpointermove={event => dragSession.move(event)}
        onpointerup={event => dragSession.finish(event)}
        onpointercancel={event => dragSession.finish(event)}
        onblur={() => dragSession.finish()}
        onresize={onResize}
/>
<article
        class="panel"
        class:moving
        class:resetting
        class:expanded={panelState.expanded}
        aria-label="{category} modules"
        style:left="{visiblePosition.left}px"
        style:top="{visiblePosition.top}px"
        style:z-index={panelState.zIndex}
        style:--modern-panel-width="{MODERN_PANEL_WIDTH}px"
        style:--modern-panel-header-height="{MODERN_PANEL_HEADER_HEIGHT}px"
        style:--modern-panel-max-modules-height="{maximumModulesHeight}px"
        style:--modern-panel-enter-index={motionStaggerIndex(panelIndex, MODERN_PANEL_STAGGER_LIMIT)}
>
    <ModernPanelHeader
            {category}
            moduleCount={modules.length}
            {panelState}
            onDragStart={event => dragSession.start(event)}
            onDragFinish={event => dragSession.finish(event)}
            onToggle={onToggle}
    />
    <ModernPanelModules
            {modules}
            {panelState}
            {resetVersion}
            {scrollTop}
            {onScrollTop}
            {onScrollSettled}
    />
</article>
