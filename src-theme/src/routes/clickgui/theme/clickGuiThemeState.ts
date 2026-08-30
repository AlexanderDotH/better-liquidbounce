import type {Readable} from "svelte/store";
import type {ConfigurableSetting} from "../../../integration/types";
import {ClickGuiThemeSessionController} from "./clickGuiThemeSessionController.ts";
import type {
    ClickGuiThemeSessionState,
    ClickGuiView,
    ClickGuiVisualTheme,
} from "./clickGuiThemeSupport.ts";

export {
    CLICK_GUI_VISUAL_THEMES,
    DEFAULT_CLICK_GUI_VISUAL_THEME,
    parseClickGuiVisualTheme,
    replaceClickGuiVisualTheme,
} from "./clickGuiThemeSupport.ts";
export type {
    ClickGuiThemeSessionState,
    ClickGuiView,
    ClickGuiVisualTheme,
} from "./clickGuiThemeSupport.ts";

export interface ClickGuiThemeSessionDependencies {
    loadSettings(): Promise<ConfigurableSetting>;
    saveSettings(settings: ConfigurableSetting): Promise<void>;
}

export interface ClickGuiThemeSession extends Readable<ClickGuiThemeSessionState> {
    load(): Promise<boolean>;
    synchronize(settings: ConfigurableSetting): void;
    setView(view: ClickGuiView): void;
    selectTheme(theme: ClickGuiVisualTheme): Promise<boolean>;
    retryThemeSave(): Promise<boolean>;
}

export function createClickGuiThemeSession(
    dependencies: ClickGuiThemeSessionDependencies,
): ClickGuiThemeSession {
    const controller = new ClickGuiThemeSessionController(dependencies);
    return {
        subscribe: controller.subscribe,
        load: () => controller.load(),
        synchronize: settings => controller.synchronize(settings),
        setView: view => controller.setView(view),
        selectTheme: theme => controller.selectTheme(theme),
        retryThemeSave: () => controller.retryThemeSave(),
    };
}
