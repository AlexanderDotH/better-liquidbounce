import type {ChooseSetting, ConfigurableSetting, ModuleSetting} from "../../../integration/types";

export const HUD_VISUAL_THEMES = ["Classic", "Modern"] as const;
export const DEFAULT_HUD_VISUAL_THEME = "Modern";

export type HudVisualTheme = typeof HUD_VISUAL_THEMES[number];

export interface HudThemeSessionState {
    settings: ConfigurableSetting | null;
    theme: HudVisualTheme;
    loading: boolean;
    saving: boolean;
    loadError: string | null;
    saveError: string | null;
    failedTheme: HudVisualTheme | null;
}

const THEME_SETTING_NAME = "Theme";

export function initialState(): HudThemeSessionState {
    return {
        settings: null,
        theme: DEFAULT_HUD_VISUAL_THEME,
        loading: false,
        saving: false,
        loadError: null,
        saveError: null,
        failedTheme: null,
    };
}

export function replaceThemeSetting(
    setting: ModuleSetting,
    theme: HudVisualTheme,
): ModuleSetting {
    return setting.name === THEME_SETTING_NAME ? {...setting, value: theme} : setting;
}

export function createThemeSetting(theme: HudVisualTheme): ChooseSetting {
    return {
        name: THEME_SETTING_NAME,
        valueType: "CHOOSE",
        value: theme,
        description: undefined,
        key: undefined,
        choices: [...HUD_VISUAL_THEMES],
    };
}

export function isHudVisualTheme(value: unknown): value is HudVisualTheme {
    return typeof value === "string"
        && HUD_VISUAL_THEMES.some(theme => theme === value);
}

export function parseHudVisualTheme(
    settings: ConfigurableSetting | null | undefined,
): HudVisualTheme {
    if (!Array.isArray(settings?.value)) return DEFAULT_HUD_VISUAL_THEME;
    const value = settings.value.find(setting => setting.name === THEME_SETTING_NAME)?.value;
    return isHudVisualTheme(value) ? value : DEFAULT_HUD_VISUAL_THEME;
}

export function replaceHudVisualTheme(
    settings: ConfigurableSetting,
    theme: HudVisualTheme,
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
