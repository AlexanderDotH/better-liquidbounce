import type {Browser, ClientInfo, ClientUpdate, ClientUser, GeneratorResult} from "../types";
import {API_BASE} from "./base";

export async function getClientInfo(): Promise<ClientInfo> {
    const response = await fetch(`${API_BASE}/client/info`);
    const data: ClientInfo = await response.json();

    return data;
}

export async function getClientUpdate(): Promise<ClientUpdate> {
    const response = await fetch(`${API_BASE}/client/update`);
    const data: ClientUpdate = await response.json();

    return data;
}

export async function reconnectToServer() {
    await fetch(`${API_BASE}/client/reconnect`, {
        method: "POST",
    });
}

export async function toggleBackgroundShaderEnabled() {
    await fetch(`${API_BASE}/client/shader`, {
        method: "POST",
    });
}

export async function getBrowser(): Promise<Browser> {
    const response = await fetch(`${API_BASE}/client/browser`);
    const data: Browser = await response.json();

    return data;
}

export async function browserNavigate(url: string) {
    await fetch(`${API_BASE}/client/browser/navigate`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({url})
    })
}

export async function browserGoForward() {
    await fetch(`${API_BASE}/client/browser/forward`, {
        method: "POST",
    });
}

export async function browserGoBack() {
    await fetch(`${API_BASE}/client/browser/back`, {
        method: "POST",
    });
}

export async function browserReload() {
    await fetch(`${API_BASE}/client/browser/reload`, {
        method: "POST",
    });
}

export async function browserForceReload() {
    await fetch(`${API_BASE}/client/browser/forceReload`, {
        method: "POST",
    });
}

export async function browserClose() {
    await fetch(`${API_BASE}/client/browser/close`, {
        method: "POST",
    });
}

export async function randomUsername(): Promise<string> {
    let response = await fetch(`${API_BASE}/client/account/random-name`, {
        method: "POST",
    });
    let data: GeneratorResult = await response.json();

    return data.name;
}

let lastTypingState: boolean | null = null;

export async function setTyping(typing: boolean) {
    if (typing === lastTypingState) return;
    lastTypingState = typing;
    await fetch(`${API_BASE}/client/typing`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({typing})
    });
}

export async function getClipboardText(): Promise<string> {
    const response = await fetch(`${API_BASE}/client/clipboard`);
    const data: { text?: string } = await response.json();
    return data.text ?? "";
}

export async function setClipboardText(text: string) {
    await fetch(`${API_BASE}/client/clipboard`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({text})
    });
}

export async function getClientUser(): Promise<ClientUser | null> {
    const response = await fetch(`${API_BASE}/client/user`);

    if (!response.ok) {
        if (response.status === 401) {
            return null;
        }
        throw new Error(`Failed to get client user: ${response.status} ${response.statusText}`);
    }

    const data: ClientUser = await response.json();
    return data;
}

export async function loginClientUser() {
    await fetch(`${API_BASE}/client/user/login`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        }
    });
}

export async function logoutClientUser() {
    await fetch(`${API_BASE}/client/user/logout`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        }
    });
}

export function itemTextureUrl(identifier: string) {
    return `${API_BASE}/client/resource/itemTexture?id=${identifier}`
}

export function effectTextureUrl(effectId: string) {
    return `${API_BASE}/client/resource/effectTexture?id=${effectId}`
}
