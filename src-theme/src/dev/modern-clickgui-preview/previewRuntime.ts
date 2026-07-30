import {
    routeModernClickGuiPreviewRequest,
    type ModernClickGuiPreviewState,
} from "./previewFixture";

interface PreviewMutation {
    name: string;
    event: unknown;
}

export function installModernClickGuiPreviewRuntime(
    state: ModernClickGuiPreviewState,
): void {
    PreviewWebSocket.reset();
    window.WebSocket = PreviewWebSocket as unknown as typeof WebSocket;
    window.fetch = async (input, init) => {
        const request = createAbsoluteRequest(input, init);
        const mutationRequest = request.clone();
        const response = await routeModernClickGuiPreviewRequest(state, request);

        if (response.ok) {
            const mutation = await previewMutation(state, mutationRequest);
            if (mutation) {
                PreviewWebSocket.broadcast(mutation);
            }
        }

        return response;
    };
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
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<PreviewMutation | null> {
    const url = new URL(request.url);

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

    if (
        request.method === "PUT"
        && url.pathname === "/api/v1/client/modules/settings"
    ) {
        const name = url.searchParams.get("name");
        if (!name || !Object.hasOwn(state.moduleSettings, name)) {
            return null;
        }

        return {
            name: "clickGuiValueChange",
            event: {configurable: structuredClone(state.moduleSettings[name])},
        };
    }

    return null;
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
            this.readyState = PreviewWebSocket.OPEN;
            this.onopen?.(new Event("open"));
        });
    }

    static reset(): void {
        PreviewWebSocket.instances.clear();
    }

    static broadcast(mutation: PreviewMutation): void {
        for (const socket of PreviewWebSocket.instances) {
            socket.receive(mutation);
        }
    }

    send(_data: string | ArrayBufferLike | Blob | ArrayBufferView): void {
        // The production client only sends heartbeat pings from this screen.
    }

    close(): void {
        this.readyState = PreviewWebSocket.CLOSED;
        PreviewWebSocket.instances.delete(this);
    }

    private receive(mutation: PreviewMutation): void {
        if (this.readyState !== PreviewWebSocket.OPEN) {
            return;
        }

        this.onmessage?.(new MessageEvent("message", {
            data: JSON.stringify(mutation),
        }));
    }
}
