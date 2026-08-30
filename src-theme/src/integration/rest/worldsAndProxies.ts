import type {Proxy, World} from "../types";
import {API_BASE} from "./base";

export async function getWorlds(): Promise<World[]> {
    const response = await fetch(`${API_BASE}/client/worlds`);
    const data: World[] = await response.json();

    return data;
}

export async function openWorld(name: string) {
    const response = await fetch(`${API_BASE}/client/worlds/join`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({name})
    });
}

export async function editWorld(name: string) {
    const response = await fetch(`${API_BASE}/client/worlds/edit`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({name})
    });
}

export async function removeWorld(name: string) {
    const response = await fetch(`${API_BASE}/client/worlds/delete`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({name})
    });
}

export async function getProxies(): Promise<Proxy[]> {
    const response = await fetch(`${API_BASE}/client/proxies`);
    const data: Proxy[] = await response.json();

    return data;
}

export async function checkProxy(id: number) {
    await fetch(`${API_BASE}/client/proxies/check`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({id})
    });
}

export async function getCurrentProxy(): Promise<Proxy | null> {
    const response = await fetch(`${API_BASE}/client/proxy`);

    if (response.status !== 200) {
        return null;
    }

    const data: Proxy = await response.json();

    return data;
}

export async function disconnectFromProxy() {
    await fetch(`${API_BASE}/client/proxy`, {
        method: "DELETE",
    });
}

export async function setProxyFavorite(id: number, favorite: boolean) {
    if (favorite) {
        await fetch(`${API_BASE}/client/proxies/favorite`, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({id})
        });
    } else {
        await fetch(`${API_BASE}/client/proxies/favorite`, {
            method: "DELETE",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({id})
        });
    }
}

export async function addProxy(host: string, port: number, username: string, password: string, type: string, forwardAuthentication: boolean) {
    await fetch(`${API_BASE}/client/proxies/add`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({host, port, username, password, type, forwardAuthentication})
    });
}

export async function editProxy(id: number, host: string, port: number, username: string, password: string, type: string, forwardAuthentication: boolean) {
    await fetch(`${API_BASE}/client/proxies/edit`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({id, host, port, username, password, type, forwardAuthentication})
    })
}

export async function addProxyFromClipboard() {
    await fetch(`${API_BASE}/client/proxies/add/clipboard`, {
        method: "POST"
    });
}

export async function removeProxy(id: number) {
    await fetch(`${API_BASE}/client/proxies/remove`, {
        method: "DELETE",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({id})
    });
}

export async function connectToProxy(id: number) {
    await fetch(`${API_BASE}/client/proxy`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({id})
    });
}
