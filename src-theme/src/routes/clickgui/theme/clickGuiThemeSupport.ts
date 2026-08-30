import type {ChooseSetting, ConfigurableSetting, ModuleSetting} from "../../../integration/types";

export const CLICK_GUI_VISUAL_THEMES = ["Classic", "Modern"] as const;
export const DEFAULT_CLICK_GUI_VISUAL_THEME = "Modern";

export type ClickGuiVisualTheme = typeof CLICK_GUI_VISUAL_THEMES[number];
export type ClickGuiView = "clickgui" | "hud-editor" | "settings";

export interface ClickGuiThemeSessionState {
    settings: ConfigurableSetting | null;
    theme: ClickGuiVisualTheme;
    view: ClickGuiView;
    loading: boolean;
    saving: boolean;
    loadError: string | null;
    saveError: string | null;
    failedTheme: ClickGuiVisualTheme | null;
}

const THEME_SETTING_NAME = "Theme";

export function initialState(): ClickGuiThemeSessionState {
    return {
        settings: null,
        theme: DEFAULT_CLICK_GUI_VISUAL_THEME,
        view: "clickgui",
        loading: true,
        saving: false,
        loadError: null,
        saveError: null,
        failedTheme: null,
    };
}

export function replaceThemeSetting(
    setting: ModuleSetting,
    theme: ClickGuiVisualTheme,
): ModuleSetting {
    return setting.name === THEME_SETTING_NAME ? {...setting, value: theme} : setting;
}

export function createThemeSetting(theme: ClickGuiVisualTheme): ChooseSetting {
    return {
        name: THEME_SETTING_NAME,
        valueType: "CHOOSE",
        value: theme,
        description: undefined,
        key: undefined,
        choices: [...CLICK_GUI_VISUAL_THEMES],
    };
}

export function isClickGuiVisualTheme(value: unknown): value is ClickGuiVisualTheme {
    return typeof value === "string"
        && CLICK_GUI_VISUAL_THEMES.some(theme => theme === value);
}

export function parseClickGuiVisualTheme(
    settings: ConfigurableSetting | null | undefined,
): ClickGuiVisualTheme {
    if (!Array.isArray(settings?.value)) return DEFAULT_CLICK_GUI_VISUAL_THEME;
    const value = settings.value.find(setting => setting.name === THEME_SETTING_NAME)?.value;
    return isClickGuiVisualTheme(value) ? value : DEFAULT_CLICK_GUI_VISUAL_THEME;
}

export function replaceClickGuiVisualTheme(
    settings: ConfigurableSetting,
    theme: ClickGuiVisualTheme,
): ConfigurableSetting {
    const existingTheme = settings.value.some(setting => setting.name === THEME_SETTING_NAME);
    const value = settings.value.map(setting => replaceThemeSetting(setting, theme));
    if (!existingTheme) value.push(createThemeSetting(theme));
    return {...settings, value};
}

export function describeError(error: unknown, fallback: string): string {
    if (!(error instanceof Error) || !error.message.trim()) {
        return fallback;
    }

    return `${fallback} ${error.message}`;
}
