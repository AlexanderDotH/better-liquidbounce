import type {ModernHudPreviewServerEvent} from "./previewFixture";

export class PreviewWebSocket {
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
