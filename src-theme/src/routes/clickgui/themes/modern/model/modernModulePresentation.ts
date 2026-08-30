import {convertToSpacedString} from "../../../../../theme/theme_config";

export function modernModuleDisplayName(value: string, spacedNames: boolean): string {
    return spacedNames ? convertToSpacedString(value) : value;
}

export function modernModuleDescription(
    bounds: Pick<DOMRect, "left" | "right" | "top" | "height">,
    viewportWidth: number,
    scaleFactor: number,
    description: string,
    aliases: string[],
    spacedNames: boolean,
) {
    const aliasText = aliases.length > 0
        ? ` (aka ${aliases.map(alias => modernModuleDisplayName(alias, spacedNames)).join(", ")})`
        : "";
    const useRightAnchor = viewportWidth - bounds.right > 300;
    return {
        x: logicalDimension(useRightAnchor ? bounds.right : bounds.left, scaleFactor),
        y: logicalDimension(bounds.top + bounds.height / 2, scaleFactor),
        anchor: useRightAnchor ? "right" as const : "left" as const,
        description: `${description}${aliasText}`,
    };
}

export function describeModernModuleError(error: unknown, fallback: string): string {
    if (!(error instanceof Error) || !error.message.trim()) return fallback;
    return `${fallback} ${error.message}`;
}

export function prefersReducedMotion(): boolean {
    return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
}

export function shouldToggleModernModuleExpansion(
    key: string,
    expanded: boolean,
    hasSettings: boolean,
): boolean {
    if (!hasSettings) return false;
    if (key === "ContextMenu") return true;
    if (key === "ArrowRight") return !expanded;
    return key === "ArrowLeft" && expanded;
}

function logicalDimension(physicalPixels: number, scaleFactor: number): number {
    return physicalPixels / scaleFactor * 2;
}
