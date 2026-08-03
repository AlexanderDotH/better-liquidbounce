import {get, writable, type Readable} from "svelte/store";
import type {
    ChooseSetting,
    ConfigurableSetting,
    ModuleSetting,
} from "../../../integration/types";

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

const THEME_SETTING_NAME = "Theme";

export function parseClickGuiVisualTheme(
    settings: ConfigurableSetting | null | undefined,
): ClickGuiVisualTheme {
    if (!Array.isArray(settings?.value)) {
        return DEFAULT_CLICK_GUI_VISUAL_THEME;
    }

    const value = settings.value.find(setting => setting.name === THEME_SETTING_NAME)?.value;
    return isClickGuiVisualTheme(value) ? value : DEFAULT_CLICK_GUI_VISUAL_THEME;
}

export function replaceClickGuiVisualTheme(
    settings: ConfigurableSetting,
    theme: ClickGuiVisualTheme,
): ConfigurableSetting {
    const existingTheme = settings.value.some(setting => setting.name === THEME_SETTING_NAME);
    const value = settings.value.map(setting => replaceThemeSetting(setting, theme));

    if (!existingTheme) {
        value.push(createThemeSetting(theme));
    }

    return {...settings, value};
}

export function createClickGuiThemeSession(
    dependencies: ClickGuiThemeSessionDependencies,
): ClickGuiThemeSession {
    const state = writable<ClickGuiThemeSessionState>(initialState());
    let loadRequest = 0;
    let synchronizedWhileSaving: ConfigurableSetting | null = null;

    async function load(): Promise<boolean> {
        const request = ++loadRequest;
        state.update(current => ({
            ...current,
            loading: true,
            loadError: null,
        }));

        try {
            const settings = await dependencies.loadSettings();
            if (request !== loadRequest) {
                return false;
            }

            state.update(current => ({
                ...current,
                settings,
                theme: parseClickGuiVisualTheme(settings),
                loading: false,
                loadError: null,
            }));
            return true;
        } catch (error) {
            if (request !== loadRequest) {
                return false;
            }

            state.update(current => ({
                ...current,
                loading: false,
                loadError: describeError(error, "Unable to load ClickGUI settings."),
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
            theme: parseClickGuiVisualTheme(settings),
            loading: false,
            loadError: null,
        }));
    }

    function setView(view: ClickGuiView): void {
        state.update(current => current.view === view ? current : {...current, view});
    }

    async function selectTheme(theme: ClickGuiVisualTheme): Promise<boolean> {
        const current = get(state);
        setView("settings");

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
                saveError: "ClickGUI settings are not loaded yet.",
                failedTheme: theme,
            }));
            return false;
        }

        const previousTheme = current.theme;
        const nextSettings = replaceClickGuiVisualTheme(current.settings, theme);
        synchronizedWhileSaving = null;
        state.update(previous => ({
            ...previous,
            view: "settings",
            saving: true,
            saveError: null,
            failedTheme: null,
        }));

        try {
            await dependencies.saveSettings(nextSettings);
            const synchronized = synchronizedWhileSaving;
            synchronizedWhileSaving = null;
            const confirmedSettings = synchronized
                ? replaceClickGuiVisualTheme(synchronized, theme)
                : nextSettings;

            state.update(previous => ({
                ...previous,
                settings: confirmedSettings,
                theme,
                view: "settings",
                saving: false,
                saveError: null,
                failedTheme: null,
            }));
            return true;
        } catch (error) {
            const synchronized = synchronizedWhileSaving;
            synchronizedWhileSaving = null;
            const restoredSettings = synchronized
                ? replaceClickGuiVisualTheme(synchronized, previousTheme)
                : current.settings;

            state.update(previous => ({
                ...previous,
                settings: restoredSettings,
                theme: previousTheme,
                view: "settings",
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
        setView,
        selectTheme,
        retryThemeSave,
    };
}

function initialState(): ClickGuiThemeSessionState {
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

function replaceThemeSetting(
    setting: ModuleSetting,
    theme: ClickGuiVisualTheme,
): ModuleSetting {
    return setting.name === THEME_SETTING_NAME ? {...setting, value: theme} : setting;
}

function createThemeSetting(theme: ClickGuiVisualTheme): ChooseSetting {
    return {
        name: THEME_SETTING_NAME,
        valueType: "CHOOSE",
        value: theme,
        description: undefined,
        key: undefined,
        choices: [...CLICK_GUI_VISUAL_THEMES],
    };
}

function isClickGuiVisualTheme(value: unknown): value is ClickGuiVisualTheme {
    return typeof value === "string"
        && CLICK_GUI_VISUAL_THEMES.some(theme => theme === value);
}

function describeError(error: unknown, fallback: string): string {
    if (!(error instanceof Error) || !error.message.trim()) {
        return fallback;
    }

    return `${fallback} ${error.message}`;
}
