import type {Alignment} from "../../../integration/types/hud.ts";
import type {
    HorizontalAnchorZone,
    HudEditorDragState,
    VerticalAnchorZone,
} from "../../../shared/hud-editor/HudEditorContracts";
import {
    alignmentOffset,
    clampAlignmentOffset,
    horizontalAlignment,
    horizontalAnchorZone,
    horizontalCenter,
    snapHudEditorGrid,
    verticalAlignment,
    verticalAnchorZone,
    verticalCenter,
    type MagneticSnap,
} from "./draggableAlignmentModel.ts";

export interface DragDimensions {
    cursorX: number;
    cursorY: number;
    elementWidth: number;
    elementHeight: number;
    hudWidth: number;
    hudHeight: number;
}

export interface DragStart {
    pointerCenterOffsetX: number;
    pointerCenterOffsetY: number;
    horizontalZone: HorizontalAnchorZone;
    verticalZone: VerticalAnchorZone;
}

export function beginHudEditorDrag(alignment: Alignment, dimensions: DragDimensions): DragStart {
    const currentHorizontalCenter = horizontalCenter(
        alignment.horizontalAlignment,
        alignment.horizontalOffset,
        dimensions.elementWidth,
        dimensions.hudWidth,
    );
    const currentVerticalCenter = verticalCenter(
        alignment.verticalAlignment,
        alignment.verticalOffset,
        dimensions.elementHeight,
        dimensions.hudHeight,
    );
    return {
        pointerCenterOffsetX: currentHorizontalCenter - dimensions.cursorX,
        pointerCenterOffsetY: currentVerticalCenter - dimensions.cursorY,
        horizontalZone: horizontalAnchorZone(dimensions.cursorX, dimensions.hudWidth),
        verticalZone: verticalAnchorZone(dimensions.cursorY, dimensions.hudHeight),
    };
}

export interface DragMoveRequest extends DragDimensions {
    pointerCenterOffsetX: number;
    pointerCenterOffsetY: number;
    horizontalSnap?: MagneticSnap;
    verticalSnap?: MagneticSnap;
    gridSize: number;
    gridIgnored: boolean;
}

export interface DragMove {
    alignment: Alignment;
    horizontalZone: HorizontalAnchorZone;
    verticalZone: VerticalAnchorZone;
}

export function moveHudEditorDrag(request: DragMoveRequest): DragMove {
    const horizontalZone = horizontalAnchorZone(request.cursorX, request.hudWidth);
    const verticalZone = verticalAnchorZone(request.cursorY, request.hudHeight);
    const nextHorizontalAlignment = horizontalAlignment(horizontalZone);
    const nextVerticalAlignment = verticalAlignment(verticalZone);
    const horizontalCenter = request.cursorX + request.pointerCenterOffsetX;
    const verticalCenter = request.cursorY + request.pointerCenterOffsetY;
    const horizontalOffset = alignmentOffset(
        request.horizontalSnap?.center ?? horizontalCenter,
        nextHorizontalAlignment,
        request.elementWidth,
        request.hudWidth,
    );
    const verticalOffset = alignmentOffset(
        request.verticalSnap?.center ?? verticalCenter,
        nextVerticalAlignment,
        request.elementHeight,
        request.hudHeight,
    );
    return {
        alignment: {
            horizontalAlignment: nextHorizontalAlignment,
            verticalAlignment: nextVerticalAlignment,
            horizontalOffset: clampedOffset(horizontalOffset, nextHorizontalAlignment, request.elementWidth, request.hudWidth, request.horizontalSnap, request),
            verticalOffset: clampedOffset(verticalOffset, nextVerticalAlignment, request.elementHeight, request.hudHeight, request.verticalSnap, request),
        },
        horizontalZone,
        verticalZone,
    };
}

function clampedOffset(
    offset: number,
    alignment: Alignment["horizontalAlignment"] | Alignment["verticalAlignment"],
    elementSize: number,
    viewportSize: number,
    snap: MagneticSnap | undefined,
    request: DragMoveRequest,
): number {
    const candidate = snap
        ? offset
        : snapHudEditorGrid(offset, request.gridSize, request.gridIgnored);
    return clampAlignmentOffset(candidate, alignment, elementSize, viewportSize);
}

export interface DragFeedback {
    horizontalZone: HorizontalAnchorZone;
    verticalZone: VerticalAnchorZone;
    verticalGuide?: number;
    horizontalGuide?: number;
    horizontalTargetId?: string;
    verticalTargetId?: string;
}

export function sameDragFeedback(left: DragFeedback, right: DragFeedback): boolean {
    return left.horizontalZone === right.horizontalZone
        && left.verticalZone === right.verticalZone
        && left.verticalGuide === right.verticalGuide
        && left.horizontalGuide === right.horizontalGuide
        && left.horizontalTargetId === right.horizontalTargetId
        && left.verticalTargetId === right.verticalTargetId;
}

export function hudEditorDragState(feedback: DragFeedback, dragging: boolean): HudEditorDragState {
    return {
        dragging,
        horizontalZone: feedback.horizontalZone,
        verticalZone: feedback.verticalZone,
        verticalGuide: feedback.verticalGuide,
        horizontalGuide: feedback.horizontalGuide,
        magneticTargetIds: [...new Set([
            feedback.horizontalTargetId,
            feedback.verticalTargetId,
        ].filter(id => id !== undefined))],
    };
}
