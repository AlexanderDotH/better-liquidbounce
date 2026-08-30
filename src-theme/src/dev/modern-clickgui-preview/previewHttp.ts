export function isConfigurableSetting(value: unknown): value is ConfigurableSetting {
    if (!isRecord(value)) {
        return false;
    }

    return value.valueType === "CONFIGURABLE"
        && typeof value.name === "string"
        && Array.isArray(value.value);
}

export function isModuleToggle(value: unknown): value is {name: string; enabled: boolean} {
    return isRecord(value)
        && typeof value.name === "string"
        && typeof value.enabled === "boolean";
}

export function isTypingPayload(value: unknown): value is {typing: boolean} {
    return isRecord(value) && typeof value.typing === "boolean";
}

export function isClipboardPayload(value: unknown): value is {text: string} {
    return isRecord(value)
        && Object.hasOwn(value, "text")
        && typeof value.text === "string";
}

export function isPersistentStoragePayload(
    value: unknown,
): value is {items: PersistentStorageItem[]} {
    return isRecord(value)
        && Array.isArray(value.items)
        && value.items.every(item =>
            isRecord(item)
            && typeof item.key === "string"
            && typeof item.value === "string"
        );
}

export function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

export async function readJson<T>(request: Request): Promise<T> {
    return await request.json() as T;
}

export function jsonResponse(value: unknown, status = 200): Response {
    return new Response(JSON.stringify(clone(value)), {
        status,
        headers: {"Content-Type": "application/json"},
    });
}

export function emptyResponse(): Response {
    return new Response(null, {status: 204});
}

export function methodNotAllowed(allowed: string[]): Response {
    return new Response(null, {
        status: 405,
        headers: {Allow: allowed.join(", ")},
    });
}

export function clone<T>(value: T): T {
    return structuredClone(value);
}
import type {ConfigurableSetting, PersistentStorageItem} from "../../integration/types";
