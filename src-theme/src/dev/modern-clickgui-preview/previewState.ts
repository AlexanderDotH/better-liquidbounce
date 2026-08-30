import type {ConfigurableSetting, Module as ClickGuiModule, PersistentStorageItem} from "../../integration/types";
import {createAutoShopSettings, createClickGuiSettings, createComprehensiveModuleSettings, createDefaultModuleSettings, createGlobalSettings, createPreviewModules} from "./previewModules.ts";
import {createInitialPersistentItems} from "./previewEnvironment.ts";

export interface PreviewRequestRecord {
    method: string;
    path: string;
}

export interface ModernClickGuiPreviewState {
    modules: ClickGuiModule[];
    moduleSettings: Record<string, ConfigurableSetting>;
    globalSettings: ConfigurableSetting;
    persistentItems: PersistentStorageItem[];
    typing: boolean;
    clipboardText: string;
    requests: PreviewRequestRecord[];
}

const API_PREFIX = "/api/v1/client";

export function createModernClickGuiPreviewState(): ModernClickGuiPreviewState {
    const modules = createPreviewModules();
    const moduleSettings = Object.fromEntries(
        modules.map(module => [module.name, createDefaultModuleSettings(module.name)]),
    );
    moduleSettings.KillAura = createComprehensiveModuleSettings();
    moduleSettings.AutoShop = createAutoShopSettings();
    moduleSettings.ClickGUI = createClickGuiSettings();

    return {
        modules,
        moduleSettings,
        globalSettings: createGlobalSettings(),
        persistentItems: createInitialPersistentItems(),
        typing: false,
        clipboardText: "preview-player",
        requests: [],
    };
}
