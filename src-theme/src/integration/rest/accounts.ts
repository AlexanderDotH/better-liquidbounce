import type {Account, TheAlteningGenerationResult} from "../types";
import {isLoggingIn} from "../accountLoginState";
import {API_BASE} from "./base";

const ALTENING_GENERATION_REQUEST_TIMEOUT_MS = 25_000;

export async function restoreSession() {
    isLoggingIn.set(true);
    await fetch(`${API_BASE}/client/account/restore`, {
        method: "POST",
    }).finally(() => isLoggingIn.set(false));
}

export async function orderAccounts(order: number[]) {
    await fetch(`${API_BASE}/client/accounts/order`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({order})
    });
}


export async function addCrackedAccount(username: string, online: boolean) {
    await fetch(`${API_BASE}/client/accounts/new/cracked`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({username, online})
    });
}

export async function addSessionAccount(token: string) {
    await fetch(`${API_BASE}/client/accounts/new/session`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({token})
    });
}

export async function addAlteningAccount(token: string) {
    await fetch(`${API_BASE}/client/accounts/new/altening`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({token})
    });
}

export async function generateAlteningAccount(apiToken: string): Promise<TheAlteningGenerationResult> {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), ALTENING_GENERATION_REQUEST_TIMEOUT_MS);

    try {
        const response = await fetch(`${API_BASE}/client/accounts/new/altening/generate`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({apiToken}),
            signal: controller.signal
        });

        return await response.json();
    } catch (error) {
        return {
            status: "ERROR",
            message: isAbortError(error)
                ? "TheAltening authentication server is not responding. Try again later."
                : "Failed to generate TheAltening account."
        };
    } finally {
        window.clearTimeout(timeout);
    }
}

function isAbortError(error: unknown) {
    return error instanceof DOMException && error.name === "AbortError";
}

export async function addMicrosoftAccountWebView() {
    await fetch(`${API_BASE}/client/accounts/new/microsoft/webview`, {
        method: "POST",
    });
}

export async function addMicrosoftAccountDeviceCode() {
    await fetch(`${API_BASE}/client/accounts/new/microsoft/device-code`, {
        method: "POST",
    });
}

export async function addMicrosoftAccountDeviceCodeCopyUrl() {
    await fetch(`${API_BASE}/client/accounts/new/microsoft/device-code/clipboard`, {
        method: "POST",
    });
}

export async function addMicrosoftAccountCredentials(email: string, password: string) {
    await fetch(`${API_BASE}/client/accounts/new/microsoft/credentials`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({email, password})
    });
}

export async function setAccountFavorite(id: number, favorite: boolean) {
    if (favorite) {
        await fetch(`${API_BASE}/client/account/favorite`, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({id})
        });
    } else {
        await fetch(`${API_BASE}/client/account/favorite`, {
            method: "DELETE",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({id})
        });
    }
}

export async function removeAccount(id: number) {
    await fetch(`${API_BASE}/client/account`, {
        method: "DELETE",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({id})
    });
}

export async function loginToAccount(id: number) {
    isLoggingIn.set(true);
    await fetch(`${API_BASE}/client/account/login`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({id})
    }).finally(() => isLoggingIn.set(false));
}

export async function directLoginToCrackedAccount(username: string, online: boolean) {
    isLoggingIn.set(true);
    await fetch(`${API_BASE}/client/account/login/cracked`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({username, online})
    }).finally(() => isLoggingIn.set(false));
}

export async function directLoginToSessionAccount(token: string) {
    isLoggingIn.set(true);
    await fetch(`${API_BASE}/client/account/login/session`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({token})
    }).finally(() => isLoggingIn.set(false));
}

export async function getAccounts(): Promise<Account[]> {
    const response = await fetch(`${API_BASE}/client/accounts`);
    const data: Account[] = await response.json();

    return data;
}
