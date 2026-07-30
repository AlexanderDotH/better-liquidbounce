export const MODERN_PANEL_WIDTH = 288;
export const MODERN_PANEL_HEADER_HEIGHT = 44;
export const MODERN_PANEL_CANVAS_PADDING = 20;
export const MODERN_PANEL_GAP = 16;
export const MODERN_PANEL_CANVAS_TOP = 84;
export const MODERN_PANEL_STATE_PREFIX = "clickgui.modern.panel.v1.";

const MODERN_PANEL_ROW_HEIGHT = MODERN_PANEL_HEADER_HEIGHT + MODERN_PANEL_GAP;

export interface ModernPanelState {
    left: number;
    top: number;
    expanded: boolean;
    scrollTop: number;
    zIndex: number;
}

export interface ModernPanelPosition {
    left: number;
    top: number;
}

export interface LogicalViewport {
    width: number;
    height: number;
}

export interface ModernPanelSnapOptions {
    gridSize: number;
    snappingEnabled: boolean;
    shiftHeld: boolean;
}

export function arrangeModernPanels(
    panels: readonly unknown[],
    viewportWidth: number,
): ModernPanelState[] {
    const columnCount = modernPanelColumnCount(viewportWidth);

    return panels.map((_, index) => ({
        left: MODERN_PANEL_CANVAS_PADDING
            + (index % columnCount) * (MODERN_PANEL_WIDTH + MODERN_PANEL_GAP),
        top: MODERN_PANEL_CANVAS_TOP
            + Math.floor(index / columnCount) * MODERN_PANEL_ROW_HEIGHT,
        expanded: false,
        scrollTop: 0,
        zIndex: 0,
    }));
}

export function modernPanelStateKey(category: string): string {
    return `${MODERN_PANEL_STATE_PREFIX}${category}`;
}

export function findModernPanelStateKeys(keys: Iterable<string>): string[] {
    return Array.from(keys).filter(key =>
        key.startsWith(MODERN_PANEL_STATE_PREFIX)
        && key.length > MODERN_PANEL_STATE_PREFIX.length
    );
}

export function parseModernPanelState(
    serialized: string | null | undefined,
    fallback: ModernPanelState,
): ModernPanelState {
    if (!serialized) {
        return {...fallback};
    }

    try {
        const parsed: unknown = JSON.parse(serialized);
        return isModernPanelState(parsed) ? {...parsed} : {...fallback};
    } catch {
        return {...fallback};
    }
}

export function clampModernPanelPosition(
    position: ModernPanelPosition,
    viewport: LogicalViewport,
): ModernPanelPosition {
    const maxLeft = Math.max(0, finiteDimension(viewport.width) - MODERN_PANEL_WIDTH);
    const maxTop = Math.max(0, finiteDimension(viewport.height) - MODERN_PANEL_HEADER_HEIGHT);
    const minTop = Math.min(MODERN_PANEL_CANVAS_TOP, maxTop);

    return {
        left: clamp(position.left, 0, maxLeft),
        top: clamp(position.top, minTop, maxTop),
    };
}

export function snapModernPanelPosition(
    position: ModernPanelPosition,
    options: ModernPanelSnapOptions,
): ModernPanelPosition {
    if (!options.snappingEnabled || options.shiftHeld || options.gridSize <= 0) {
        return {...position};
    }

    return {
        left: snapCoordinate(position.left, options.gridSize),
        top: snapCoordinate(position.top, options.gridSize),
    };
}

function modernPanelColumnCount(viewportWidth: number): number {
    const availableWidth = finiteDimension(viewportWidth) - (2 * MODERN_PANEL_CANVAS_PADDING);
    const panelStride = MODERN_PANEL_WIDTH + MODERN_PANEL_GAP;
    return Math.max(1, Math.floor((availableWidth + MODERN_PANEL_GAP) / panelStride));
}

function isModernPanelState(value: unknown): value is ModernPanelState {
    if (!isRecord(value) || typeof value.expanded !== "boolean") {
        return false;
    }

    return isFiniteNumber(value.left)
        && isFiniteNumber(value.top)
        && isNonNegativeFiniteNumber(value.scrollTop)
        && isNonNegativeFiniteNumber(value.zIndex);
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isFiniteNumber(value: unknown): value is number {
    return typeof value === "number" && Number.isFinite(value);
}

function isNonNegativeFiniteNumber(value: unknown): value is number {
    return isFiniteNumber(value) && value >= 0;
}

function finiteDimension(value: number): number {
    return Number.isFinite(value) ? Math.max(0, value) : 0;
}

function clamp(value: number, minimum: number, maximum: number): number {
    const finiteValue = Number.isFinite(value) ? value : minimum;
    return Math.max(minimum, Math.min(finiteValue, maximum));
}

function snapCoordinate(value: number, gridSize: number): number {
    return Math.round(value / gridSize) * gridSize;
}
