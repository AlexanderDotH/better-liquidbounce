import type {ConfigurableSetting, HudComponent, MinecraftKeyboardKey, MinecraftMouseKey, Screen} from "../types";

export interface ThemeColorChangeEvent {
    themeId: string;
    name: "Accent" | "Tint";
    value: number;
}

export interface ClickGuiValueChangeEvent {
    configurable: ConfigurableSetting;
}

export interface HudValueChangeEvent {
    configurable: ConfigurableSetting;
}

export interface ModuleToggleEvent {
    moduleName: string;
    hidden: boolean;
    enabled: boolean;
}

export interface KeyboardKeyEvent {
    keyCode: number;
    scanCode: number;
    action: number;
    mods: number;
    key: MinecraftKeyboardKey;
    screen: Screen | undefined;
}

export interface MouseButtonEvent {
    key: MinecraftMouseKey;
    button: number;
    action: number;
    mods: number;
    screen: Screen | undefined;
}

export interface KeyboardCharEvent {
    codePoint: number;
}

export interface ScaleFactorChangeEvent {
    scaleFactor: number;
}

export type ComponentsUpdateEvent =
    | {
        source: "native";
        components: HudComponent[];
    }
    | {
        source: "theme";
        themeId: string;
        components: HudComponent[];
    };
