import {get, writable} from "svelte/store";
import type {ConfigurableSetting} from "../../../integration/types";
import type {HudThemeSession, HudThemeSessionDependencies} from "./hudThemeState.ts";
import {
    describeError,
    initialState,
    parseHudVisualTheme,
    replaceHudVisualTheme,
    type HudThemeSessionState,
    type HudVisualTheme,
} from "./hudThemeSupport.ts";

export class HudThemeSessionController implements HudThemeSession {
    private readonly state = writable<HudThemeSessionState>(initialState());
    private readonly dependencies: HudThemeSessionDependencies;
    private activeLoad: Promise<boolean> | null = null;
    private synchronizedWhileSaving: ConfigurableSetting | null = null;
    readonly subscribe = this.state.subscribe;

    constructor(dependencies: HudThemeSessionDependencies) {
        this.dependencies = dependencies;
    }

    load(): Promise<boolean> {
        if (this.activeLoad) return this.activeLoad;
        const request = this.performLoad();
        this.activeLoad = request;
        void request.then(() => {
            if (this.activeLoad === request) this.activeLoad = null;
        });
        return request;
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
            theme: parseHudVisualTheme(settings),
            loading: false,
            loadError: null,
        }));
    }

    async selectTheme(theme: HudVisualTheme): Promise<boolean> {
        const current = get(this.state);
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

    private async performLoad(): Promise<boolean> {
        this.state.update(current => ({...current, loading: true, loadError: null}));
        try {
            const settings = await this.dependencies.loadSettings();
            this.state.update(current => ({
                ...current,
                settings,
                theme: parseHudVisualTheme(settings),
                loading: false,
                loadError: null,
            }));
            return true;
        } catch (error) {
            this.state.update(current => ({
                ...current,
                loading: false,
                loadError: describeError(error, "Unable to load HUD settings."),
            }));
            return false;
        }
    }

    private reportMissingSettings(theme: HudVisualTheme): false {
        this.state.update(previous => ({
            ...previous,
            saveError: "HUD settings are not loaded yet.",
            failedTheme: theme,
        }));
        return false;
    }

    private async saveTheme(
        current: HudThemeSessionState,
        currentSettings: ConfigurableSetting,
        theme: HudVisualTheme,
    ): Promise<boolean> {
        const previousTheme = current.theme;
        const nextSettings = replaceHudVisualTheme(currentSettings, theme);
        this.synchronizedWhileSaving = null;
        this.state.update(previous => ({
            ...previous,
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

    private confirmTheme(nextSettings: ConfigurableSetting, theme: HudVisualTheme): void {
        const synchronized = this.consumeSynchronizedSettings();
        const settings = synchronized ? replaceHudVisualTheme(synchronized, theme) : nextSettings;
        this.state.update(previous => ({
            ...previous,
            settings,
            theme,
            saving: false,
            saveError: null,
            failedTheme: null,
        }));
    }

    private restoreTheme(
        currentSettings: ConfigurableSetting,
        previousTheme: HudVisualTheme,
        failedTheme: HudVisualTheme,
        error: unknown,
    ): void {
        const synchronized = this.consumeSynchronizedSettings();
        const settings = synchronized ? replaceHudVisualTheme(synchronized, previousTheme) : currentSettings;
        this.state.update(previous => ({
            ...previous,
            settings,
            theme: previousTheme,
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
