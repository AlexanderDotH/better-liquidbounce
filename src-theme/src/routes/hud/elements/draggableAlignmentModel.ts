import type {Alignment, HorizontalAlignment, VerticalAlignment} from "../../../integration/types/hud.ts";
import type {
    HorizontalAnchorZone,
    VerticalAnchorZone,
} from "../../../shared/hud-editor/HudEditorContracts";

export function toHudCoordinate(clientCoordinate: number, scaleFactor: number): number {
    return clientCoordinate * (2 / scaleFactor);
}

export function horizontalCenter(
    alignment: HorizontalAlignment,
    offset: number,
    elementSize: number,
    viewportSize: number,
): number {
    switch (alignment) {
        case "Left":
            return offset + elementSize / 2;
        case "Right":
            return viewportSize - offset - elementSize / 2;
        case "Center":
            return viewportSize / 2 + offset + elementSize / 2;
        case "CenterTranslated":
            return viewportSize / 2 + offset;
    }
    throw new Error(`Unsupported horizontal alignment: ${alignment}`);
}

export function verticalCenter(
    alignment: VerticalAlignment,
    offset: number,
    elementSize: number,
    viewportSize: number,
): number {
    switch (alignment) {
        case "Top":
            return offset + elementSize / 2;
        case "Bottom":
            return viewportSize - offset - elementSize / 2;
        case "Center":
            return viewportSize / 2 + offset + elementSize / 2;
        case "CenterTranslated":
            return viewportSize / 2 + offset;
    }
    throw new Error(`Unsupported vertical alignment: ${alignment}`);
}

export function horizontalAnchorZone(cursor: number, viewportSize: number): HorizontalAnchorZone {
    if (cursor < viewportSize / 3) return "left";
    if (cursor > viewportSize * 2 / 3) return "right";
    return "center";
}

export function verticalAnchorZone(cursor: number, viewportSize: number): VerticalAnchorZone {
    if (cursor < viewportSize / 3) return "upper";
    if (cursor > viewportSize * 2 / 3) return "lower";
    return "center";
}

export function horizontalAlignment(zone: HorizontalAnchorZone): HorizontalAlignment {
    if (zone === "left") return "Left" as HorizontalAlignment;
    if (zone === "right") return "Right" as HorizontalAlignment;
    return "CenterTranslated" as HorizontalAlignment;
}

export function verticalAlignment(zone: VerticalAnchorZone): VerticalAlignment {
    if (zone === "upper") return "Top" as VerticalAlignment;
    if (zone === "lower") return "Bottom" as VerticalAlignment;
    return "CenterTranslated" as VerticalAlignment;
}

export function alignmentOffset(
    center: number,
    anchor: HorizontalAlignment | VerticalAlignment,
    elementSize: number,
    viewportSize: number,
): number {
    if (anchor === "Left" || anchor === "Top") {
        return center - elementSize / 2;
    }
    if (anchor === "Right" || anchor === "Bottom") {
        return viewportSize - center - elementSize / 2;
    }
    if (anchor === "Center") {
        return center - viewportSize / 2 - elementSize / 2;
    }
    return center - viewportSize / 2;
}

export function clampAlignmentOffset(
    offset: number,
    anchor: HorizontalAlignment | VerticalAlignment,
    elementSize: number,
    viewportSize: number,
): number {
    if (anchor === "CenterTranslated") {
        return clamp(offset, -viewportSize / 2 + elementSize / 2, viewportSize / 2 - elementSize / 2);
    }
    if (anchor === "Center") {
        return clamp(offset, -viewportSize / 2, viewportSize / 2 - elementSize);
    }
    return clamp(offset, 0, viewportSize - elementSize);
}

export function snapHudEditorGrid(value: number, gridSize: number, ignored: boolean): number {
    return ignored ? value : Math.round(value / gridSize) * gridSize;
}

export interface MagneticSnap {
    center: number;
    guide: number;
    targetId?: string;
}

export interface MagneticTarget {
    id: string;
    points: readonly number[];
}

export interface MagneticSnapRequest {
    center: number;
    size: number;
    viewportSize: number;
    threshold: number;
    targets: readonly MagneticTarget[];
}

export function findMagneticSnap(request: MagneticSnapRequest): MagneticSnap | undefined {
    const draggedPoints = [
        request.center - request.size / 2,
        request.center,
        request.center + request.size / 2,
    ];
    const viewportCenter = request.viewportSize / 2;
    const viewportDistance = viewportCenter - request.center;
    let closest: MagneticSnap | undefined = Math.abs(viewportDistance) <= request.threshold
        ? {center: viewportCenter, guide: viewportCenter}
        : undefined;
    let closestDistance = closest ? Math.abs(viewportDistance) : request.threshold + 1;

    for (const candidate of magneticCandidates(request.targets, draggedPoints, request.center)) {
        if (!isCloserInBounds(candidate.distance, candidate.center, closestDistance, request)) {
            continue;
        }
        closestDistance = Math.abs(candidate.distance);
        closest = candidate.snap;
    }
    return closest;
}

function magneticCandidates(
    targets: readonly MagneticTarget[],
    draggedPoints: readonly number[],
    center: number,
) {
    return targets.flatMap(target => target.points.flatMap(guide => draggedPoints.map(draggedPoint => {
        const distance = guide - draggedPoint;
        const snappedCenter = center + distance;
        return {
            distance,
            center: snappedCenter,
            snap: {center: snappedCenter, guide, targetId: target.id},
        };
    })));
}

function isCloserInBounds(
    distance: number,
    center: number,
    closestDistance: number,
    request: MagneticSnapRequest,
): boolean {
    return Math.abs(distance) <= request.threshold
        && Math.abs(distance) < closestDistance
        && center - request.size / 2 >= 0
        && center + request.size / 2 <= request.viewportSize;
}

export function generateAlignmentStyle(alignment: Alignment): string {
    const translateX = alignment.horizontalAlignment === "CenterTranslated" ? "-50%" : "0";
    const translateY = alignment.verticalAlignment === "CenterTranslated" ? "-50%" : "0";
    return [
        "position: fixed;",
        horizontalStyle(alignment),
        verticalStyle(alignment),
        `transform: translate(${translateX}, ${translateY});`,
    ].join(" ");
}

function horizontalStyle(alignment: Alignment): string {
    if (alignment.horizontalAlignment === "Left") {
        return `left: ${alignment.horizontalOffset}px;`;
    }
    if (alignment.horizontalAlignment === "Right") {
        return `right: ${alignment.horizontalOffset}px;`;
    }
    return `left: calc(50% + ${alignment.horizontalOffset}px);`;
}

function verticalStyle(alignment: Alignment): string {
    if (alignment.verticalAlignment === "Top") {
        return `top: ${alignment.verticalOffset}px;`;
    }
    if (alignment.verticalAlignment === "Bottom") {
        return `bottom: ${alignment.verticalOffset}px;`;
    }
    return `top: calc(50% + ${alignment.verticalOffset}px);`;
}

function clamp(value: number, minimum: number, maximum: number): number {
    return Math.max(minimum, Math.min(value, maximum));
}
