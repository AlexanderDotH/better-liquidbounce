import {listen} from "../ws";
import type {KeyboardCharEvent, KeyboardKeyEvent, VirtualScreenEvent} from "../events";
import {setTyping} from "../rest";
import {handleCefKeyboardKey, type CefTextInputKeyboardRuntime} from "./cefTextInputKeyboard.ts";
import {
    isActiveVirtualScreen,
    readSelection,
    restoreSelection,
    virtualScreenName,
    type CefTextInputOptions,
} from "./cefTextInputSupport";
import {
    pasteTextSelection,
    type TextEdit,
    type TextSelection,
} from "./textEditing";

export type {CefTextInputOptions} from "./cefTextInputSupport";

export function cefTextInput(node: HTMLInputElement, options: CefTextInputOptions) {
    const controller = new CefTextInputController(node, options);
    controller.connect();
    return {
        update: (next: CefTextInputOptions) => controller.update(next),
        destroy: () => controller.destroy(),
    };
}

class CefTextInputController implements CefTextInputKeyboardRuntime {
    readonly node: HTMLInputElement;
    options: CefTextInputOptions;
    focused = false;
    screenActive = false;
    focusRevision = 0;

    constructor(node: HTMLInputElement, options: CefTextInputOptions) {
        this.node = node;
        this.options = options;
    }

    connect(): void {
        listen("virtualScreen", this.handleVirtualScreen);
        listen("keyboardChar", this.handleKeyboardChar);
        listen("keyboardKey", this.handleKeyboardKey);
        this.node.addEventListener("focusin", this.handleFocusIn);
        this.node.addEventListener("focusout", this.handleFocusOut);
        document.addEventListener("pointerdown", this.handlePointerDown, true);
        window.addEventListener("blur", this.releaseFocus);
        this.syncDisplay();
    }

    update(options: CefTextInputOptions): void {
        this.options = options;
        this.syncDisplay();
    }

    destroy(): void {
        this.node.removeEventListener("focusin", this.handleFocusIn);
        this.node.removeEventListener("focusout", this.handleFocusOut);
        document.removeEventListener("pointerdown", this.handlePointerDown, true);
        window.removeEventListener("blur", this.releaseFocus);
        this.releaseFocus();
    }

    commit(edit: TextEdit): void {
        this.options.onChange(edit.value);
        this.syncDisplay(edit.selection);
    }

    releaseFocus = (): void => {
        if (!this.focused && document.activeElement !== this.node) return;
        this.focused = false;
        this.focusRevision += 1;
        if (document.activeElement === this.node) this.node.blur();
        void setTyping(false);
    };

    private syncDisplay(selection?: TextSelection): void {
        this.node.value = this.options.getValue() ?? "";
        if (selection !== undefined && this.focused) {
            restoreSelection(this.node, selection);
        }
    }

    private handleVirtualScreen = (event: VirtualScreenEvent): void => {
        const active = isActiveVirtualScreen(virtualScreenName(event), this.options);
        if (event.action === "close") {
            if (active) {
                this.screenActive = false;
                this.releaseFocus();
            }
            return;
        }
        if (active) this.screenActive = true;
    };

    private handleKeyboardChar = (event: KeyboardCharEvent): void => {
        if (!this.focused || document.activeElement !== this.node || !this.screenActive) return;
        const char = String.fromCodePoint(event.codePoint);
        if (!char || char.charCodeAt(0) < 32) return;
        this.commit(pasteTextSelection(this.node.value, readSelection(this.node), char));
    };

    private handleKeyboardKey = (event: KeyboardKeyEvent): void => {
        void handleCefKeyboardKey(this, event);
    };

    private handleFocusIn = (): void => {
        this.focused = true;
        this.focusRevision += 1;
        this.syncDisplay();
        void setTyping(true);
    };

    private handleFocusOut = (): void => {
        if (!this.focused) return;
        this.focused = false;
        this.focusRevision += 1;
        void setTyping(false);
    };

    private handlePointerDown = (event: PointerEvent): void => {
        if (!event.composedPath().includes(this.node)) this.releaseFocus();
    };
}
