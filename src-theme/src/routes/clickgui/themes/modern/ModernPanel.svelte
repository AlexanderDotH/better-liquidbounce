<script lang="ts">
    import {onDestroy, onMount, tick} from "svelte";
    import type {Module as ClickGuiModule} from "../../../../integration/types";
    import type {ModuleToggleEvent} from "../../../../integration/events";
    import {setItem} from "../../../../integration/persistent_storage";
    import {listen} from "../../../../integration/ws";
    import {
        gridSize,
        highlightModuleName,
        maxPanelZIndex,
        scaleFactor,
        shiftHeld,
        showGrid,
        snappingEnabled,
    } from "../../clickgui_store";
    import ModernModule from "./ModernModule.svelte";
    import {
        clampModernPanelPosition,
        MODERN_PANEL_CANVAS_PADDING,
        MODERN_PANEL_HEADER_HEIGHT,
        MODERN_PANEL_WIDTH,
        modernPanelStateKey,
        parseModernPanelState,
        snapModernPanelPosition,
        type LogicalViewport,
        type ModernPanelPosition,
        type ModernPanelState,
    } from "./model/modernPanelState";
    import {
        MODERN_LAYOUT_RESET_DURATION_MS,
        MODERN_PANEL_STAGGER_LIMIT,
        motionStaggerIndex,
    } from "./model/modernMotion";
    import {logicalViewportDimension} from "./modernShellState";

    interface Props {
        category: string;
        modules: ClickGuiModule[];
        panelIndex: number;
        initialState: ModernPanelState;
        resetVersion: number;
    }

    interface PendingPointer {
        clientX: number;
        clientY: number;
        shiftKey: boolean;
    }

    let {
        category,
        modules,
        panelIndex,
        initialState,
        resetVersion,
    }: Props = $props();

    const storageKey = $derived(modernPanelStateKey(category));
    let panelState = $state<ModernPanelState>(loadPanelState());
    let modulesElement: HTMLElement;
    let dragCaptureTarget: HTMLElement | null = null;
    let activePointerId: number | null = null;
    let dragOffset: ModernPanelPosition = {left: 0, top: 0};
    let pendingPointer: PendingPointer | null = null;
    let dragFrame: number | null = null;
    let scrollSaveTimeout: number | null = null;
    let scaleChangeFrame: number | null = null;
    let resetAnimationTimeout: number | null = null;
    let observedScaleFactor: number | null = null;
    let observedResetVersion: number | null = null;
    let moving = $state(false);
    let resetting = $state(false);
    let viewport = $state<LogicalViewport>(readLogicalViewport());
    let maximumModulesHeight = $derived(Math.max(
        0,
        viewport.height
        - panelState.top
        - MODERN_PANEL_HEADER_HEIGHT
        - MODERN_PANEL_CANVAS_PADDING,
    ));

    updateMaximumZIndex();
    clampPanelPosition();

    $effect(() => {
        const currentScaleFactor = $scaleFactor;
        if (currentScaleFactor === observedScaleFactor) {
            return;
        }

        observedScaleFactor = currentScaleFactor;
        if (scaleChangeFrame !== null) {
            cancelAnimationFrame(scaleChangeFrame);
        }

        scaleChangeFrame = requestAnimationFrame(() => {
            scaleChangeFrame = null;
            handleWindowResize();
        });
    });

    $effect(() => {
        const requestedResetVersion = resetVersion;
        const nextInitialState = initialState;
        if (observedResetVersion === null) {
            observedResetVersion = requestedResetVersion;
            return;
        }

        if (requestedResetVersion === observedResetVersion) {
            return;
        }

        observedResetVersion = requestedResetVersion;
        resetPanelState(nextInitialState);
    });

    listen("moduleToggle", (event: ModuleToggleEvent) => {
        const module = modules.find(candidate => candidate.name === event.moduleName);
        if (module) {
            module.enabled = event.enabled;
        }
    });

    const unsubscribeHighlight = highlightModuleName.subscribe(name => {
        if (!name || !modules.some(module => module.name === name)) {
            return;
        }

        panelState.expanded = true;
        bringToFront();
        savePanelState();
    });

    onMount(async () => {
        await tick();
        modulesElement.scrollTop = panelState.scrollTop;
    });

    onDestroy(() => {
        unsubscribeHighlight();
        cancelDragFrame();
        releasePointerCapture();
        moving = false;
        $showGrid = false;

        if (scrollSaveTimeout !== null) {
            clearTimeout(scrollSaveTimeout);
        }

        if (scaleChangeFrame !== null) {
            cancelAnimationFrame(scaleChangeFrame);
        }

        if (resetAnimationTimeout !== null) {
            clearTimeout(resetAnimationTimeout);
        }

    });

    function loadPanelState(): ModernPanelState {
        return parseModernPanelState(
            localStorage.getItem(storageKey),
            initialState,
        );
    }

    function resetPanelState(nextInitialState: ModernPanelState): void {
        cancelDragFrame();
        const pointerId = activePointerId;
        activePointerId = null;
        moving = false;
        $showGrid = false;
        releasePointerCapture(pointerId);

        if (scrollSaveTimeout !== null) {
            clearTimeout(scrollSaveTimeout);
            scrollSaveTimeout = null;
        }

        if (resetAnimationTimeout !== null) {
            clearTimeout(resetAnimationTimeout);
        }

        resetting = true;
        panelState = {...nextInitialState};
        updateMaximumZIndex();
        resetAnimationTimeout = window.setTimeout(() => {
            resetAnimationTimeout = null;
            resetting = false;
        }, MODERN_LAYOUT_RESET_DURATION_MS);

        void tick().then(() => {
            if (modulesElement) {
                modulesElement.scrollTop = 0;
            }
        });
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

    function startDrag(event: PointerEvent): void {
        const target = event.target as HTMLElement;
        if (
            moving
            || !event.isPrimary
            || event.button !== 0
            || target.closest("button")
        ) {
            return;
        }

        event.preventDefault();
        const logicalPointer = toLogicalPosition(event.clientX, event.clientY);

        activePointerId = event.pointerId;
        dragOffset = {
            left: logicalPointer.left - panelState.left,
            top: logicalPointer.top - panelState.top,
        };
        dragCaptureTarget = event.currentTarget as HTMLElement;
        dragCaptureTarget.setPointerCapture(event.pointerId);
        moving = true;
        bringToFront();
        updateGridVisibility(event.shiftKey);
    }

    function moveDrag(event: PointerEvent): void {
        if (!moving || event.pointerId !== activePointerId) {
            return;
        }

        pendingPointer = {
            clientX: event.clientX,
            clientY: event.clientY,
            shiftKey: event.shiftKey,
        };

        if (dragFrame !== null) {
            return;
        }

        dragFrame = requestAnimationFrame(applyPendingDrag);
    }

    function applyPendingDrag(): void {
        dragFrame = null;
        const pointer = pendingPointer;
        pendingPointer = null;

        if (!pointer || !moving) {
            return;
        }

        const logicalPointer = toLogicalPosition(pointer.clientX, pointer.clientY);
        const rawPosition = {
            left: logicalPointer.left - dragOffset.left,
            top: logicalPointer.top - dragOffset.top,
        };
        const snappedPosition = snapModernPanelPosition(rawPosition, {
            gridSize: $gridSize,
            snappingEnabled: $snappingEnabled,
            shiftHeld: pointer.shiftKey || $shiftHeld,
        });

        setPanelPosition(clampModernPanelPosition(
            snappedPosition,
            viewport,
        ));
        updateGridVisibility(pointer.shiftKey);
    }

    function finishDrag(event?: PointerEvent): void {
        if (!moving || (event && event.pointerId !== activePointerId)) {
            return;
        }

        cancelDragFrame(true);
        const completedPointerId = activePointerId;
        activePointerId = null;
        moving = false;
        $showGrid = false;
        releasePointerCapture(completedPointerId);
        savePanelState();
    }

    function cancelDragFrame(applyPending = false): void {
        if (dragFrame !== null) {
            cancelAnimationFrame(dragFrame);
            dragFrame = null;
        }

        if (applyPending) {
            applyPendingDrag();
            return;
        }

        pendingPointer = null;
    }

    function releasePointerCapture(pointerId = activePointerId): void {
        if (
            dragCaptureTarget
            && pointerId !== null
            && dragCaptureTarget.hasPointerCapture(pointerId)
        ) {
            dragCaptureTarget.releasePointerCapture(pointerId);
        }

        dragCaptureTarget = null;
    }

    function toggleExpanded(): void {
        panelState.expanded = !panelState.expanded;
        savePanelState();
    }

    function handleHeaderContextMenu(event: MouseEvent): void {
        event.preventDefault();
        const target = event.target as HTMLElement;
        if (!target.closest("button")) {
            toggleExpanded();
        }
    }

    function handleModulesScroll(): void {
        panelState.scrollTop = modulesElement.scrollTop;

        if (scrollSaveTimeout !== null) {
            clearTimeout(scrollSaveTimeout);
        }

        scrollSaveTimeout = window.setTimeout(() => {
            scrollSaveTimeout = null;
            savePanelState();
        }, 300);
    }

    function handleWindowResize(): void {
        viewport = readLogicalViewport();
        const previousLeft = panelState.left;
        const previousTop = panelState.top;
        clampPanelPosition();

        if (previousLeft !== panelState.left || previousTop !== panelState.top) {
            savePanelState();
        }
    }

    function clampPanelPosition(): void {
        setPanelPosition(clampModernPanelPosition(
            panelState,
            viewport,
        ));
    }

    function setPanelPosition(position: ModernPanelPosition): void {
        panelState.left = position.left;
        panelState.top = position.top;
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
        void setItem(storageKey, JSON.stringify($state.snapshot(panelState)))
            .catch(error => {
                console.warn(`Failed to persist Modern panel "${category}"`, error);
            });
    }
</script>

<svelte:window
        onpointermove={moveDrag}
        onpointerup={finishDrag}
        onpointercancel={finishDrag}
        onblur={() => finishDrag()}
        onresize={handleWindowResize}
/>

<article
        class="panel"
        class:moving
        class:resetting
        class:expanded={panelState.expanded}
        aria-label="{category} modules"
        style:left="{panelState.left}px"
        style:top="{panelState.top}px"
        style:z-index={panelState.zIndex}
        style:--modern-panel-width="{MODERN_PANEL_WIDTH}px"
        style:--modern-panel-header-height="{MODERN_PANEL_HEADER_HEIGHT}px"
        style:--modern-panel-max-modules-height="{maximumModulesHeight}px"
        style:--modern-panel-enter-index={motionStaggerIndex(panelIndex, MODERN_PANEL_STAGGER_LIMIT)}
>
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <header
            class="header"
            onpointerdown={startDrag}
            onlostpointercapture={finishDrag}
            oncontextmenu={handleHeaderContextMenu}
    >
        <span class="category-icon" aria-hidden="true">
            <img
                    src="img/clickgui/icon-{category.toLowerCase()}.svg"
                    alt=""
                    draggable="false"
            />
        </span>

        <span class="heading">
            <strong>{category}</strong>
            <span>{modules.length} {modules.length === 1 ? "module" : "modules"}</span>
        </span>

        <button
                class="expand-toggle"
                class:expanded={panelState.expanded}
                type="button"
                aria-label="{panelState.expanded ? "Collapse" : "Expand"} {category}"
                aria-expanded={panelState.expanded}
                onclick={toggleExpanded}
        >
            <svg aria-hidden="true" viewBox="0 0 16 16">
                <path d="m4.1 6 3.9 4 3.9-4"/>
            </svg>
        </button>
    </header>

    <div
            class="modules"
            class:expanded={panelState.expanded}
            aria-hidden={!panelState.expanded}
            inert={!panelState.expanded}
            bind:this={modulesElement}
            onscroll={handleModulesScroll}
    >
        {#each modules as {name, enabled, description, aliases}, moduleIndex (name)}
            <ModernModule
                    {name}
                    {enabled}
                    {description}
                    {aliases}
                    {moduleIndex}
                    revealed={panelState.expanded}
            />
        {/each}
    </div>
</article>

<style lang="scss">
  .panel {
    position: absolute;
    width: var(--modern-panel-width, 288px);
    overflow: hidden;
    color: var(--modern-text-primary, #f2f4f7);
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.025), transparent 76px),
      var(--modern-surface-panel, rgba(17, 20, 26, 0.97));
    border: 1px solid var(--modern-border, rgba(255, 255, 255, 0.1));
    border-radius: var(--modern-panel-radius, 12px);
    box-shadow:
      0 16px 38px rgba(0, 0, 0, 0.3),
      inset 0 1px rgba(255, 255, 255, 0.035);
    contain: layout paint style;
    user-select: none;
    -webkit-user-select: none;
    animation:
      modern-panel-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
    animation-delay:
      calc(
        var(--modern-panel-enter-index, 0)
        * var(--modern-motion-stagger, 24ms)
      );
    transition:
      transform
      var(--modern-motion-fast, 100ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      border-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      box-shadow
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .panel.moving {
    transform: translateY(-2px);
    border-color: var(--modern-border-strong, rgba(255, 255, 255, 0.13));
    box-shadow:
      0 20px 46px rgba(0, 0, 0, 0.36),
      inset 0 1px rgba(255, 255, 255, 0.04);
  }

  .panel.resetting {
    transition:
      left
      var(--modern-motion-layout-duration, 360ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1)),
      top
      var(--modern-motion-layout-duration, 360ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1)),
      transform
      var(--modern-motion-fast, 100ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      border-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      box-shadow
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .header {
    position: relative;
    height: var(--modern-panel-header-height, 44px);
    display: grid;
    grid-template-columns: 28px minmax(0, 1fr) 30px;
    align-items: center;
    gap: 9px;
    padding: 0 7px 0 10px;
    cursor: grab;
    touch-action: none;
    background: var(--modern-surface-panel-header, rgba(255, 255, 255, 0.035));
    border-bottom: 1px solid transparent;
    overflow: hidden;
    transition:
      background-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      border-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .header::after {
    position: absolute;
    z-index: 0;
    inset: 0;
    content: "";
    background: linear-gradient(
      90deg,
      transparent,
      color-mix(in srgb, var(--accent-color) 13%, white 2%),
      transparent
    );
    opacity: 0;
    pointer-events: none;
    transform: translateX(-110%);
  }

  .header > * {
    position: relative;
    z-index: 1;
  }

  .panel.expanded .header::after {
    animation:
      modern-panel-expand-sweep
      440ms
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1));
  }

  .panel.expanded .header {
    border-bottom-color: var(--modern-divider, rgba(255, 255, 255, 0.075));
  }

  .moving .header {
    cursor: grabbing;
    background: color-mix(in srgb, var(--accent-color) 7%, var(--modern-surface-panel-header));
  }

  .category-icon {
    width: 27px;
    height: 27px;
    display: grid;
    place-items: center;
    background: color-mix(in srgb, var(--accent-color) 10%, rgba(255, 255, 255, 0.035));
    border: 1px solid color-mix(in srgb, var(--accent-color) 20%, rgba(255, 255, 255, 0.065));
    border-radius: 7px;
  }

  .category-icon img {
    width: 14px;
    height: 14px;
    opacity: 0.9;
    pointer-events: none;
  }

  .heading {
    min-width: 0;
    display: grid;
    gap: 1px;
  }

  .heading strong {
    overflow: hidden;
    color: var(--modern-text-primary, #f2f4f7);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: -0.01em;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .heading span {
    color: var(--modern-text-muted, #8d96a3);
    font-size: 9px;
    font-weight: 500;
  }

  .expand-toggle {
    width: 29px;
    height: 29px;
    display: grid;
    place-items: center;
    color: var(--modern-text-secondary, #aeb5bf);
    background: transparent;
    border: 1px solid transparent;
    border-radius: 7px;
    cursor: pointer;
    transition:
      color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      background-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      border-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease);
  }

  .expand-toggle:hover {
    color: var(--modern-text-primary, #f2f4f7);
    background: rgba(255, 255, 255, 0.055);
    border-color: rgba(255, 255, 255, 0.075);
  }

  .expand-toggle:focus-visible {
    outline: 2px solid var(--modern-focus-ring, color-mix(in srgb, var(--accent-color) 80%, white));
    outline-offset: 1px;
  }

  .expand-toggle svg {
    width: 14px;
    height: 14px;
    fill: none;
    stroke: currentColor;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.6;
  }

  .expand-toggle.expanded svg {
    transform: rotate(180deg);
  }

  .modules {
    max-height: 0;
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior: contain;
    opacity: 0;
    background: var(--modern-surface-panel-body, rgba(8, 10, 14, 0.2));
  }

  .modules.expanded {
    max-height: min(540px, var(--modern-panel-max-modules-height, 540px));
    opacity: 1;
  }

  .modules::-webkit-scrollbar {
    width: 6px;
  }

  .modules::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.11);
    border-radius: 999px;
  }

  @keyframes modern-panel-enter {
    from {
      transform: translateY(10px);
    }
  }

  @keyframes modern-panel-expand-sweep {
    from {
      opacity: 0;
      transform: translateX(-110%);
    }

    48% {
      opacity: 0.72;
    }

    to {
      opacity: 0;
      transform: translateX(110%);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .panel,
    .header,
    .expand-toggle,
    .expand-toggle svg,
    .modules {
      transition-duration: 0ms;
    }

    .panel {
      animation: none;
    }

    .panel.resetting {
      transition-duration: 0ms;
    }

    .panel.expanded .header::after {
      animation: none;
    }
  }
</style>
