import type {ConfigurableSetting} from "../../integration/types";
import type {ModernHudPreviewState} from "./previewState";
import {previewColor} from "./previewComponents.ts";
import {clone, emptyResponse, isRecord, jsonResponse, methodNotAllowed, readJson} from "./previewHttp.ts";

const API_PREFIX = "/api/v1/client";

export async function routeModernHudPreviewRequest(
    state: ModernHudPreviewState,
    request: Request,
): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();
    const route = `${method} ${url.pathname}`;
    state.requests.push({method, path: `${url.pathname}${url.search}`});

    if (url.pathname === `${API_PREFIX}/modules/settings`) {
        return routeHudSettings(state, request, url);
    }
    const response = await routeExactHudPreview(state, request, url, route)
        ?? routeDynamicHudPreview(state, url, method);
    return response ?? jsonResponse(
        {error: `Unsupported Modern HUD preview API: ${method} ${url.pathname}`},
        404,
    );
}

async function routeExactHudPreview(
    state: ModernHudPreviewState,
    request: Request,
    url: URL,
    route: string,
): Promise<Response | null> {
    switch (route) {
        case "GET /metadata.json": return jsonResponse(state.metadata);
        case `GET ${API_PREFIX}/info`: return jsonResponse(state.clientInfo);
        case `GET ${API_PREFIX}/window`: return jsonResponse(state.gameWindow);
        case `GET ${API_PREFIX}/modules`: return jsonResponse(state.modules);
        case `GET ${API_PREFIX}/player`: return jsonResponse(state.player);
        case `GET ${API_PREFIX}/player/contextualBar`: return jsonResponse(state.contextualBar);
        case `GET ${API_PREFIX}/player/inventory`: return jsonResponse(state.inventory);
        case `GET ${API_PREFIX}/keybinds`: return jsonResponse(state.keybinds);
        case `GET ${API_PREFIX}/input`: return printableKeyResponse(url);
        case `POST ${API_PREFIX}/modules/toggle`: return routeModuleToggle(state, request);
        default: return null;
    }
}

function routeDynamicHudPreview(
    state: ModernHudPreviewState,
    url: URL,
    method: string,
): Response | null {
    if (method !== "GET") return null;
    if (url.pathname === `${API_PREFIX}/components/${state.metadata.id}`) {
        return jsonResponse(state.components);
    }
    if (url.pathname.startsWith(`${API_PREFIX}/resource/`)) {
        return previewResourceResponse(url);
    }
    return null;
}

async function routeHudSettings(
    state: ModernHudPreviewState,
    request: Request,
    url: URL,
): Promise<Response> {
    if (url.searchParams.get("name") !== "HUD") {
        return jsonResponse({error: "Only HUD settings exist in this preview."}, 404);
    }

    if (request.method === "GET") {
        return jsonResponse(state.hudSettings);
    }

    if (request.method !== "PUT") {
        return methodNotAllowed(["GET", "PUT"]);
    }

    const settings = await readJson<unknown>(request);
    if (!isHudSettings(settings)) {
        return jsonResponse({error: "Invalid HUD settings payload."}, 400);
    }

    state.hudSettings = clone(settings);
    return emptyResponse();
}

async function routeModuleToggle(
    state: ModernHudPreviewState,
    request: Request,
): Promise<Response> {
    const body = await readJson<unknown>(request);
    if (!isModuleToggle(body)) {
        return jsonResponse({error: "Invalid module toggle payload."}, 400);
    }

    const module = state.modules.find(candidate => candidate.name === body.name);
    if (!module) {
        return jsonResponse({error: `Unknown preview module: ${body.name}`}, 404);
    }

    module.enabled = body.enabled;
    return emptyResponse();
}

function printableKeyResponse(url: URL): Response {
    const translationKey = url.searchParams.get("key") ?? "key.keyboard.unknown";
    const keyName = translationKey.split(".").at(-1) ?? "unknown";
    const localized = keyName.length === 1
        ? keyName.toUpperCase()
        : keyName.charAt(0).toUpperCase() + keyName.slice(1);

    return jsonResponse({translationKey, localized});
}

function previewResourceResponse(url: URL): Response {
    const kind = url.pathname.split("/").at(-1) ?? "resource";
    const label = url.searchParams.get("id") ?? url.searchParams.get("uuid") ?? kind;
    const color = previewColor(label);
    const svg = [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">',
        `<rect width="32" height="32" rx="7" fill="${color}"/>`,
        '<path d="M8 21 16 7l8 14-8 5z" fill="rgba(255,255,255,.72)"/>',
        "</svg>",
    ].join("");

    return new Response(svg, {
        status: 200,
        headers: {"Content-Type": "image/svg+xml"},
    });
}

function isHudSettings(value: unknown): value is ConfigurableSetting {
    if (!isRecord(value)) {
        return false;
    }

    return value.name === "HUD"
        && value.valueType === "CONFIGURABLE"
        && Array.isArray(value.value);
}

function isModuleToggle(value: unknown): value is {name: string; enabled: boolean} {
    return isRecord(value)
        && typeof value.name === "string"
        && typeof value.enabled === "boolean";
}
