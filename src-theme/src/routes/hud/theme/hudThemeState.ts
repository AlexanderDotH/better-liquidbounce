import {get, writable, type Readable} from "svelte/store";
import type {
    ChooseSetting,
    ConfigurableSetting,
    ModuleSetting,
} from "../../../integration/types";

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

const THEME_SETTING_NAME = "Theme";

export function parseHudVisualTheme(
    settings: ConfigurableSetting | null | undefined,
): HudVisualTheme {
    if (!Array.isArray(settings?.value)) {
        return DEFAULT_HUD_VISUAL_THEME;
    }

    const value = settings.value.find(setting => setting.name === THEME_SETTING_NAME)?.value;
    return isHudVisualTheme(value) ? value : DEFAULT_HUD_VISUAL_THEME;
}

export function replaceHudVisualTheme(
    settings: ConfigurableSetting,
    theme: HudVisualTheme,
): ConfigurableSetting {
    const existingTheme = settings.value.some(setting => setting.name === THEME_SETTING_NAME);
    const value = settings.value.map(setting => replaceThemeSetting(setting, theme));

    if (!existingTheme) {
        value.push(createThemeSetting(theme));
    }

    return {...settings, value};
}

export function createHudThemeSession(
    dependencies: HudThemeSessionDependencies,
): HudThemeSession {
    const state = writable<HudThemeSessionState>(initialState());
    let activeLoad: Promise<boolean> | null = null;
    let synchronizedWhileSaving: ConfigurableSetting | null = null;

    function load(): Promise<boolean> {
        if (activeLoad) {
            return activeLoad;
        }

        const request = performLoad();
        activeLoad = request;
        void request.then(() => {
            if (activeLoad === request) {
                activeLoad = null;
            }
        });
        return request;
    }

    async function performLoad(): Promise<boolean> {
        state.update(current => ({
            ...current,
            loading: true,
            loadError: null,
        }));

        try {
            const settings = await dependencies.loadSettings();
            state.update(current => ({
                ...current,
                settings,
                theme: parseHudVisualTheme(settings),
                loading: false,
                loadError: null,
            }));
            return true;
        } catch (error) {
            state.update(current => ({
                ...current,
                loading: false,
                loadError: describeError(error, "Unable to load HUD settings."),
            }));
            return false;
        }
    }

    function synchronize(settings: ConfigurableSetting): void {
        const current = get(state);
        if (current.saving) {
            synchronizedWhileSaving = settings;
            return;
        }

        state.update(previous => ({
            ...previous,
            settings,
            theme: parseHudVisualTheme(settings),
            loading: false,
            loadError: null,
        }));
    }

    async function selectTheme(theme: HudVisualTheme): Promise<boolean> {
        const current = get(state);
        if (current.saving) {
            return false;
        }

        if (current.theme === theme && current.failedTheme === null) {
            state.update(previous => ({
                ...previous,
                saveError: null,
            }));
            return true;
        }

        if (!current.settings) {
            state.update(previous => ({
                ...previous,
                saveError: "HUD settings are not loaded yet.",
                failedTheme: theme,
            }));
            return false;
        }

        const previousTheme = current.theme;
        const nextSettings = replaceHudVisualTheme(current.settings, theme);
        synchronizedWhileSaving = null;
        state.update(previous => ({
            ...previous,
            saving: true,
            saveError: null,
            failedTheme: null,
        }));

        try {
            await dependencies.saveSettings(nextSettings);
            const synchronized = synchronizedWhileSaving;
            synchronizedWhileSaving = null;
            const confirmedSettings = synchronized
                ? replaceHudVisualTheme(synchronized, theme)
                : nextSettings;

            state.update(previous => ({
                ...previous,
                settings: confirmedSettings,
                theme,
                saving: false,
                saveError: null,
                failedTheme: null,
            }));
            return true;
        } catch (error) {
            const synchronized = synchronizedWhileSaving;
            synchronizedWhileSaving = null;
            const restoredSettings = synchronized
                ? replaceHudVisualTheme(synchronized, previousTheme)
                : current.settings;

            state.update(previous => ({
                ...previous,
                settings: restoredSettings,
                theme: previousTheme,
                saving: false,
                saveError: describeError(error, `Unable to switch to ${theme}.`),
                failedTheme: theme,
            }));
            return false;
        }
    }

    async function retryThemeSave(): Promise<boolean> {
        const failedTheme = get(state).failedTheme;
        if (!failedTheme) {
            return false;
        }

        return selectTheme(failedTheme);
    }

    return {
        subscribe: state.subscribe,
        load,
        synchronize,
        selectTheme,
        retryThemeSave,
    };
}

function initialState(): HudThemeSessionState {
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

function replaceThemeSetting(
    setting: ModuleSetting,
    theme: HudVisualTheme,
): ModuleSetting {
    return setting.name === THEME_SETTING_NAME ? {...setting, value: theme} : setting;
}

function createThemeSetting(theme: HudVisualTheme): ChooseSetting {
    return {
        name: THEME_SETTING_NAME,
        valueType: "CHOOSE",
        value: theme,
        description: undefined,
        key: undefined,
        choices: [...HUD_VISUAL_THEMES],
    };
}

function isHudVisualTheme(value: unknown): value is HudVisualTheme {
    return typeof value === "string"
        && HUD_VISUAL_THEMES.some(theme => theme === value);
}

function describeError(error: unknown, fallback: string): string {
    if (!(error instanceof Error) || !error.message.trim()) {
        return fallback;
    }

    return `${fallback} ${error.message}`;
}
