export type MerchantTradeFilter = {
    inputA: string[];
    inputB: string[];
    outputs: string[];
};

export type MerchantTradeFilterSlot = keyof MerchantTradeFilter;

export type MerchantRegistryItem = {
    value: string;
    name: string;
    icon: string | undefined;
};

export function createEmptyMerchantTradeFilter(): MerchantTradeFilter {
    return {
        inputA: [],
        inputB: [],
        outputs: [],
    };
}

function identifierList(value: unknown): string[] {
    if (!Array.isArray(value)) {
        return [];
    }

    const identifiers = value
        .filter((identifier): identifier is string => typeof identifier === "string")
        .map(identifier => identifier.trim())
        .filter(Boolean);

    return [...new Set(identifiers)].slice(0, 1);
}

function recordArray(record: object, key: MerchantTradeFilterSlot): unknown {
    if (!Object.hasOwn(record, key)) {
        return [];
    }

    return (record as Record<MerchantTradeFilterSlot, unknown>)[key];
}

function normalizeMerchantTradeFilter(value: object): MerchantTradeFilter {
    return {
        inputA: identifierList(recordArray(value, "inputA")),
        inputB: identifierList(recordArray(value, "inputB")),
        outputs: identifierList(recordArray(value, "outputs")),
    };
}

export function normalizeMerchantTradeFilters(value: unknown): MerchantTradeFilter[] {
    if (!Array.isArray(value)) {
        return [];
    }

    return value
        .filter((rule): rule is object => typeof rule === "object" && rule !== null)
        .map(normalizeMerchantTradeFilter);
}

export function addMerchantTradeFilter(rules: readonly MerchantTradeFilter[]): MerchantTradeFilter[] {
    return [...normalizeMerchantTradeFilters(rules), createEmptyMerchantTradeFilter()];
}

export function removeMerchantTradeFilter(
    rules: readonly MerchantTradeFilter[],
    index: number,
): MerchantTradeFilter[] {
    return normalizeMerchantTradeFilters(rules).filter((_, currentIndex) => currentIndex !== index);
}

export function moveMerchantTradeFilter(
    rules: readonly MerchantTradeFilter[],
    fromIndex: number,
    toIndex: number,
): MerchantTradeFilter[] {
    const reordered = normalizeMerchantTradeFilters(rules);
    const validIndex = (index: number) => index >= 0 && index < reordered.length;

    if (!validIndex(fromIndex) || !validIndex(toIndex) || fromIndex === toIndex) {
        return reordered;
    }

    const [movedRule] = reordered.splice(fromIndex, 1);
    reordered.splice(toIndex, 0, movedRule);
    return reordered;
}

export function toggleMerchantTradeFilterItem(
    rules: readonly MerchantTradeFilter[],
    index: number,
    slot: MerchantTradeFilterSlot,
    itemIdentifier: string,
): MerchantTradeFilter[] {
    const updated = normalizeMerchantTradeFilters(rules);
    const rule = updated[index];
    const identifier = itemIdentifier.trim();

    if (!rule || !identifier) {
        return updated;
    }

    rule[slot] = rule[slot][0] === identifier ? [] : [identifier];

    return updated;
}

export function searchMerchantRegistryItems(
    items: readonly MerchantRegistryItem[],
    query: string,
): MerchantRegistryItem[] {
    const queryParts = query.toLocaleLowerCase().match(/\S+/g) ?? [];

    if (queryParts.length === 0) {
        return [...items];
    }

    return items.filter(item => {
        const searchableText = `${item.name} ${item.value}`.toLocaleLowerCase();
        return queryParts.every(part => searchableText.includes(part));
    });
}

export function isMerchantTradeFilterActive(rule: MerchantTradeFilter): boolean {
    return identifierList(rule.inputA).length > 0 && identifierList(rule.outputs).length > 0;
}
