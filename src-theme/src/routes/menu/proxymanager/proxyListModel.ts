import lookup from "country-code-lookup";
import type {Proxy} from "../../../integration/types";

export interface ProxyListFilters {
    countries: readonly string[];
    proxyTypes: readonly string[];
    favoritesOnly: boolean;
    searchQuery: string;
}

export function convertProxyCountryCode(code: string | undefined): string {
    if (code === undefined) {
        return "Unknown";
    }
    return lookup.byIso(code)?.country ?? "Unknown";
}

export function filterProxies(
    proxies: readonly Proxy[],
    filters: ProxyListFilters,
): Proxy[] {
    const query = filters.searchQuery.toLocaleLowerCase();
    return proxies.filter(proxy =>
        filters.countries.includes(convertProxyCountryCode(proxy.ipInfo?.country))
        && filters.proxyTypes.includes(proxy.type)
        && (!filters.favoritesOnly || proxy.favorite)
        && (!query || proxy.host.toLocaleLowerCase().includes(query)),
    );
}
