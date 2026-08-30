import type {Alignment} from "../../../integration/types/hud.ts";
import {
    HUD_EDITOR_GRID_SIZE,
    HUD_EDITOR_MAGNET_THRESHOLD,
} from "../../../shared/hud-editor/HudEditorContracts";
import {
    findMagneticSnap,
    toHudCoordinate,
    type MagneticSnap,
} from "./draggableAlignmentModel.ts";
import {
    beginHudEditorDrag,
    moveHudEditorDrag,
    sameDragFeedback,
    type DragFeedback,
    type DragMove,
    type DragStart,
} from "./draggableInteractionModel.ts";

interface DragElementContext {
    element?: HTMLElement;
    scaleFactor: number;
}

interface MoveDomDragRequest extends DragElementContext {
    event: MouseEvent;
    editorElements: Map<string, HTMLElement>;
    componentId: string;
    pointerCenterOffsetX: number;
    pointerCenterOffsetY: number;
    gridIgnored: boolean;
}

export interface MoveDomDragResult {
    moved: DragMove;
    horizontalSnap?: MagneticSnap;
    verticalSnap?: MagneticSnap;
}

export function beginDomDrag(
    alignment: Alignment,
    event: MouseEvent,
    context: DragElementContext,
): DragStart {
    const dimensions = dragDimensions(event, context);
    return beginHudEditorDrag(alignment, dimensions);
}

export function moveDomDrag(request: MoveDomDragRequest): MoveDomDragResult {
    const dimensions = dragDimensions(request.event, request);
    const horizontalSnap = editorSnap(
        dimensions.cursorX + request.pointerCenterOffsetX,
        dimensions.elementWidth,
        true,
        request,
    );
    const verticalSnap = editorSnap(
        dimensions.cursorY + request.pointerCenterOffsetY,
        dimensions.elementHeight,
        false,
        request,
    );
    return {
        horizontalSnap,
        verticalSnap,
        moved: moveHudEditorDrag({
            ...dimensions,
            pointerCenterOffsetX: request.pointerCenterOffsetX,
            pointerCenterOffsetY: request.pointerCenterOffsetY,
            horizontalSnap,
            verticalSnap,
            gridSize: HUD_EDITOR_GRID_SIZE,
            gridIgnored: request.gridIgnored,
        }),
    };
}

export function changedDragFeedback(
    current: DragFeedback,
    moved: DragMove,
    horizontalSnap?: MagneticSnap,
    verticalSnap?: MagneticSnap,
): DragFeedback | null {
    const next = {
        horizontalZone: moved.horizontalZone,
        verticalZone: moved.verticalZone,
        verticalGuide: horizontalSnap?.guide,
        horizontalGuide: verticalSnap?.guide,
        horizontalTargetId: horizontalSnap?.targetId,
        verticalTargetId: verticalSnap?.targetId,
    };
    return sameDragFeedback(current, next) ? null : next;
}

export function elementDisplayPosition(element: HTMLElement) {
    const bounds = element.getBoundingClientRect();
    return {
        position: {x: Math.round(bounds.x), y: Math.round(bounds.y)},
        onTop: bounds.top + bounds.height / 2 >= window.innerHeight / 2,
    };
}

function dragDimensions(event: MouseEvent, context: DragElementContext) {
    const bounds = context.element?.getBoundingClientRect();
    const scaled = (value: number) => toHudCoordinate(value, context.scaleFactor);
    return {
        cursorX: scaled(event.clientX),
        cursorY: scaled(event.clientY),
        elementWidth: scaled(bounds?.width ?? 0),
        elementHeight: scaled(bounds?.height ?? 0),
        hudWidth: scaled(window.innerWidth),
        hudHeight: scaled(window.innerHeight),
    };
}

function editorSnap(
    center: number,
    size: number,
    horizontal: boolean,
    request: MoveDomDragRequest,
): MagneticSnap | undefined {
    if (request.gridIgnored) return undefined;
    const scaled = (value: number) => toHudCoordinate(value, request.scaleFactor);
    const viewportSize = scaled(horizontal ? window.innerWidth : window.innerHeight);
    const targets = [...request.editorElements]
        .filter(([id]) => id !== request.componentId)
        .map(([id, target]) => ({id, points: elementPoints(target, horizontal, scaled)}));
    return findMagneticSnap({center, size, viewportSize, threshold: HUD_EDITOR_MAGNET_THRESHOLD, targets});
}

function elementPoints(target: HTMLElement, horizontal: boolean, scaled: (value: number) => number): number[] {
    const bounds = target.getBoundingClientRect();
    const start = scaled(horizontal ? bounds.left : bounds.top);
    const size = scaled(horizontal ? bounds.width : bounds.height);
    return [start, start + size / 2, start + size];
}
