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
