import {mount} from "svelte";
import "../../app.scss";
import "./preview.scss";
import {
    createModernClickGuiPreviewState,
    type ModernClickGuiPreviewState,
} from "./previewFixture";
import {installModernClickGuiPreviewRuntime} from "./previewRuntime";
import type {
    ClickGuiThemeSession,
    ClickGuiVisualTheme,
} from "../../routes/clickgui/theme/clickGuiThemeState";

const previewState = createModernClickGuiPreviewState();
installModernClickGuiPreviewRuntime(previewState);
applyPreviewColors();

const {insertPersistentData} = await import("../../integration/persistent_storage");
await insertPersistentData();

const [
    {getModuleSettings, setModuleSettings},
    {createClickGuiThemeSession},
    {default: ModernTabbedClickGui},
] = await Promise.all([
    import("../../integration/rest"),
    import("../../routes/clickgui/theme/clickGuiThemeState"),
    import("../../routes/clickgui/themes/modern/ModernTabbedClickGui.svelte"),
]);

const liveSession = createClickGuiThemeSession({
    loadSettings: () => getModuleSettings("ClickGUI"),
    saveSettings: settings => setModuleSettings("ClickGUI", settings),
});
await liveSession.load();

const session = modernOnlySession(liveSession);
const target = document.getElementById("preview-app");
if (!target) {
    throw new Error("Modern ClickGUI preview target is missing.");
}
const previewTarget = target;

mount(ModernTabbedClickGui, {
    target: previewTarget,
    props: {
        session,
        nativeTextInput: true,
    },
});

const themeOptionObserver = new MutationObserver(disableClassicPreviewOption);
themeOptionObserver.observe(previewTarget, {childList: true, subtree: true});
disableClassicPreviewOption();
window.addEventListener("beforeunload", () => themeOptionObserver.disconnect(), {once: true});

const previewWindow = window as Window & {
    __LIQUIDBOUNCE_MODERN_PREVIEW__?: ModernClickGuiPreviewState;
};
previewWindow.__LIQUIDBOUNCE_MODERN_PREVIEW__ = previewState;

function modernOnlySession(session: ClickGuiThemeSession): ClickGuiThemeSession {
    return {
        subscribe: session.subscribe,
        load: session.load,
        synchronize: session.synchronize,
        setView: session.setView,
        selectTheme: (theme: ClickGuiVisualTheme) =>
            theme === "Classic" ? Promise.resolve(false) : session.selectTheme(theme),
        retryThemeSave: session.retryThemeSave,
    };
}

function disableClassicPreviewOption(): void {
    const buttons = previewTarget.querySelectorAll<HTMLButtonElement>(".theme-option");
    for (const button of buttons) {
        if (!button.textContent?.includes("Classic")) {
            continue;
        }

        button.disabled = true;
        button.title = "Classic is intentionally disabled in the Modern-only preview.";
    }
}

function applyPreviewColors(): void {
    const root = document.documentElement.style;
    root.setProperty("--accent-color", "#7897d6");
    root.setProperty("--accent-hover-color", "#8aa7e0");
    root.setProperty("--accent-subtle-background-color", "rgba(120, 151, 214, 0.13)");
    root.setProperty("--surface-color", "#090b0f");
    root.setProperty("--grid-color", "rgba(120, 151, 214, 0.16)");
}
