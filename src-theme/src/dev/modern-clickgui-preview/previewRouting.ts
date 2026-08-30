import type {ConfigurableSetting} from "../../integration/types";
import type {ModernClickGuiPreviewState} from "./previewState";
import {createClientInfo, createGameWindow, createRegistryItems} from "./previewEnvironment.ts";
import {clone, emptyResponse, isClipboardPayload, isConfigurableSetting, isModuleToggle, isPersistentStoragePayload, isTypingPayload, jsonResponse, methodNotAllowed, readJson} from "./previewHttp.ts";

const API_PREFIX = "/api/v1/client";

export async function routeModernClickGuiPreviewRequest(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();
    const route = `${method} ${url.pathname}`;
    state.requests.push({method, path: `${url.pathname}${url.search}`});

    if (url.pathname === `${API_PREFIX}/modules/settings`) {
        return routeModuleSettings(state, request, url);
    }
    if (url.pathname === `${API_PREFIX}/global`) {
        return routeGlobalSettings(state, request);
    }
    if (url.pathname === `${API_PREFIX}/localStorage/all`) {
        return routePersistentStorage(state, request);
    }
    if (method === "GET" && url.pathname.startsWith(`${API_PREFIX}/registry/`)) {
        return jsonResponse(createRegistryItems());
    }
    return await routeExactPreviewRequest(state, request, url, route) ?? jsonResponse(
        {error: `Unsupported preview API: ${method} ${url.pathname}`},
        404,
    );
}

async function routeExactPreviewRequest(
    state: ModernClickGuiPreviewState,
    request: Request,
    url: URL,
    route: string,
): Promise<Response | null> {
    switch (route) {
        case `GET ${API_PREFIX}/modules`: return jsonResponse(state.modules);
        case `POST ${API_PREFIX}/modules/toggle`: return routeModuleToggle(state, request);
        case `POST ${API_PREFIX}/typing`: return routeTyping(state, request);
        case `GET ${API_PREFIX}/clipboard`: return jsonResponse({text: state.clipboardText});
        case `PUT ${API_PREFIX}/clipboard`: return routeClipboard(state, request);
        case `GET ${API_PREFIX}/info`: return jsonResponse(createClientInfo());
        case `GET ${API_PREFIX}/window`: return jsonResponse(createGameWindow());
        case `GET ${API_PREFIX}/input`: return routePrintableKey(url);
        case `POST ${API_PREFIX}/fileDialog`:
            return jsonResponse({file: "/home/alex/LiquidBounce/preview-config.json"});
        case `GET ${API_PREFIX}/virtualScreen`: return jsonResponse({name: "clickGui"});
        case `POST ${API_PREFIX}/virtualScreen`: return emptyResponse();
        default: return null;
    }
}

async function routeModuleSettings(
    state: ModernClickGuiPreviewState,
    request: Request,
    url: URL,
): Promise<Response> {
    const name = url.searchParams.get("name");
    if (!name || !Object.hasOwn(state.moduleSettings, name)) {
        return jsonResponse({error: `Unknown preview module: ${name ?? ""}`}, 404);
    }

    if (request.method === "GET") {
        return jsonResponse(state.moduleSettings[name]);
    }

    if (request.method !== "PUT") {
        return methodNotAllowed(["GET", "PUT"]);
    }

    const settings = await readJson<ConfigurableSetting>(request);
    if (!isConfigurableSetting(settings)) {
        return jsonResponse({error: "Invalid module settings payload"}, 400);
    }

    state.moduleSettings[name] = clone(settings);
    return emptyResponse();
}

async function routeModuleToggle(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    const body = await readJson<unknown>(request);
    if (!isModuleToggle(body)) {
        return jsonResponse({error: "Invalid module toggle payload"}, 400);
    }

    const module = state.modules.find(candidate => candidate.name === body.name);
    if (!module) {
        return jsonResponse({error: `Unknown preview module: ${body.name}`}, 404);
    }

    module.enabled = body.enabled;
    return emptyResponse();
}

async function routeGlobalSettings(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    if (request.method === "GET") {
        return jsonResponse(state.globalSettings);
    }

    if (request.method !== "PUT") {
        return methodNotAllowed(["GET", "PUT"]);
    }

    const settings = await readJson<ConfigurableSetting>(request);
    if (!isConfigurableSetting(settings)) {
        return jsonResponse({error: "Invalid global settings payload"}, 400);
    }

    state.globalSettings = clone(settings);
    return emptyResponse();
}

async function routePersistentStorage(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    if (request.method === "GET") {
        return jsonResponse({items: state.persistentItems});
    }

    if (request.method !== "PUT") {
        return methodNotAllowed(["GET", "PUT"]);
    }

    const body = await readJson<unknown>(request);
    if (!isPersistentStoragePayload(body)) {
        return jsonResponse({error: "Invalid persistent storage payload"}, 400);
    }

    state.persistentItems = clone(body.items);
    return emptyResponse();
}

async function routeTyping(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    const body = await readJson<unknown>(request);
    if (!isTypingPayload(body)) {
        return jsonResponse({error: "Invalid typing payload"}, 400);
    }

    state.typing = body.typing;
    return emptyResponse();
}

async function routeClipboard(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    const body = await readJson<unknown>(request);
    if (!isClipboardPayload(body)) {
        return jsonResponse({error: "Invalid clipboard payload"}, 400);
    }

    state.clipboardText = body.text;
    return emptyResponse();
}

function routePrintableKey(url: URL): Response {
    const key = url.searchParams.get("key") ?? "key.keyboard.unknown";
    const name = key.split(".").at(-1) ?? "unknown";
    const localized = name === "unknown"
        ? "None"
        : name.length === 1
            ? name.toUpperCase()
            : `${name.charAt(0).toUpperCase()}${name.slice(1)}`;

    return jsonResponse({
        translationKey: key,
        localized,
    });
}
