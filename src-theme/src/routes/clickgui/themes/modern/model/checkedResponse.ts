export async function ensureSuccessfulResponse(
    response: Response,
    action: string,
): Promise<void> {
    if (response.ok) {
        return;
    }

    const details = (await response.text()).trim().slice(0, 240);
    const status = `${response.status} ${response.statusText}`.trim();
    const suffix = details ? `: ${details}` : "";
    throw new Error(`Failed to ${action} (${status})${suffix}`);
}
