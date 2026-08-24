import {listen} from "../../../../integration/ws";
import type {KeyboardCharEvent, KeyboardKeyEvent, VirtualScreenEvent} from "../../../../integration/events";
import type {Screen} from "../../../../integration/types";
import {getClipboardText, setClipboardText, setTyping} from "../../../../integration/rest";
import {isClickGuiScreen} from "../../../../util/utils";
import {
    copyTextSelection,
    cutTextSelection,
    deleteTextBackward,
    deleteTextForward,
    pasteTextSelection,
    type TextEdit,
    type TextSelection,
} from "./textEditing";

const GLFW_PRESS = 1;
const GLFW_REPEAT = 2;
const GLFW_MOD_CONTROL = 0x0002;
const GLFW_MOD_SUPER = 0x0008;

const KEY_BACKSPACE = 259;
const KEY_DELETE = 261;
const KEY_LEFT = 263;
const KEY_RIGHT = 262;
const KEY_HOME = 268;
const KEY_END = 269;
const KEY_C = 67;
const KEY_V = 86;
const KEY_X = 88;
const KEY_A = 65;

export type CefTextInputOptions = {
    getValue: () => string;
    onChange: (value: string) => void;
    screenNames?: string[];
    isActiveScreen?: (screen: Screen | undefined) => boolean;
};

const DEFAULT_SCREEN_NAMES = ["clickgui"];

function activeScreenNames(options: CefTextInputOptions) {
    return options.screenNames ?? DEFAULT_SCREEN_NAMES;
}

function virtualScreenName(event: VirtualScreenEvent) {
    return (event as VirtualScreenEvent & { screenName?: string }).screenName ?? event.type;
}

function isActiveVirtualScreen(name: string | undefined, options: CefTextInputOptions) {
    return name !== undefined && activeScreenNames(options).includes(name);
}

function isCustomVirtualScreen(screen: Screen | undefined, options: CefTextInputOptions) {
    if (screen === undefined || !screen.class.startsWith("net.ccbluex.liquidbounce")) {
        return false;
    }

    return activeScreenNames(options).some((name) => screen.title === `VS-${name.toUpperCase()}`);
}

function isActiveKeyboardScreen(screen: Screen | undefined, options: CefTextInputOptions) {
    return options.isActiveScreen?.(screen) === true || isClickGuiScreen(screen) || isCustomVirtualScreen(screen, options);
}

function readSelection(input: HTMLInputElement): TextSelection {
    const fallback = input.value.length;
    return {
        start: input.selectionStart ?? fallback,
        end: input.selectionEnd ?? fallback,
    };
}

function restoreSelection(input: HTMLInputElement, selection: TextSelection) {
    const start = Math.max(0, Math.min(input.value.length, selection.start));
    const end = Math.max(start, Math.min(input.value.length, selection.end));
    input.setSelectionRange(start, end);
}

function moveCursor(input: HTMLInputElement, delta: number) {
    const pos = Math.max(0, Math.min(input.value.length, (input.selectionStart ?? 0) + delta));
    input.setSelectionRange(pos, pos);
}

function selectAll(input: HTMLInputElement) {
    input.setSelectionRange(0, input.value.length);
}

function shortcutModifier(mods: number) {
    return (mods & GLFW_MOD_CONTROL) !== 0 || (mods & GLFW_MOD_SUPER) !== 0;
}

export function cefTextInput(node: HTMLInputElement, options: CefTextInputOptions) {
    let focused = false;
    let screenActive = false;
    let focusRevision = 0;

    function syncDisplay(selection?: TextSelection) {
        node.value = options.getValue() ?? "";
        if (selection !== undefined && focused) {
            restoreSelection(node, selection);
        }
    }

    function commit(edit: TextEdit) {
        options.onChange(edit.value);
        syncDisplay(edit.selection);
    }

    function releaseFocus() {
        if (!focused && document.activeElement !== node) {
            return;
        }

        focused = false;
        focusRevision += 1;
        if (document.activeElement === node) {
            node.blur();
        }
        void setTyping(false);
    }

    function handleFocusIn() {
        focused = true;
        focusRevision += 1;
        syncDisplay();
        void setTyping(true);
    }

    function handleFocusOut() {
        if (!focused) {
            return;
        }

        focused = false;
        focusRevision += 1;
        void setTyping(false);
    }

    function handlePointerDown(event: PointerEvent) {
        if (event.composedPath().includes(node)) {
            return;
        }

        releaseFocus();
    }

    listen("virtualScreen", (event: VirtualScreenEvent) => {
        if (event.action === "close") {
            if (isActiveVirtualScreen(virtualScreenName(event), options)) {
                screenActive = false;
                releaseFocus();
            }
            return;
        }

        if (isActiveVirtualScreen(virtualScreenName(event), options)) {
            screenActive = true;
        }
    });

    listen("keyboardChar", (event: KeyboardCharEvent) => {
        if (!focused || document.activeElement !== node || !screenActive) {
            return;
        }

        const char = String.fromCodePoint(event.codePoint);
        if (!char || char.charCodeAt(0) < 32) {
            return;
        }

        commit(pasteTextSelection(node.value, readSelection(node), char));
    });

    listen("keyboardKey", async (event: KeyboardKeyEvent) => {
        screenActive = isActiveKeyboardScreen(event.screen, options);

        if (!screenActive) {
            releaseFocus();
            return;
        }

        if (!focused || document.activeElement !== node) {
            return;
        }

        if (event.action !== GLFW_PRESS && event.action !== GLFW_REPEAT) {
            return;
        }

        const shortcut = shortcutModifier(event.mods);

        if (shortcut && event.keyCode === KEY_C) {
            const {clipboardText} = copyTextSelection(node.value, readSelection(node));
            if (clipboardText) {
                await setClipboardText(clipboardText);
            }
            return;
        }

        if (shortcut && event.keyCode === KEY_X) {
            const edit = cutTextSelection(node.value, readSelection(node));
            if (edit.clipboardText) {
                commit(edit);
                await setClipboardText(edit.clipboardText);
            }
            return;
        }

        if (shortcut && event.keyCode === KEY_V) {
            const requestedForFocusRevision = focusRevision;
            const text = await getClipboardText();
            if (
                text
                && focused
                && document.activeElement === node
                && screenActive
                && requestedForFocusRevision === focusRevision
            ) {
                commit(pasteTextSelection(node.value, readSelection(node), text));
            }
            return;
        }

        if (shortcut && event.keyCode === KEY_A) {
            selectAll(node);
            return;
        }

        switch (event.keyCode) {
            case KEY_BACKSPACE:
                commit(deleteTextBackward(node.value, readSelection(node)));
                break;
            case KEY_DELETE:
                commit(deleteTextForward(node.value, readSelection(node)));
                break;
            case KEY_LEFT:
                moveCursor(node, -1);
                break;
            case KEY_RIGHT:
                moveCursor(node, 1);
                break;
            case KEY_HOME:
                node.setSelectionRange(0, 0);
                break;
            case KEY_END:
                node.setSelectionRange(node.value.length, node.value.length);
                break;
        }
    });

    node.addEventListener("focusin", handleFocusIn);
    node.addEventListener("focusout", handleFocusOut);
    document.addEventListener("pointerdown", handlePointerDown, true);
    window.addEventListener("blur", releaseFocus);
    syncDisplay();

    return {
        update(next: CefTextInputOptions) {
            options = next;
            syncDisplay();
        },
        destroy() {
            node.removeEventListener("focusin", handleFocusIn);
            node.removeEventListener("focusout", handleFocusOut);
            document.removeEventListener("pointerdown", handlePointerDown, true);
            window.removeEventListener("blur", releaseFocus);
            releaseFocus();
        },
    };
}
