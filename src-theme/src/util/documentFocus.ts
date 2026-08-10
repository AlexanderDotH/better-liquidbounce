type FocusDocument = Pick<Document, "activeElement">;

export function releaseDocumentFocus(currentDocument: FocusDocument = document): void {
    const activeElement = currentDocument.activeElement;
    if (activeElement === null || !(activeElement instanceof HTMLElement)) {
        return;
    }

    activeElement.blur();
}
