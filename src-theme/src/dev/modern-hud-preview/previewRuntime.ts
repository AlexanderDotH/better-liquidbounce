import {
    createModernHudPreviewSnapshotEvents,
    routeModernHudPreviewRequest,
    type ModernHudPreviewServerEvent,
    type ModernHudPreviewState,
} from "./previewFixture";

export const MODERN_HUD_TARGET_REFRESH_MS = 750;
export const MODERN_HUD_NOTIFICATION_REFRESH_MS = 2_400;
export const MODERN_HUD_SNAPSHOT_START_MS = 150;

export interface ModernHudPreviewRuntime {
    emit(event: ModernHudPreviewServerEvent): void;
    start(): void;
    dispose(): void;
}

export function installModernHudPreviewRuntime(
    state: ModernHudPreviewState,
): ModernHudPreviewRuntime {
    const originalFetch = window.fetch;
    const originalWebSocket = window.WebSocket;
    const resourceObserver = installPreviewResourceObserver();
    const timers = new Set<number>();
    let started = false;
    let disposed = false;

    PreviewWebSocket.reset();
    window.WebSocket = PreviewWebSocket as unknown as typeof WebSocket;
    window.fetch = async (input, init) => {
        const request = createAbsoluteRequest(input, init);
        const mutationRequest = request.clone();
        const response = await routeModernHudPreviewRequest(state, request);

        if (response.ok) {
            const mutation = await previewMutation(state, mutationRequest);
            if (mutation) {
                PreviewWebSocket.broadcast(mutation);
            }
        }

        return response;
    };

    function emit(event: ModernHudPreviewServerEvent): void {
        if (!disposed) {
            PreviewWebSocket.broadcast(event);
        }
    }

    function start(): void {
        if (started || disposed) {
            return;
        }

        started = true;
        const snapshot = createModernHudPreviewSnapshotEvents(state);
        scheduleTimeout(
            () => snapshot.forEach(emit),
            MODERN_HUD_SNAPSHOT_START_MS,
        );
        scheduleInterval(
            () => emit(findEvent(snapshot, "targetChange")),
            MODERN_HUD_TARGET_REFRESH_MS,
        );
        scheduleInterval(
            () => emit(findEvent(snapshot, "notification")),
            MODERN_HUD_NOTIFICATION_REFRESH_MS,
        );
    }

    function dispose(): void {
        if (disposed) {
            return;
        }

        disposed = true;
        for (const timer of timers) {
            window.clearTimeout(timer);
            window.clearInterval(timer);
        }
        timers.clear();
        resourceObserver.disconnect();
        PreviewWebSocket.closeAll();

        if (window.fetch !== originalFetch) {
            window.fetch = originalFetch;
        }
        if (window.WebSocket === PreviewWebSocket as unknown as typeof WebSocket) {
            window.WebSocket = originalWebSocket;
        }
    }

    function scheduleTimeout(callback: () => void, delay: number): void {
        const timer = window.setTimeout(() => {
            timers.delete(timer);
            callback();
        }, delay);
        timers.add(timer);
    }

    function scheduleInterval(callback: () => void, delay: number): void {
        const timer = window.setInterval(callback, delay);
        timers.add(timer);
    }

    return {emit, start, dispose};
}

function createAbsoluteRequest(
    input: RequestInfo | URL,
    init?: RequestInit,
): Request {
    if (input instanceof Request) {
        return new Request(input, init);
    }

    return new Request(new URL(String(input), window.location.href), init);
}

async function previewMutation(
    state: ModernHudPreviewState,
    request: Request,
): Promise<ModernHudPreviewServerEvent | null> {
    const url = new URL(request.url);

    if (
        request.method === "PUT"
        && url.pathname === "/api/v1/client/modules/settings"
        && url.searchParams.get("name") === "HUD"
    ) {
        return {
            name: "hudValueChange",
            event: {configurable: structuredClone(state.hudSettings)},
        };
    }

    if (
        request.method === "POST"
        && url.pathname === "/api/v1/client/modules/toggle"
    ) {
        const body = await request.json() as {name: string; enabled: boolean};
        return {
            name: "moduleToggle",
            event: {
                moduleName: body.name,
                enabled: body.enabled,
                hidden: false,
            },
        };
    }

    return null;
}

function findEvent(
    events: ModernHudPreviewServerEvent[],
    name: string,
): ModernHudPreviewServerEvent {
    const event = events.find(candidate => candidate.name === name);
    if (!event) {
        throw new Error(`Modern HUD preview event is missing: ${name}`);
    }

    return event;
}

function installPreviewResourceObserver(): MutationObserver {
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

class PreviewWebSocket {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSING = 2;
    static readonly CLOSED = 3;

    private static instances = new Set<PreviewWebSocket>();

    readonly CONNECTING = PreviewWebSocket.CONNECTING;
    readonly OPEN = PreviewWebSocket.OPEN;
    readonly CLOSING = PreviewWebSocket.CLOSING;
    readonly CLOSED = PreviewWebSocket.CLOSED;

    readonly url: string;
    readonly protocol = "";
    readonly extensions = "";
    bufferedAmount = 0;
    binaryType: BinaryType = "blob";
    readyState = PreviewWebSocket.CONNECTING;
    onopen: ((event: Event) => void) | null = null;
    onclose: ((event: CloseEvent) => void) | null = null;
    onerror: ((event: Event) => void) | null = null;
    onmessage: ((event: MessageEvent) => void) | null = null;

    constructor(url: string | URL) {
        this.url = String(url);
        PreviewWebSocket.instances.add(this);

        queueMicrotask(() => {
            if (this.readyState !== PreviewWebSocket.CONNECTING) {
                return;
            }

            this.readyState = PreviewWebSocket.OPEN;
            this.onopen?.(new Event("open"));
        });
    }

    static reset(): void {
        PreviewWebSocket.closeAll();
        PreviewWebSocket.instances.clear();
    }

    static broadcast(message: ModernHudPreviewServerEvent): void {
        for (const socket of PreviewWebSocket.instances) {
            socket.receive(message);
        }
    }

    static closeAll(): void {
        for (const socket of PreviewWebSocket.instances) {
            socket.close();
        }
    }

    send(_data: string | ArrayBufferLike | Blob | ArrayBufferView): void {
        // The production HUD only sends WebSocket heartbeat pings.
    }

    close(): void {
        this.readyState = PreviewWebSocket.CLOSED;
        PreviewWebSocket.instances.delete(this);
    }

    private receive(message: ModernHudPreviewServerEvent): void {
        if (this.readyState !== PreviewWebSocket.OPEN) {
            return;
        }

        this.onmessage?.(new MessageEvent("message", {
            data: JSON.stringify(message),
        }));
    }
}
