import type {Server} from "../../../integration/types";

export interface MultiplayerServerFilters {
    onlineOnly: boolean;
    searchQuery: string;
}

export function filterMultiplayerServers(
    servers: readonly Server[],
    lanServers: readonly Server[],
    filters: MultiplayerServerFilters,
): Server[] {
    let combined = [...servers, ...lanServers];
    if (filters.onlineOnly) {
        combined = combined.filter(server => server.ping > 0);
    }
    if (filters.searchQuery) {
        const query = filters.searchQuery.toLocaleLowerCase();
        combined = combined.filter(server => server.name.toLocaleLowerCase().includes(query));
    }
    return combined;
}

export function updatePingedServer(
    servers: readonly Server[],
    pingedServer: Server,
): Server[] {
    return servers.map(server => {
        if (server.address !== pingedServer.address) {
            return server;
        }

        return {
            ...structuredClone(pingedServer),
            id: server.id,
            name: server.name,
            resourcePackPolicy: server.resourcePackPolicy,
        };
    });
}

export function multiplayerPingColor(ping: number): string {
    if (ping < 0) {
        return "#E84C3D";
    }
    if (ping <= 50) {
        return "#2DCC70";
    }
    return ping <= 100 ? "#F1C40F" : "#E84C3D";
}

export function formatFritzBoxError(error: unknown): string {
    if (!(error instanceof Error)) {
        return "Failed";
    }
    if (error.message.includes("login failed")) {
        return "Login failed";
    }
    return error.message.includes("HTTP") ? "HTTP failed" : "Failed";
}
