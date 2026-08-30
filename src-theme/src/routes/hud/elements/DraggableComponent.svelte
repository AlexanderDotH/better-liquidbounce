<script lang="ts">
    import {getContext, onMount, tick} from "svelte";

    import type {KeyboardKeyEvent, ScaleFactorChangeEvent} from "../../../integration/events";
    import {bringComponentToFront, getGameWindow, setComponentAlignment} from "../../../integration/rest";
    import type {Alignment} from "../../../integration/types.js";
    import {listen} from "../../../integration/ws";
    import ComponentSettings from "../../../shared/hud-editor/HudComponentSettings.svelte";
    import {
        HUD_EDITOR_ELEMENTS_CONTEXT,
        type HudEditorDragState,
    } from "../../../shared/hud-editor/HudEditorContracts";
    import {fade, type TransitionConfig} from "svelte/transition";
    import {
        generateAlignmentStyle,
    } from "./draggableAlignmentModel.ts";
    import {
        hudEditorDragState,
        type DragFeedback,
    } from "./draggableInteractionModel.ts";
    import {
        beginDomDrag,
        changedDragFeedback,
        elementDisplayPosition,
        moveDomDrag,
    } from "./draggableDomInteraction.ts";

    export let alignment: Alignment;
    export let componentId: string;
    export let componentName: string;
    export let inEditor: boolean;
    export let onDragStateChange: ((state: HudEditorDragState) => void) | undefined = undefined;
    export let magneticallyReferenced = false;
    export let width: number | undefined = undefined;
    export let height: number | undefined = undefined;
    export let zIndex = 0;

    let scaleFactor = 2;
    let element: HTMLElement | undefined;
    let isDragging = false;
    let isGridIgnored = false;
    let pointerCenterOffsetX = 0;
    let pointerCenterOffsetY = 0;
    let dragFeedback: DragFeedback = {
        horizontalZone: "left",
        verticalZone: "upper",
    };
    let displayedZIndex = zIndex;

    let displayPosition = {
        x: 0,
        y: 0
    };
    let positionOnTop = false;

    const POSITION_OVERLAY_OFFSET = 19;

    const editorElements = getContext<Map<string, HTMLElement>>(HUD_EDITOR_ELEMENTS_CONTEXT);

    $: styleString = generateAlignmentStyle(alignment);
    $: displayedZIndex = zIndex;
    $: sizeStyleString = (width !== undefined && height !== undefined)
        ? `width: ${width}px; height: ${height}px;`
        : "";

    function emitDragState(dragging: boolean): void {
        onDragStateChange?.(hudEditorDragState(dragFeedback, dragging));
    }

    function onMouseDown(event: MouseEvent): void {
        if (inEditor && event.button === 0) {
            updateZIndex();
        }

        if (event.button !== 0 && event.button !== 1) {
            return;
        }

        isDragging = true;
        const started = beginDomDrag(alignment, event, {element, scaleFactor});
        pointerCenterOffsetX = started.pointerCenterOffsetX;
        pointerCenterOffsetY = started.pointerCenterOffsetY;
        dragFeedback = {
            horizontalZone: started.horizontalZone,
            verticalZone: started.verticalZone,
        };
        updateDisplayedPosition();
        emitDragState(true);
    }

    async function updateZIndex(): Promise<void> {
        displayedZIndex = await bringComponentToFront(componentId);
    }

    async function updateDisplayedPosition(): Promise<void> {
        await tick();

        if (!element) {
            return;
        }

        const displayed = elementDisplayPosition(element);
        displayPosition = displayed.position;
        positionOnTop = displayed.onTop;
    }

    function onMouseMove(event: MouseEvent): void {
        if (!isDragging) {
            return;
        }

        const {moved, horizontalSnap, verticalSnap} = moveDomDrag({
            event,
            element,
            scaleFactor,
            editorElements,
            componentId,
            pointerCenterOffsetX,
            pointerCenterOffsetY,
            gridIgnored: isGridIgnored,
        });
        Object.assign(alignment, moved.alignment);
        const nextFeedback = changedDragFeedback(dragFeedback, moved, horizontalSnap, verticalSnap);
        if (nextFeedback) {
            dragFeedback = nextFeedback;
            emitDragState(true);
        }
        updateDisplayedPosition();
    }

    function onMouseUp(): void {
        if (!isDragging) {
            return;
        }

        isDragging = false;
        dragFeedback = {
            horizontalZone: dragFeedback.horizontalZone,
            verticalZone: dragFeedback.verticalZone,
        };
        emitDragState(false);
        setComponentAlignment(componentId, alignment);
    }

    function editorFade(node: Element): TransitionConfig {
        return fade(node, {
            duration: inEditor ? 200 : 0
        });
    }

    listen("keyboardKey", (e: KeyboardKeyEvent) => {
        if (e.key === "key.keyboard.left.shift") {
            isGridIgnored = e.action === 1;
        }
    });

    onMount(() => {
        if (!inEditor || !element) {
            return;
        }

        editorElements.set(componentId, element);
        return () => editorElements.delete(componentId);
    });

    onMount(async () => {
        const gameWindow = await getGameWindow();
        scaleFactor = gameWindow.scaleFactor;
    });

    listen("scaleFactorChange", (event: ScaleFactorChangeEvent) => {
        scaleFactor = event.scaleFactor;
    });
</script>

<svelte:window
        on:mouseup={onMouseUp}
        on:mousemove={onMouseMove}
/>

<div class="draggable-element" data-component={componentName}
     style="{styleString} z-index: {displayedZIndex};" bind:this={element}
     transition:editorFade|global>
    <!-- svelte-ignore a11y-no-static-element-interactions -->
    <div
            class="contained-element"
            style={sizeStyleString}
            class:editor-mode={inEditor}
            class:magnetically-referenced={inEditor && magneticallyReferenced}
            on:mousedown={onMouseDown}
    >
        <slot/>
    </div>
    {#if isDragging}
        <div class="position" class:top={positionOnTop} transition:fade={{duration: 100}}>
            {displayPosition.x} &#215; {displayPosition.y}
        </div>
    {/if}
    {#if inEditor}
        <ComponentSettings
                name={componentName}
                id={componentId}
                {alignment}
                overlayOffset={isDragging ? POSITION_OVERLAY_OFFSET : 0}
        />
    {/if}
</div>

<style lang="scss">
  @use "./DraggableComponent.styles";
</style>
