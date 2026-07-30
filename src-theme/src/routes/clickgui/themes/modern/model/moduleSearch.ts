import type {Module} from "../../../../../integration/types";

type SearchableModule = Pick<Module, "name" | "aliases">;

export function normalizeModuleSearchText(value: string): string {
    return value.toLowerCase().replaceAll(/\s/g, "");
}

export function filterModulesBySearch<T extends SearchableModule>(
    modules: readonly T[],
    query: string,
): T[] {
    const normalizedQuery = normalizeModuleSearchText(query);
    if (!normalizedQuery) {
        return [];
    }

    return modules.filter(module => matchesModule(module, normalizedQuery));
}

function matchesModule(module: SearchableModule, normalizedQuery: string): boolean {
    if (normalizeModuleSearchText(module.name).includes(normalizedQuery)) {
        return true;
    }

    return module.aliases.some(alias =>
        normalizeModuleSearchText(alias).includes(normalizedQuery)
    );
}
