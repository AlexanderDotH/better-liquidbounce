import {get, writable} from "svelte/store";
import type {ConfigurableSetting} from "../../../integration/types";
import type {
    ClickGuiThemeSession,
    ClickGuiThemeSessionDependencies,
} from "./clickGuiThemeState.ts";
import {
    describeError,
    initialState,
    parseClickGuiVisualTheme,
    replaceClickGuiVisualTheme,
    type ClickGuiThemeSessionState,
    type ClickGuiView,
    type ClickGuiVisualTheme,
} from "./clickGuiThemeSupport.ts";

export class ClickGuiThemeSessionController implements ClickGuiThemeSession {
    private readonly state = writable<ClickGuiThemeSessionState>(initialState());
    private readonly dependencies: ClickGuiThemeSessionDependencies;
    private loadRequest = 0;
    private synchronizedWhileSaving: ConfigurableSetting | null = null;
    readonly subscribe = this.state.subscribe;

    constructor(dependencies: ClickGuiThemeSessionDependencies) {
        this.dependencies = dependencies;
    }

    async load(): Promise<boolean> {
        const request = ++this.loadRequest;
        this.state.update(current => ({...current, loading: true, loadError: null}));
        try {
            const settings = await this.dependencies.loadSettings();
            if (request !== this.loadRequest) return false;
            this.state.update(current => ({
                ...current,
                settings,
                theme: parseClickGuiVisualTheme(settings),
                loading: false,
                loadError: null,
            }));
            return true;
        } catch (error) {
            if (request !== this.loadRequest) return false;
            this.state.update(current => ({
                ...current,
                loading: false,
                loadError: describeError(error, "Unable to load ClickGUI settings."),
            }));
            return false;
        }
    }

    synchronize(settings: ConfigurableSetting): void {
        const current = get(this.state);
        if (current.saving) {
            this.synchronizedWhileSaving = settings;
            return;
        }
        this.state.update(previous => ({
            ...previous,
            settings,
            theme: parseClickGuiVisualTheme(settings),
            loading: false,
            loadError: null,
        }));
    }

    setView(view: ClickGuiView): void {
        this.state.update(current => current.view === view ? current : {...current, view});
    }

    async selectTheme(theme: ClickGuiVisualTheme): Promise<boolean> {
        const current = get(this.state);
        this.setView("settings");
        if (current.saving) return false;
        if (current.theme === theme && current.failedTheme === null) {
            this.state.update(previous => ({...previous, saveError: null}));
            return true;
        }
        if (!current.settings) return this.reportMissingSettings(theme);
        return this.saveTheme(current, current.settings, theme);
    }

    async retryThemeSave(): Promise<boolean> {
        const failedTheme = get(this.state).failedTheme;
        return failedTheme ? this.selectTheme(failedTheme) : false;
    }

    private reportMissingSettings(theme: ClickGuiVisualTheme): false {
        this.state.update(previous => ({
            ...previous,
            saveError: "ClickGUI settings are not loaded yet.",
            failedTheme: theme,
        }));
        return false;
    }

    private async saveTheme(
        current: ClickGuiThemeSessionState,
        currentSettings: ConfigurableSetting,
        theme: ClickGuiVisualTheme,
    ): Promise<boolean> {
        const previousTheme = current.theme;
        const nextSettings = replaceClickGuiVisualTheme(currentSettings, theme);
        this.synchronizedWhileSaving = null;
        this.state.update(previous => ({
            ...previous,
            view: "settings",
            saving: true,
            saveError: null,
            failedTheme: null,
        }));
        try {
            await this.dependencies.saveSettings(nextSettings);
            this.confirmTheme(nextSettings, theme);
            return true;
        } catch (error) {
            this.restoreTheme(currentSettings, previousTheme, theme, error);
            return false;
        }
    }

    private confirmTheme(nextSettings: ConfigurableSetting, theme: ClickGuiVisualTheme): void {
        const synchronized = this.consumeSynchronizedSettings();
        const settings = synchronized ? replaceClickGuiVisualTheme(synchronized, theme) : nextSettings;
        this.state.update(previous => ({
            ...previous,
            settings,
            theme,
            view: "settings",
            saving: false,
            saveError: null,
            failedTheme: null,
        }));
    }

    private restoreTheme(
        currentSettings: ConfigurableSetting,
        previousTheme: ClickGuiVisualTheme,
        failedTheme: ClickGuiVisualTheme,
        error: unknown,
    ): void {
        const synchronized = this.consumeSynchronizedSettings();
        const settings = synchronized
            ? replaceClickGuiVisualTheme(synchronized, previousTheme)
            : currentSettings;
        this.state.update(previous => ({
            ...previous,
            settings,
            theme: previousTheme,
            view: "settings",
            saving: false,
            saveError: describeError(error, `Unable to switch to ${failedTheme}.`),
            failedTheme,
        }));
    }

    private consumeSynchronizedSettings(): ConfigurableSetting | null {
        const settings = this.synchronizedWhileSaving;
        this.synchronizedWhileSaving = null;
        return settings;
    }
}
