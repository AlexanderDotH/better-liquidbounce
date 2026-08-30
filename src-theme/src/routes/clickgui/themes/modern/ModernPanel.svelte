<script lang="ts">
    import {onDestroy} from "svelte";
    import type {Module as ClickGuiModule} from "../../../../integration/types";
    import {setItem} from "../../../../integration/persistent_storage";
    import {
        gridSize,
        maxPanelZIndex,
        scaleFactor,
        shiftHeld,
        showGrid,
        snappingEnabled,
    } from "../../clickgui_store";
    import ModernPanelEffects from "./ModernPanelEffects.svelte";
    import ModernPanelView from "./ModernPanelView.svelte";
    import {
        clampModernPanelPosition,
        MODERN_PANEL_CANVAS_PADDING,
        MODERN_PANEL_HEADER_HEIGHT,
        modernPanelStateKey,
        parseModernPanelState,
        snapModernPanelPosition,
        type LogicalViewport,
        type ModernPanelPosition,
        type ModernPanelState,
    } from "./model/modernPanelState";
    import {MODERN_LAYOUT_RESET_DURATION_MS} from "./model/modernMotion";
    import {ModernPanelDragSession} from "./model/modernPanelDragSession.ts";
    import {logicalViewportDimension} from "./modernShellState";
    import "./ModernPanel.styles.scss";

    interface Props {
        category: string;
        modules: ClickGuiModule[];
        panelIndex: number;
        initialState: ModernPanelState;
        resetVersion: number;
    }

    let {
        category,
        modules,
        panelIndex,
        initialState,
        resetVersion,
    }: Props = $props();

    const storageKey = $derived(modernPanelStateKey(category));
    const loadedPanelState = loadPanelState();
    const initialViewport = readLogicalViewport();
    let panelState = $state<ModernPanelState>(loadedPanelState);
    let currentScrollTop = loadedPanelState.scrollTop;
    let viewport = $state<LogicalViewport>(initialViewport);
    let visiblePanelPosition = $state<ModernPanelPosition>(
        clampModernPanelPosition(loadedPanelState, initialViewport),
    );
    let resetAnimationTimeout: number | null = null;
    let moving = $state(false);
    let resetting = $state(false);
    let maximumModulesHeight = $derived(Math.max(
        0,
        viewport.height
        - visiblePanelPosition.top
        - MODERN_PANEL_HEADER_HEIGHT
        - MODERN_PANEL_CANVAS_PADDING,
    ));

    updateMaximumZIndex();
    const dragSession = new ModernPanelDragSession({
        isMoving: () => moving,
        setMoving: value => moving = value,
        visiblePosition: () => visiblePanelPosition,
        toLogicalPosition,
        constrain: (position, pointerShiftHeld) => clampModernPanelPosition(
            snapModernPanelPosition(position, {
                gridSize: $gridSize,
                snappingEnabled: $snappingEnabled,
                shiftHeld: pointerShiftHeld || $shiftHeld,
            }),
            viewport,
        ),
        setPosition: setPanelPosition,
        bringToFront,
        updateGridVisibility,
        save: savePanelState,
    });

    function handleHighlight(): void {
        panelState.expanded = true;
        bringToFront();
        savePanelState();
    }

    onDestroy(() => {
        dragSession.destroy();
        moving = false;
        $showGrid = false;
        if (resetAnimationTimeout !== null) clearTimeout(resetAnimationTimeout);
    });

    function loadPanelState(): ModernPanelState {
        return parseModernPanelState(
            localStorage.getItem(storageKey),
            initialState,
        );
    }

    function resetPanelState(nextInitialState: ModernPanelState): void {
        dragSession.reset();

        currentScrollTop = nextInitialState.scrollTop;
        if (resetAnimationTimeout !== null) clearTimeout(resetAnimationTimeout);

        resetting = true;
        panelState = {...nextInitialState};
        syncVisiblePanelPosition();
        updateMaximumZIndex();
        resetAnimationTimeout = window.setTimeout(() => {
            resetAnimationTimeout = null;
            resetting = false;
        }, MODERN_LAYOUT_RESET_DURATION_MS);

    }

    function updateMaximumZIndex(): void {
        if (panelState.zIndex > $maxPanelZIndex) {
            $maxPanelZIndex = panelState.zIndex;
        }
    }

    function bringToFront(): void {
        const nextZIndex = Math.max($maxPanelZIndex, panelState.zIndex) + 1;
        $maxPanelZIndex = nextZIndex;
        panelState.zIndex = nextZIndex;
    }

    function toggleExpanded(): void {
        panelState.expanded = !panelState.expanded;
        savePanelState();
    }

    function handleWindowResize(): void {
        viewport = readLogicalViewport();
        syncVisiblePanelPosition();
    }

    function syncVisiblePanelPosition(): void {
        visiblePanelPosition = clampModernPanelPosition(
            panelState,
            viewport,
        );
    }

    function setPanelPosition(position: ModernPanelPosition): void {
        panelState.left = position.left;
        panelState.top = position.top;
        visiblePanelPosition.left = position.left;
        visiblePanelPosition.top = position.top;
    }

    function toLogicalPosition(clientX: number, clientY: number): ModernPanelPosition {
        return {
            left: logicalViewportDimension(clientX, $scaleFactor),
            top: logicalViewportDimension(clientY, $scaleFactor),
        };
    }

    function readLogicalViewport(): LogicalViewport {
        return {
            width: logicalViewportDimension(window.innerWidth, $scaleFactor),
            height: logicalViewportDimension(window.innerHeight, $scaleFactor),
        };
    }

    function updateGridVisibility(pointerShiftHeld: boolean): void {
        $showGrid = $snappingEnabled && !(pointerShiftHeld || $shiftHeld);
    }

    function savePanelState(): void {
        const state = {
            ...$state.snapshot(panelState),
            scrollTop: currentScrollTop,
        };

        void setItem(storageKey, JSON.stringify(state))
            .catch(error => {
                console.warn(`Failed to persist Modern panel "${category}"`, error);
            });
    }
</script>

<ModernPanelEffects
        scaleFactor={$scaleFactor}
        {resetVersion}
        {initialState}
        {modules}
        onScaleChange={handleWindowResize}
        onReset={resetPanelState}
        onHighlight={handleHighlight}
/>
<ModernPanelView
        {category}
        {modules}
        {panelIndex}
        {panelState}
        visiblePosition={visiblePanelPosition}
        {maximumModulesHeight}
        {moving}
        {resetting}
        {resetVersion}
        scrollTop={panelState.scrollTop}
        {dragSession}
        onResize={handleWindowResize}
        onToggle={toggleExpanded}
        onScrollTop={value => currentScrollTop = value}
        onScrollSettled={savePanelState}
/>
