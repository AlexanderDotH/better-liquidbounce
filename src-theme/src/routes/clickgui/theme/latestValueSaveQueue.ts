export interface LatestValueSaveQueueState {
    saving: boolean;
    error: Error | null;
    hasPending: boolean;
}

export interface LatestValueSaveQueueDependencies<T> {
    save(value: T): Promise<void>;
    reload(): Promise<T>;
    onConfirmed(value: T): void;
    onStateChange?(state: LatestValueSaveQueueState): void;
}

export interface LatestValueSaveQueue<T> {
    enqueue(value: T): void;
    retry(): void;
    isSaving(): boolean;
    hasPending(): boolean;
    whenIdle(): Promise<void>;
}

export function createLatestValueSaveQueue<T>(
    dependencies: LatestValueSaveQueueDependencies<T>,
): LatestValueSaveQueue<T> {
    let pendingValue: T | undefined;
    let pending = false;
    let saving = false;
    let error: Error | null = null;
    let idleWaiters: Array<() => void> = [];

    function enqueue(value: T): void {
        pendingValue = value;
        pending = true;
        error = null;

        if (!saving) {
            void flush();
        }
    }

    function retry(): void {
        if (!saving && pending) {
            error = null;
            void flush();
        }
    }

    async function flush(): Promise<void> {
        if (saving || !pending) {
            return;
        }

        saving = true;
        publishState();
        let failed = false;
        let latestAttemptedValue: T | undefined;
        let attemptedValue = false;

        try {
            while (true) {
                const latestSaved = await savePendingValues();
                if (latestSaved.attempted) {
                    latestAttemptedValue = latestSaved.value;
                    attemptedValue = true;
                }
                const confirmed = await dependencies.reload();

                if (pending) {
                    continue;
                }

                dependencies.onConfirmed(confirmed);
                break;
            }
        } catch (cause) {
            failed = true;
            if (!pending && attemptedValue) {
                pendingValue = latestAttemptedValue as T;
                pending = true;
            }
            error = toError(cause);
        } finally {
            saving = false;
            publishState();
            resolveIdleWaiters();

            if (pending && !failed) {
                void flush();
            }
        }
    }

    async function savePendingValues(): Promise<{
        attempted: boolean;
        value: T | undefined;
    }> {
        let latestValue: T | undefined;
        let attempted = false;

        while (pending) {
            const value = pendingValue as T;
            pending = false;
            latestValue = value;
            attempted = true;

            try {
                await dependencies.save(value);
            } catch (cause) {
                if (!pending) {
                    pendingValue = value;
                    pending = true;
                }

                throw cause;
            }
        }

        return {attempted, value: latestValue};
    }

    function publishState(): void {
        dependencies.onStateChange?.({
            saving,
            error,
            hasPending: pending,
        });
    }

    function whenIdle(): Promise<void> {
        if (!saving) {
            return Promise.resolve();
        }

        return new Promise(resolve => {
            idleWaiters.push(resolve);
        });
    }

    function resolveIdleWaiters(): void {
        const waiters = idleWaiters;
        idleWaiters = [];
        waiters.forEach(resolve => resolve());
    }

    return {
        enqueue,
        retry,
        isSaving: () => saving,
        hasPending: () => pending,
        whenIdle,
    };
}

function toError(cause: unknown): Error {
    if (cause instanceof Error) {
        return cause;
    }

    return new Error("The settings update failed.");
}
