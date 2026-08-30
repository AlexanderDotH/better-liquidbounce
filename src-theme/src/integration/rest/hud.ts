import type {
    Alignment,
    ConfigurableSetting,
    GameWindow,
    HudComponent,
    HudComponentCatalogEntry,
    Theme,
} from "../types";
import {API_BASE} from "./base";

export async function getGameWindow(): Promise<GameWindow> {
    const response = await fetch(`${API_BASE}/client/window`);
    const data: GameWindow = await response.json();

    return data;
}

export async function setHudEditorSelected(selected: boolean): Promise<void> {
    await fetch(`${API_BASE}/client/hud-editor`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({selected})
    });
}

/**
 * @param id Use the ID from [getMetadata].
 */
export async function getTheme(id: string): Promise<Theme> {
    const response = await fetch(`${API_BASE}/client/theme/${id}`);
    return await response.json();
}

/**
 * @param id Use the ID from [getMetadata].
 */
export async function getComponents(id: string): Promise<HudComponent[]> {
    const response = await fetch(`${API_BASE}/client/components/${id}`);
    return await response.json();
}

export async function getNativeComponents(): Promise<HudComponent[]> {
    const response = await fetch(`${API_BASE}/client/components/native`);
    return await response.json();
}

export async function getComponentCatalog(id: string): Promise<HudComponentCatalogEntry[]> {
    const response = await fetch(`${API_BASE}/client/components/${id}/catalog`);
    return await response.json();
}

export async function addComponent(id: string): Promise<void> {
    const response = await fetch(`${API_BASE}/client/components/${id}`, {
        method: "POST"
    });

    if (!response.ok) {
        throw new Error("Failed to add HUD component");
    }
}

export async function setComponentAlignment(id: string, alignment: Alignment): Promise<void> {
    await fetch(`${API_BASE}/client/components/${id}/alignment`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(alignment)
    });
}

export async function bringComponentToFront(id: string): Promise<number> {
    const response = await fetch(`${API_BASE}/client/components/${id}/z-index`, {
        method: "POST"
    });

    const data: { zIndex: number } = await response.json();
    return data.zIndex;
}

export async function getComponentSettings(id: string): Promise<ConfigurableSetting> {
    const response = await fetch(`${API_BASE}/client/components/${id}/settings`);
    return await response.json();
}

export function getComponentFileUrl(id: string, cacheKey?: string): string {
    const url = `${API_BASE}/client/components/${id}/file`;
    return cacheKey === undefined ? url : `${url}?v=${encodeURIComponent(cacheKey)}`;
}

export async function setComponentSettings(id: string, settings: ConfigurableSetting): Promise<void> {
    await fetch(`${API_BASE}/client/components/${id}/settings`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(settings)
    });
}
