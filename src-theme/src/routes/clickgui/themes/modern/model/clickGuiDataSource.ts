import {REST_BASE} from "../../../../../integration/host";
import type {ConfigurableSetting, Module} from "../../../../../integration/types";
import {ensureSuccessfulResponse} from "./checkedResponse";

export interface ClickGuiDataSource {
    getModules(): Promise<Module[]>;
    getModuleSettings(name: string): Promise<ConfigurableSetting>;
    setModuleSettings(name: string, settings: ConfigurableSetting): Promise<void>;
    setModuleEnabled(name: string, enabled: boolean): Promise<void>;
    setTyping(typing: boolean): Promise<void>;
}

export interface GlobalSettingsDataSource {
    getGlobalSettings(): Promise<ConfigurableSetting>;
    setGlobalSettings(settings: ConfigurableSetting): Promise<void>;
}

const CLIENT_API_BASE = `${REST_BASE}/api/v1/client`;

export const productionClickGuiDataSource: ClickGuiDataSource = {
    getModules: () => requestJson<Module[]>(
        `${CLIENT_API_BASE}/modules`,
        "load modules",
    ),
    getModuleSettings: name => requestJson<ConfigurableSetting>(
        moduleSettingsUrl(name),
        `load ${name} settings`,
    ),
    setModuleSettings: (name, settings) => requestVoid(
        moduleSettingsUrl(name),
        {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(settings),
        },
        `save ${name} settings`,
    ),
    setModuleEnabled: (name, enabled) => requestVoid(
        `${CLIENT_API_BASE}/modules/toggle`,
        {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({name, enabled}),
        },
        `change ${name}`,
    ),
    setTyping: typing => requestVoid(
        `${CLIENT_API_BASE}/typing`,
        {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({typing}),
        },
        "update typing state",
    ),
};

export const productionGlobalSettingsDataSource: GlobalSettingsDataSource = {
    getGlobalSettings: () => requestJson<ConfigurableSetting>(
        `${CLIENT_API_BASE}/global`,
        "load global settings",
    ),
    setGlobalSettings: settings => requestVoid(
        `${CLIENT_API_BASE}/global`,
        {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(settings),
        },
        "save global settings",
    ),
};

function moduleSettingsUrl(name: string): string {
    const query = new URLSearchParams({name});
    return `${CLIENT_API_BASE}/modules/settings?${query.toString()}`;
}

async function requestJson<T>(url: string, action: string): Promise<T> {
    const response = await fetch(url);
    await ensureSuccessfulResponse(response, action);
    return await response.json() as T;
}

async function requestVoid(
    url: string,
    init: RequestInit,
    action: string,
): Promise<void> {
    const response = await fetch(url, init);
    await ensureSuccessfulResponse(response, action);
}
