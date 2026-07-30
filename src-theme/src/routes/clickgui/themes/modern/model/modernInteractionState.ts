import type {ConfigurableSetting} from "../../../../../integration/types";

export const MODERN_MODULE_EXPANSION_PREFIX = "clickgui.modern.module.v1.";

export function modernModuleExpansionKey(moduleName: string): string {
    return `${MODERN_MODULE_EXPANSION_PREFIX}${moduleName}`;
}

export function clampSearchSelection(selectedIndex: number, resultCount: number): number {
    const count = Math.max(0, Math.trunc(resultCount));
    if (count === 0) {
        return 0;
    }

    return Math.max(0, Math.min(Math.trunc(selectedIndex), count - 1));
}

export function moveSearchSelection(
    selectedIndex: number,
    resultCount: number,
    direction: -1 | 1,
): number {
    const count = Math.max(0, Math.trunc(resultCount));
    if (count === 0) {
        return 0;
    }

    const current = clampSearchSelection(selectedIndex, count);
    return (current + direction + count) % count;
}

export function readSearchBarAutoFocus(
    configurable: Pick<ConfigurableSetting, "value">,
    fallback = true,
): boolean {
    const setting = configurable.value.find(value => value.name === "SearchBarAutoFocus");
    return typeof setting?.value === "boolean" ? setting.value : fallback;
}
