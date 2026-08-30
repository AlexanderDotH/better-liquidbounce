import type {
    ContextualBarData,
    FileSelectDialog,
    FileSelectResult,
    HitResult,
    MinecraftKeybind,
    PlayerData,
    PrintableKey,
    RegistryItem,
    Session,
    VirtualScreen,
} from "../types";
import type {PlayerInventory} from "../events";
import {API_BASE} from "./base";

export async function getVirtualScreen(): Promise<VirtualScreen> {
    const response = await fetch(`${API_BASE}/client/virtualScreen`);
    const data: VirtualScreen = await response.json();

    return data;
}

export async function confirmVirtualScreen(name: string) {
    await fetch(`${API_BASE}/client/virtualScreen`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({name})
    });
}

export async function getPlayerData(): Promise<PlayerData> {
    const response = await fetch(`${API_BASE}/client/player`);
    const data: PlayerData = await response.json();

    return data;
}

export async function getContextualBar(): Promise<ContextualBarData> {
    const response = await fetch(`${API_BASE}/client/player/contextualBar`);
    if (!response.ok) {
        throw new Error(`Unable to load contextual bar: ${response.status}`);
    }

    return await response.json() as ContextualBarData;
}

export async function openFileDialog(body: FileSelectDialog): Promise<FileSelectResult> {
    const response = await fetch(`${API_BASE}/client/fileDialog`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(body)
    });

    return await response.json();
}

export async function getPlayerInventory(): Promise<PlayerInventory> {
    const response = await fetch(`${API_BASE}/client/player/inventory`);
    const data: PlayerInventory = await response.json();

    return data;
}

export async function getCrosshairData(): Promise<HitResult> {
    const response = await fetch(`${API_BASE}/client/crosshair`);
    const data: HitResult = await response.json();

    return data;
}

export async function getPrintableKeyName(key: string): Promise<PrintableKey> {
    const searchParams = new URLSearchParams({key});

    const response = await fetch(`${API_BASE}/client/input?${searchParams.toString()}`);
    const data: PrintableKey = await response.json();

    return data;
}

export async function getMinecraftKeybinds(): Promise<MinecraftKeybind[]> {
    const response = await fetch(`${API_BASE}/client/keybinds`);
    const data: MinecraftKeybind[] = await response.json();

    return data;
}

export async function getRegistryItems(name: string): Promise<Record<string, RegistryItem>> {
    const response = await fetch(`${API_BASE}/client/registry/${name}`);
    const data: Record<string, RegistryItem> = await response.json();

    return data;
}

export async function getSession(): Promise<Session> {
    const response = await fetch(`${API_BASE}/client/session`);
    const data: Session = await response.json();

    return data;
}

export async function browse(target: string) {
    await fetch(`${API_BASE}/client/browse`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({target})
    });
}

export async function browsePath(path: string) {
    await fetch(`${API_BASE}/client/browsePath`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({path})
    });
}

export async function exitClient() {
    await fetch(`${API_BASE}/client/exit`, {
        method: "POST"
    });
}

export async function openScreen(name: string) {
    await fetch(`${API_BASE}/client/screen`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({name})
    });
}

export async function deleteScreen() {
    await fetch(`${API_BASE}/client/screen`, {
        method: "DELETE"
    });
}
