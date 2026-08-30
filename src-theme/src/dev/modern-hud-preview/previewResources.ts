export function installPreviewResourceObserver(): MutationObserver {
    const observer = new MutationObserver(records => {
        for (const record of records) {
            if (record.type === "attributes" && record.target instanceof HTMLImageElement) {
                replacePreviewResource(record.target);
                continue;
            }

            for (const node of record.addedNodes) {
                if (!(node instanceof Element)) {
                    continue;
                }

                if (node instanceof HTMLImageElement) {
                    replacePreviewResource(node);
                }
                node.querySelectorAll("img").forEach(replacePreviewResource);
            }
        }
    });

    observer.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ["src"],
        childList: true,
        subtree: true,
    });
    document.querySelectorAll("img").forEach(replacePreviewResource);
    return observer;
}

function replacePreviewResource(image: HTMLImageElement): void {
    if (image.dataset.previewResource) {
        return;
    }

    const url = new URL(image.src, window.location.href);
    const resourcePrefix = "/api/v1/client/resource/";
    if (!url.pathname.startsWith(resourcePrefix)) {
        return;
    }

    const kind = url.pathname.slice(resourcePrefix.length);
    const key = url.searchParams.get("id") ?? url.searchParams.get("uuid") ?? kind;
    image.dataset.previewResource = kind;
    image.src = createModernHudPreviewResourceDataUrl(kind, key);
}

export function createModernHudPreviewResourceDataUrl(
    kind: string,
    key: string,
): string {
    const color = previewColor(key);
    const svg = kind === "skin"
        ? skinPlaceholder(color)
        : iconPlaceholder(color, kind === "effectTexture");

    return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

function iconPlaceholder(color: string, round: boolean): string {
    const radius = round ? 16 : 6;
    return [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">',
        `<rect width="32" height="32" rx="${radius}" fill="${color}"/>`,
        '<path d="M8 21 16 6l8 15-8 5z" fill="rgba(255,255,255,.76)"/>',
        '<path d="M12 20h8" stroke="rgba(9,11,15,.48)" stroke-width="2"/>',
        "</svg>",
    ].join("");
}

function skinPlaceholder(color: string): string {
    return [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">',
        `<rect width="64" height="64" rx="12" fill="${color}"/>`,
        '<rect x="13" y="10" width="38" height="42" rx="10" fill="#d8a47f"/>',
        '<path d="M13 24V14q0-8 10-8h20q8 0 8 8v10l-8-8-22 3z" fill="#31271f"/>',
        '<rect x="21" y="29" width="5" height="5" rx="1" fill="#26313f"/>',
        '<rect x="38" y="29" width="5" height="5" rx="1" fill="#26313f"/>',
        '<path d="M24 42q8 6 16 0" fill="none" stroke="#7b4137" stroke-width="3"/>',
        "</svg>",
    ].join("");
}

function previewColor(value: string): string {
    let hash = 0;
    for (const character of value) {
        hash = ((hash << 5) - hash + character.charCodeAt(0)) | 0;
    }

    return `hsl(${Math.abs(hash) % 360} 48% 48%)`;
}
