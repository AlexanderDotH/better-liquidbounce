import {REST_BASE} from "../../../integration/host";
import type {ConfigurableSetting} from "../../../integration/types";
import {createClickGuiThemeSession} from "./clickGuiThemeState";

const CLICK_GUI_MODULE_NAME = "ClickGUI";

function clickGuiSettingsUrl(): string {
    const query = new URLSearchParams({name: CLICK_GUI_MODULE_NAME});
    return `${REST_BASE}/api/v1/client/modules/settings?${query.toString()}`;
}

async function loadClickGuiSettings(): Promise<ConfigurableSetting> {
    const response = await fetch(clickGuiSettingsUrl());
    await ensureSuccessfulResponse(response, "load");

    const settings: unknown = await response.json();
    if (!isConfigurableSetting(settings)) {
        throw new Error("The server returned an invalid ClickGUI settings payload.");
    }

    return settings;
}

async function saveClickGuiSettings(settings: ConfigurableSetting): Promise<void> {
    const response = await fetch(clickGuiSettingsUrl(), {
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
    throw new Error(`Failed to ${action} ClickGUI settings (${status})${suffix}`);
}

function isConfigurableSetting(value: unknown): value is ConfigurableSetting {
    if (typeof value !== "object" || value === null || Array.isArray(value)) {
        return false;
    }

    const setting = value as Partial<ConfigurableSetting>;
    return setting.name === CLICK_GUI_MODULE_NAME && Array.isArray(setting.value);
}

export const clickGuiThemeSession = createClickGuiThemeSession({
    loadSettings: loadClickGuiSettings,
    saveSettings: saveClickGuiSettings,
});
