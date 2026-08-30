import type {Readable} from "svelte/store";
import type {ConfigurableSetting} from "../../../integration/types";
import {HudThemeSessionController} from "./hudThemeSessionController.ts";
import type {HudThemeSessionState, HudVisualTheme} from "./hudThemeSupport.ts";

export {
    DEFAULT_HUD_VISUAL_THEME,
    HUD_VISUAL_THEMES,
    parseHudVisualTheme,
    replaceHudVisualTheme,
} from "./hudThemeSupport.ts";
export type {HudThemeSessionState, HudVisualTheme} from "./hudThemeSupport.ts";

export interface HudThemeSessionDependencies {
    loadSettings(): Promise<ConfigurableSetting>;
    saveSettings(settings: ConfigurableSetting): Promise<void>;
}

export interface HudThemeSession extends Readable<HudThemeSessionState> {
    load(): Promise<boolean>;
    synchronize(settings: ConfigurableSetting): void;
    selectTheme(theme: HudVisualTheme): Promise<boolean>;
    retryThemeSave(): Promise<boolean>;
}

export function createHudThemeSession(
    dependencies: HudThemeSessionDependencies,
): HudThemeSession {
    const controller = new HudThemeSessionController(dependencies);
    return {
        subscribe: controller.subscribe,
        load: () => controller.load(),
        synchronize: settings => controller.synchronize(settings),
        selectTheme: theme => controller.selectTheme(theme),
        retryThemeSave: () => controller.retryThemeSave(),
    };
}
