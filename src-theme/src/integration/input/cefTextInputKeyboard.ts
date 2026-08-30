import type {KeyboardKeyEvent} from "../events";
import {getClipboardText, setClipboardText} from "../rest";
import {
    GLFW_PRESS,
    GLFW_REPEAT,
    KEY_A,
    KEY_BACKSPACE,
    KEY_C,
    KEY_DELETE,
    KEY_END,
    KEY_HOME,
    KEY_LEFT,
    KEY_RIGHT,
    KEY_V,
    KEY_X,
    isActiveKeyboardScreen,
    moveCursor,
    readSelection,
    selectAll,
    shortcutModifier,
    type CefTextInputOptions,
} from "./cefTextInputSupport";
import {
    copyTextSelection,
    cutTextSelection,
    deleteTextBackward,
    deleteTextForward,
    pasteTextSelection,
    type TextEdit,
} from "./textEditing";

export interface CefTextInputKeyboardRuntime {
    readonly node: HTMLInputElement;
    options: CefTextInputOptions;
    focused: boolean;
    screenActive: boolean;
    focusRevision: number;
    commit(edit: TextEdit): void;
    releaseFocus(): void;
}

export async function handleCefKeyboardKey(
    runtime: CefTextInputKeyboardRuntime,
    event: KeyboardKeyEvent,
): Promise<void> {
    runtime.screenActive = isActiveKeyboardScreen(event.screen, runtime.options);
    if (!runtime.screenActive) {
        runtime.releaseFocus();
        return;
    }
    if (!runtime.focused || document.activeElement !== runtime.node) return;
    if (event.action !== GLFW_PRESS && event.action !== GLFW_REPEAT) return;
    if (await handleShortcut(runtime, event)) return;
    handleEditKey(runtime, event.keyCode);
}

async function handleShortcut(
    runtime: CefTextInputKeyboardRuntime,
    event: KeyboardKeyEvent,
): Promise<boolean> {
    if (!shortcutModifier(event.mods)) return false;
    switch (event.keyCode) {
        case KEY_C: await copySelection(runtime); return true;
        case KEY_X: await cutSelection(runtime); return true;
        case KEY_V: await pasteSelection(runtime); return true;
        case KEY_A: selectAll(runtime.node); return true;
        default: return false;
    }
}

async function copySelection(runtime: CefTextInputKeyboardRuntime): Promise<void> {
    const {clipboardText} = copyTextSelection(runtime.node.value, readSelection(runtime.node));
    if (clipboardText) await setClipboardText(clipboardText);
}

async function cutSelection(runtime: CefTextInputKeyboardRuntime): Promise<void> {
    const edit = cutTextSelection(runtime.node.value, readSelection(runtime.node));
    if (!edit.clipboardText) return;
    runtime.commit(edit);
    await setClipboardText(edit.clipboardText);
}

async function pasteSelection(runtime: CefTextInputKeyboardRuntime): Promise<void> {
    const requestedForFocusRevision = runtime.focusRevision;
    const text = await getClipboardText();
    if (!canApplyPaste(runtime, text, requestedForFocusRevision)) return;
    runtime.commit(pasteTextSelection(runtime.node.value, readSelection(runtime.node), text as string));
}

function canApplyPaste(
    runtime: CefTextInputKeyboardRuntime,
    text: string | null,
    requestedForFocusRevision: number,
): boolean {
    return Boolean(text)
        && runtime.focused
        && document.activeElement === runtime.node
        && runtime.screenActive
        && requestedForFocusRevision === runtime.focusRevision;
}

function handleEditKey(runtime: CefTextInputKeyboardRuntime, keyCode: number): void {
    const {node} = runtime;
    switch (keyCode) {
        case KEY_BACKSPACE: runtime.commit(deleteTextBackward(node.value, readSelection(node))); break;
        case KEY_DELETE: runtime.commit(deleteTextForward(node.value, readSelection(node))); break;
        case KEY_LEFT: moveCursor(node, -1); break;
        case KEY_RIGHT: moveCursor(node, 1); break;
        case KEY_HOME: node.setSelectionRange(0, 0); break;
        case KEY_END: node.setSelectionRange(node.value.length, node.value.length); break;
    }
}
