import type {Module} from "../../../../../integration/types";
import {convertToSpacedString} from "../../../../../theme/theme_config";
import type {ClickGuiDataSource} from "./clickGuiDataSource";
import {readSearchBarAutoFocus} from "./modernInteractionState";

export type ModernSearchCommand = "next" | "previous" | "toggle" | "locate" | "clear";

export function browserSearchCommand(key: string): ModernSearchCommand | null {
    return ({
        ArrowDown: "next",
        ArrowUp: "previous",
        Enter: "toggle",
        Tab: "locate",
        Escape: "clear",
    } as Record<string, ModernSearchCommand>)[key] ?? null;
}

export function minecraftSearchCommand(key: string): ModernSearchCommand | null {
    return ({
        "key.keyboard.down": "next",
        "key.keyboard.up": "previous",
        "key.keyboard.enter": "toggle",
        "key.keyboard.tab": "locate",
        "key.keyboard.escape": "clear",
    } as Record<string, ModernSearchCommand>)[key] ?? null;
}

export async function loadModernSearch(
    dataSource: ClickGuiDataSource,
    currentAutoFocus: boolean,
) {
    const [moduleResult, clickGuiResult] = await Promise.allSettled([
        dataSource.getModules(),
        dataSource.getModuleSettings("ClickGUI"),
    ]);
    return {
        modules: moduleResult.status === "fulfilled" ? moduleResult.value : [],
        autoFocus: clickGuiResult.status === "fulfilled"
            ? readSearchBarAutoFocus(clickGuiResult.value, currentAutoFocus)
            : currentAutoFocus,
        error: moduleResult.status === "rejected"
            ? describeModernSearchError(moduleResult.reason, "Modules could not be loaded.")
            : null,
        modulesLoaded: moduleResult.status === "fulfilled",
    };
}

export async function toggleModernSearchModule(
    dataSource: ClickGuiDataSource,
    module: Module,
    applyEnabled: (enabled: boolean) => void,
): Promise<string | null> {
    const nextEnabled = !module.enabled;
    applyEnabled(nextEnabled);
    try {
        await dataSource.setModuleEnabled(module.name, nextEnabled);
        return null;
    } catch (error) {
        applyEnabled(module.enabled);
        return describeModernSearchError(error, "Module state could not be changed.");
    }
}

export function modernSearchDisplayName(value: string, spacedNames: boolean): string {
    return spacedNames ? convertToSpacedString(value) : value;
}

export function prefersReducedMotion(): boolean {
    return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
}

function describeModernSearchError(error: unknown, fallback: string): string {
    if (!(error instanceof Error) || !error.message.trim()) return fallback;
    return `${fallback} ${error.message}`;
}
