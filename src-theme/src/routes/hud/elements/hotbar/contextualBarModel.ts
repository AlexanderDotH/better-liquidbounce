import type {ContextualBarData, LocatorMarker} from "../../../../integration/types";

const EMPTY_MARKERS: readonly LocatorMarker[] = Object.freeze([]);

export const EMPTY_CONTEXTUAL_BAR: ContextualBarData = Object.freeze({
    mode: "empty",
    progress: 0,
    level: 0,
    cooldown: false,
    markers: EMPTY_MARKERS,
});

export function clampContextualProgress(progress: number): number {
    return Number.isFinite(progress) ? Math.min(1, Math.max(0, progress)) : 0;
}

export function locatorMarkerPercent(offset: number): number {
    const normalizedOffset = Number.isFinite(offset)
        ? Math.min(1, Math.max(-1, offset))
        : 0;

    return 50 + normalizedOffset * 46;
}

export function locatorRgbColor(color: number): string {
    const rgb = color & 0xFFFFFF;
    return `#${rgb.toString(16).padStart(6, "0")}`;
}

export function sortLocatorMarkersForRendering<T extends {distance: number}>(markers: readonly T[]): T[] {
    const finiteDistance = (marker: T) => Number.isFinite(marker.distance) ? marker.distance : 0;
    return [...markers].sort((left, right) => finiteDistance(right) - finiteDistance(left));
}

export function waypointEmoji(style: string): string {
    return style.toLowerCase().includes("bowtie") ? "🎀" : "📍";
}
