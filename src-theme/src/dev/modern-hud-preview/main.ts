import {mount} from "svelte";
import "../../app.scss";
import "./preview.scss";
import {createModernHudPreviewState} from "./previewFixture";
import {installModernHudPreviewRuntime} from "./previewRuntime";

const previewState = createModernHudPreviewState();
const previewRuntime = installModernHudPreviewRuntime(previewState);
applyPreviewColors();

const {default: Hud} = await import("../../routes/hud/Hud.svelte");
const target = document.getElementById("preview-app");
if (!target) {
    throw new Error("Modern HUD preview target is missing.");
}

mount(Hud, {target});
previewRuntime.start();

window.addEventListener("beforeunload", () => previewRuntime.dispose(), {once: true});

const previewWindow = window as Window & {
    __LIQUIDBOUNCE_MODERN_HUD_PREVIEW__?: {
        state: typeof previewState;
        emit: typeof previewRuntime.emit;
    };
};
previewWindow.__LIQUIDBOUNCE_MODERN_HUD_PREVIEW__ = {
    state: previewState,
    emit: previewRuntime.emit,
};

function applyPreviewColors(): void {
    const root = document.documentElement.style;
    root.setProperty("--accent-color", "#7897d6");
    root.setProperty("--accent-hover-color", "#8aa7e0");
    root.setProperty("--accent-subtle-background-color", "rgba(120, 151, 214, 0.13)");
    root.setProperty("--surface-color", "#090b0f");
    root.setProperty("--grid-color", "rgba(120, 151, 214, 0.16)");
}
