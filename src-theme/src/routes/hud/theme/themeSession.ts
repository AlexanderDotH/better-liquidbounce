import {REST_BASE} from "../../../integration/host";
import type {ConfigurableSetting} from "../../../integration/types";
import {createHudThemeSession} from "./hudThemeState";

const HUD_MODULE_NAME = "HUD";

function hudSettingsUrl(): string {
    const query = new URLSearchParams({name: HUD_MODULE_NAME});
    return `${REST_BASE}/api/v1/client/modules/settings?${query.toString()}`;
}

async function loadHudSettings(): Promise<ConfigurableSetting> {
    const response = await fetch(hudSettingsUrl());
    await ensureSuccessfulResponse(response, "load");

    const settings: unknown = await response.json();
    if (!isConfigurableSetting(settings)) {
        throw new Error("The server returned an invalid HUD settings payload.");
    }

    return settings;
}

async function saveHudSettings(settings: ConfigurableSetting): Promise<void> {
    const response = await fetch(hudSettingsUrl(), {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(settings),
    });

    await ensureSuccessfulResponse(response, "save");
}

async function ensureSuccessfulResponse(
    response: Response,
    action: "load" | "save",
): Promise<void> {
    if (response.ok) {
        return;
    }

    const details = (await response.text()).trim().slice(0, 240);
    const status = `${response.status} ${response.statusText}`.trim();
    const suffix = details ? `: ${details}` : "";
    throw new Error(`Failed to ${action} HUD settings (${status})${suffix}`);
}

function isConfigurableSetting(value: unknown): value is ConfigurableSetting {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
        return false;
    }

    const setting = value as Partial<ConfigurableSetting>;
    return setting.name === HUD_MODULE_NAME && Array.isArray(setting.value);
}

export const hudThemeSession = createHudThemeSession({
    loadSettings: loadHudSettings,
    saveSettings: saveHudSettings,
});
