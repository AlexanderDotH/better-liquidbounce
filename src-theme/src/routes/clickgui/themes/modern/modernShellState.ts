import type {ClickGuiView} from "../../theme/clickGuiThemeState";

export const DEFAULT_CLICK_GUI_SCALE_FACTOR = 2;

const CLICK_GUI_VIEWS: readonly ClickGuiView[] = ["clickgui", "hud-editor", "settings"];

export function normalizeClickGuiScaleFactor(scaleFactor: number): number {
    return Number.isFinite(scaleFactor) && scaleFactor > 0
        ? scaleFactor
        : DEFAULT_CLICK_GUI_SCALE_FACTOR;
}

export function logicalViewportDimension(
    physicalDimension: number,
    scaleFactor: number,
): number {
    const finiteDimension = Number.isFinite(physicalDimension)
        ? Math.max(0, physicalDimension)
        : 0;

    return finiteDimension * (2 / normalizeClickGuiScaleFactor(scaleFactor));
}

export function motionAwareScrollBehavior(
    reducedMotion: boolean,
): "auto" | "smooth" {
    return reducedMotion ? "auto" : "smooth";
}

export function moveClickGuiView(
    current: ClickGuiView,
    direction: -1 | 1,
): ClickGuiView {
    const currentIndex = CLICK_GUI_VIEWS.indexOf(current);
    const nextIndex = (currentIndex + direction + CLICK_GUI_VIEWS.length)
        % CLICK_GUI_VIEWS.length;

    return CLICK_GUI_VIEWS[nextIndex];
}
