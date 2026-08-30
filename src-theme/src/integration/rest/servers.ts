import type {FritzBoxReconnectRequest, FritzBoxReconnectResult, Protocol, Server} from "../types";
import {API_BASE} from "./base";

export async function getServers(): Promise<Server[]> {
    const response = await fetch(`${API_BASE}/client/servers`);
    const data: Server[] = await response.json();

    return data;
}

export async function getLanServers(): Promise<Server[]> {
    const response = await fetch(`${API_BASE}/client/servers/lan`);

    if (!response.ok) {
        return [];
    }

    const data: Server[] = await response.json();

    return data;
}

export async function connectToServer(address: string) {
    await fetch(`${API_BASE}/client/servers/connect`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({address})
    });
}

export async function removeServer(id: number) {
    await fetch(`${API_BASE}/client/servers/remove`, {
        method: "DELETE",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({id})
    });
}

export async function addServer(name: string, address: string, resourcePackPolicy: string) {
    await fetch(`${API_BASE}/client/servers/add`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({name, address, resourcePackPolicy})
    });
}

export async function editServer(id: number, name: string, address: string, resourcePackPolicy: string) {
    await fetch(`${API_BASE}/client/servers/edit`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({id, name, address, resourcePackPolicy})
    });
}

export async function orderServers(order: number[]) {
    await fetch(`${API_BASE}/client/servers/order`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({order})
    });
}

export async function getProtocols(): Promise<Protocol[]> {
    const response = await fetch(`${API_BASE}/client/protocols`);
    const data: Protocol[] = await response.json();

    return data;
}

export async function getSelectedProtocol(): Promise<Protocol> {
    const response = await fetch(`${API_BASE}/client/protocols/protocol`);
    const data: Protocol = await response.json();

    return data;
}

export async function setSelectedProtocol(protocol: Protocol) {
    await fetch(`${API_BASE}/client/protocols/protocol`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({version: protocol.version})
    });
}

export async function reconnectFritzBox(password?: string): Promise<FritzBoxReconnectResult> {
    const request: FritzBoxReconnectRequest = {};
    if (password !== undefined) {
        request.password = password;
    }

    const response = await fetch(`${API_BASE}/client/fritzbox/reconnect`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(request)
    });

    if (!response.ok) {
        const message = await response.text();
        throw new Error(message || `FritzBox reconnect failed with HTTP ${response.status}`);
    }

    return await response.json();
}
