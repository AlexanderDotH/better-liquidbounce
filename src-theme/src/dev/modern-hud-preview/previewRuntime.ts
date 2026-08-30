import {
    createModernHudPreviewSnapshotEvents,
    routeModernHudPreviewRequest,
    type ModernHudPreviewServerEvent,
    type ModernHudPreviewState,
} from "./previewFixture";
import {installPreviewResourceObserver} from "./previewResources";
import {PreviewWebSocket} from "./previewSocket";
export {createModernHudPreviewResourceDataUrl} from "./previewResources";

export const MODERN_HUD_TARGET_REFRESH_MS = 750;
export const MODERN_HUD_NOTIFICATION_REFRESH_MS = 2_400;
export const MODERN_HUD_SNAPSHOT_START_MS = 150;

export interface ModernHudPreviewRuntime {
    emit(event: ModernHudPreviewServerEvent): void;
    start(): void;
    dispose(): void;
}

interface ModernHudPreviewRuntimeContext {
    state: ModernHudPreviewState;
    originalFetch: typeof window.fetch;
    originalWebSocket: typeof window.WebSocket;
    resourceObserver: ReturnType<typeof installPreviewResourceObserver>;
    timers: Set<number>;
    started: boolean;
    disposed: boolean;
}

export function installModernHudPreviewRuntime(
    state: ModernHudPreviewState,
): ModernHudPreviewRuntime {
    const context = createRuntimeContext(state);
    installRuntimeBindings(context);
    return {
        emit: event => emit(context, event),
        start: () => start(context),
        dispose: () => dispose(context),
    };
}

function createRuntimeContext(state: ModernHudPreviewState): ModernHudPreviewRuntimeContext {
    return {
        state,
        originalFetch: window.fetch,
        originalWebSocket: window.WebSocket,
        resourceObserver: installPreviewResourceObserver(),
        timers: new Set<number>(),
        started: false,
        disposed: false,
    };
}

function installRuntimeBindings(context: ModernHudPreviewRuntimeContext): void {
    PreviewWebSocket.reset();
    window.WebSocket = PreviewWebSocket as unknown as typeof WebSocket;
    window.fetch = (input, init) => routePreviewFetch(context, input, init);
}

async function routePreviewFetch(
    context: ModernHudPreviewRuntimeContext,
    input: RequestInfo | URL,
    init?: RequestInit,
): Promise<Response> {
    const request = createAbsoluteRequest(input, init);
    const mutationRequest = request.clone();
    const response = await routeModernHudPreviewRequest(context.state, request);
    if (response.ok) {
        const mutation = await previewMutation(context.state, mutationRequest);
        if (mutation) PreviewWebSocket.broadcast(mutation);
    }
    return response;
}

function emit(context: ModernHudPreviewRuntimeContext, event: ModernHudPreviewServerEvent): void {
    if (!context.disposed) PreviewWebSocket.broadcast(event);
}

function start(context: ModernHudPreviewRuntimeContext): void {
    if (context.started || context.disposed) return;
    context.started = true;
    const snapshot = createModernHudPreviewSnapshotEvents(context.state);
    scheduleTimeout(context, () => snapshot.forEach(event => emit(context, event)), MODERN_HUD_SNAPSHOT_START_MS);
    scheduleInterval(context, () => emit(context, findEvent(snapshot, "targetChange")), MODERN_HUD_TARGET_REFRESH_MS);
    scheduleInterval(context, () => emit(context, findEvent(snapshot, "notification")), MODERN_HUD_NOTIFICATION_REFRESH_MS);
}

function dispose(context: ModernHudPreviewRuntimeContext): void {
    if (context.disposed) return;
    context.disposed = true;
    for (const timer of context.timers) {
        window.clearTimeout(timer);
        window.clearInterval(timer);
    }
    context.timers.clear();
    context.resourceObserver.disconnect();
    PreviewWebSocket.closeAll();
    if (window.fetch !== context.originalFetch) window.fetch = context.originalFetch;
    if (window.WebSocket === PreviewWebSocket as unknown as typeof WebSocket) {
        window.WebSocket = context.originalWebSocket;
    }
}

function scheduleTimeout(
    context: ModernHudPreviewRuntimeContext,
    callback: () => void,
    delay: number,
): void {
    const timer = window.setTimeout(() => {
        context.timers.delete(timer);
        callback();
    }, delay);
    context.timers.add(timer);
}

function scheduleInterval(
    context: ModernHudPreviewRuntimeContext,
    callback: () => void,
    delay: number,
): void {
    const timer = window.setInterval(callback, delay);
    context.timers.add(timer);
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
