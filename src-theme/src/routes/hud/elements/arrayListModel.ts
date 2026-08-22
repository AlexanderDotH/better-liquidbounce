import type {Module} from "../../../integration/types";

export type ArrayListVariant = "classic" | "modern";

export interface ArrayListLayoutSettings {
    showTags: boolean;
    order: "Ascending" | "Descending";
}

export interface ArrayListEntry extends Module {
    displayName: string;
    visibleTag: string | null;
    measuredWidth: number;
}

export type ArrayListNameFormatter = (name: string) => string;
export type ArrayListTextMeasurer = (text: string, font: string) => number;

const CLASSIC_FONT = "500 14px Inter";
const CLASSIC_HORIZONTAL_CHROME_PX = 20;
const MODERN_NAME_FONT = "550 12px Inter";
const MODERN_TAG_FONT = "600 10px Inter";
const MODERN_TAG_GAP_PX = 6;
const MODERN_TAG_HORIZONTAL_PADDING_PX = 12;
const MODERN_UNTAGGED_OUTER_PADDING_PX = 18;
const MODERN_TAGGED_OUTER_PADDING_PX = 14;

export function buildArrayListEntries(
    modules: readonly Module[],
    settings: ArrayListLayoutSettings,
    variant: ArrayListVariant,
    formatName: ArrayListNameFormatter,
    measureText: ArrayListTextMeasurer,
): ArrayListEntry[] {
    const entries = modules
        .filter(module => module.enabled && !module.hidden)
        .map(module => createEntry(module, settings.showTags, variant, formatName, measureText));
    const direction = settings.order === "Ascending" ? 1 : -1;

    return entries.sort((left, right) =>
        direction * (left.measuredWidth - right.measuredWidth),
    );
}

export function areArrayListEntriesRenderEquivalent(
    current: readonly ArrayListEntry[],
    next: readonly ArrayListEntry[],
): boolean {
    if (current.length !== next.length) {
        return false;
    }

    return current.every((entry, index) => {
        const nextEntry = next[index];
        return nextEntry !== undefined
            && entry.name === nextEntry.name
            && entry.displayName === nextEntry.displayName
            && entry.visibleTag === nextEntry.visibleTag;
    });
}

export function getArrayListMotionOffset(
    variant: ArrayListVariant,
    itemAlignment: "Left" | "Right",
): number {
    const magnitude = variant === "modern" ? 18 : 50;
    return itemAlignment === "Left" ? -magnitude : magnitude;
}

export class LatestArrayListModuleLoader {
    private loadSequence = 0;
    private readonly loadModules: () => Promise<Module[]>;

    constructor(loadModules: () => Promise<Module[]>) {
        this.loadModules = loadModules;
    }

    async loadLatest(): Promise<Module[] | null> {
        const sequence = ++this.loadSequence;
        const modules = await this.loadModules();

        return sequence === this.loadSequence ? modules : null;
    }

    invalidate(): void {
        this.loadSequence += 1;
    }
}

function createEntry(
    module: Module,
    showTags: boolean,
    variant: ArrayListVariant,
    formatName: ArrayListNameFormatter,
    measureText: ArrayListTextMeasurer,
): ArrayListEntry {
    const displayName = formatName(module.name);
    const visibleTag = showTags && module.tag ? module.tag : null;
    const measuredWidth = measureRenderedWidth(
        displayName,
        visibleTag,
        variant,
        measureText,
    );

    return {...module, displayName, visibleTag, measuredWidth};
}

function measureRenderedWidth(
    displayName: string,
    visibleTag: string | null,
    variant: ArrayListVariant,
    measureText: ArrayListTextMeasurer,
): number {
    if (variant === "classic") {
        const label = visibleTag ? `${displayName} ${visibleTag}` : displayName;
        return measureText(label, CLASSIC_FONT) + CLASSIC_HORIZONTAL_CHROME_PX;
    }

    const nameWidth = measureText(displayName, MODERN_NAME_FONT);
    if (!visibleTag) {
        return nameWidth + MODERN_UNTAGGED_OUTER_PADDING_PX;
    }

    return nameWidth
        + MODERN_TAG_GAP_PX
        + MODERN_TAG_HORIZONTAL_PADDING_PX
        + measureText(visibleTag, MODERN_TAG_FONT)
        + MODERN_TAGGED_OUTER_PADDING_PX;
}
