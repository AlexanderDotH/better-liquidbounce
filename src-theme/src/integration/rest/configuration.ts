import type {ConfigurableSetting, Metadata, Module, PersistentStorageItem} from "../types";
import {API_BASE} from "./base";

export async function getMetadata(): Promise<Metadata> {
    const response = await fetch(`metadata.json`);
    const data: Metadata = await response.json();

    return data;
}

export async function getModules(): Promise<Module[]> {
    const response = await fetch(`${API_BASE}/client/modules`);
    const data: [Module] = await response.json();

    return data;
}

export async function getModule(name: string): Promise<Module> {
    const response = await fetch(`${API_BASE}/client/module/${name}`);
    const data = await response.json();

    return data;
}

export async function getModuleSettings(name: string): Promise<ConfigurableSetting> {
    const searchParams = new URLSearchParams({name});

    const response = await fetch(`${API_BASE}/client/modules/settings?${searchParams.toString()}`);
    const data = await response.json();

    return data;
}

export async function setModuleSettings(name: string, settings: ConfigurableSetting) {
    const searchParams = new URLSearchParams({name});

    await fetch(`${API_BASE}/client/modules/settings?${searchParams.toString()}`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(settings)
    });
}

export async function getSpooferSettings(): Promise<ConfigurableSetting> {
    const response = await fetch(`${API_BASE}/client/spoofer`);
    const data = await response.json();

    return data;
}

export async function setSpooferSettings(settings: ConfigurableSetting) {
    await fetch(`${API_BASE}/client/spoofer`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(settings)
    });
}

export async function getGlobalSettings(): Promise<ConfigurableSetting> {
    const response = await fetch(`${API_BASE}/client/global`);
    const data = await response.json();

    return data;
}

export async function setGlobalSettings(settings: ConfigurableSetting) {
    await fetch(`${API_BASE}/client/global`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(settings)
    });
}

export async function setModuleEnabled(name: string, enabled: boolean) {
    await fetch(`${API_BASE}/client/modules/toggle`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            name,
            enabled
        })
    });
}

export async function getPersistentStorageItems(): Promise<PersistentStorageItem[]> {
    const response = await fetch(`${API_BASE}/client/localStorage/all`);
    const data: PersistentStorageItem[] = (await response.json()).items;

    return data;
}

export async function setPersistentStorageItems(items: PersistentStorageItem[]) {
    await fetch(`${API_BASE}/client/localStorage/all`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({items})
    })
}
