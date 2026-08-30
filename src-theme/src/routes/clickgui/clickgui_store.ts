import {type Writable, writable} from "svelte/store";

export {os} from "../../integration/clientEnvironment";

export {clickGuiThemeSession} from "./theme/themeSession";
export type {
    ClickGuiThemeSession,
    ClickGuiThemeSessionState,
    ClickGuiView,
    ClickGuiVisualTheme,
} from "./theme/clickGuiThemeState";

export interface TDescription {
    description: string;
    anchor: "left" | "right",
    x: number;
    y: number;
    variant?: "extended";
}

export const description: Writable<TDescription | null> = writable(null);

export const maxPanelZIndex: Writable<number> = writable(0);

export const highlightModuleName: Writable<string | null> = writable(null);

export const scaleFactor: Writable<number> = writable(2);

export const shiftHeld: Writable<boolean> = writable(false);

export const showGrid: Writable<boolean> = writable(false);

export const snappingEnabled: Writable<boolean> = writable(true);

export const gridSize: Writable<number> = writable(10);

export const darken = writable(true);
