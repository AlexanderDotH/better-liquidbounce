import type {VirtualScreenEvent} from "../events";
import type {Screen} from "../types";
import {isClickGuiScreen} from "../../util/utils";
import type {TextSelection} from "./textEditing";

export const GLFW_PRESS = 1;
export const GLFW_REPEAT = 2;
export const GLFW_MOD_CONTROL = 0x0002;
export const GLFW_MOD_SUPER = 0x0008;

export const KEY_BACKSPACE = 259;
export const KEY_DELETE = 261;
export const KEY_LEFT = 263;
export const KEY_RIGHT = 262;
export const KEY_HOME = 268;
export const KEY_END = 269;
export const KEY_C = 67;
export const KEY_V = 86;
export const KEY_X = 88;
export const KEY_A = 65;

export type CefTextInputOptions = {
    getValue: () => string;
    onChange: (value: string) => void;
    screenNames?: string[];
    isActiveScreen?: (screen: Screen | undefined) => boolean;
};

const DEFAULT_SCREEN_NAMES = ["clickgui"];

export function activeScreenNames(options: CefTextInputOptions) {
    return options.screenNames ?? DEFAULT_SCREEN_NAMES;
}

export function virtualScreenName(event: VirtualScreenEvent) {
    return (event as VirtualScreenEvent & { screenName?: string }).screenName ?? event.type;
}

export function isActiveVirtualScreen(name: string | undefined, options: CefTextInputOptions) {
    return name !== undefined && activeScreenNames(options).includes(name);
}

export function isCustomVirtualScreen(screen: Screen | undefined, options: CefTextInputOptions) {
    if (screen === undefined || !screen.class.startsWith("net.ccbluex.liquidbounce")) {
        return false;
    }

    return activeScreenNames(options).some((name) => screen.title === `VS-${name.toUpperCase()}`);
}

export function isActiveKeyboardScreen(screen: Screen | undefined, options: CefTextInputOptions) {
    return options.isActiveScreen?.(screen) === true || isClickGuiScreen(screen) || isCustomVirtualScreen(screen, options);
}

export function readSelection(input: HTMLInputElement): TextSelection {
    const fallback = input.value.length;
    return {
        start: input.selectionStart ?? fallback,
        end: input.selectionEnd ?? fallback,
    };
}

export function restoreSelection(input: HTMLInputElement, selection: TextSelection) {
    const start = Math.max(0, Math.min(input.value.length, selection.start));
    const end = Math.max(start, Math.min(input.value.length, selection.end));
    input.setSelectionRange(start, end);
}

export function moveCursor(input: HTMLInputElement, delta: number) {
    const pos = Math.max(0, Math.min(input.value.length, (input.selectionStart ?? 0) + delta));
    input.setSelectionRange(pos, pos);
}

export function selectAll(input: HTMLInputElement) {
    input.setSelectionRange(0, input.value.length);
}

export function shortcutModifier(mods: number) {
    return (mods & GLFW_MOD_CONTROL) !== 0 || (mods & GLFW_MOD_SUPER) !== 0;
}
