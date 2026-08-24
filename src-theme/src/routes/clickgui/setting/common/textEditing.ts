export interface TextSelection {
    start: number;
    end: number;
}

export interface TextEdit {
    value: string;
    selection: TextSelection;
}

export interface ClipboardTextEdit extends TextEdit {
    clipboardText: string;
}

function clampOffset(value: string, offset: number): number {
    return Math.max(0, Math.min(value.length, offset));
}

function normalizeSelection(value: string, selection: TextSelection): TextSelection {
    const start = clampOffset(value, selection.start);
    const end = clampOffset(value, selection.end);

    return start <= end ? {start, end} : {start: end, end: start};
}

export function copyTextSelection(value: string, selection: TextSelection): ClipboardTextEdit {
    const normalized = normalizeSelection(value, selection);

    return {
        value,
        selection: normalized,
        clipboardText: value.slice(normalized.start, normalized.end),
    };
}

export function pasteTextSelection(value: string, selection: TextSelection, text: string): TextEdit {
    const normalized = normalizeSelection(value, selection);
    const cursor = normalized.start + text.length;

    return {
        value: value.slice(0, normalized.start) + text + value.slice(normalized.end),
        selection: {start: cursor, end: cursor},
    };
}

export function cutTextSelection(value: string, selection: TextSelection): ClipboardTextEdit {
    const copied = copyTextSelection(value, selection);
    const edited = pasteTextSelection(value, copied.selection, "");

    return {...edited, clipboardText: copied.clipboardText};
}

export function deleteTextBackward(value: string, selection: TextSelection): TextEdit {
    const normalized = normalizeSelection(value, selection);
    if (normalized.start !== normalized.end) {
        return pasteTextSelection(value, normalized, "");
    }

    if (normalized.start === 0) {
        return {value, selection: normalized};
    }

    return pasteTextSelection(value, {
        start: normalized.start - 1,
        end: normalized.end,
    }, "");
}

export function deleteTextForward(value: string, selection: TextSelection): TextEdit {
    const normalized = normalizeSelection(value, selection);
    if (normalized.start !== normalized.end) {
        return pasteTextSelection(value, normalized, "");
    }

    if (normalized.end === value.length) {
        return {value, selection: normalized};
    }

    return pasteTextSelection(value, {
        start: normalized.start,
        end: normalized.end + 1,
    }, "");
}
