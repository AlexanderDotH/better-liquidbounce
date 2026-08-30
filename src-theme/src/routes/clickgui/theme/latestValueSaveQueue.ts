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

interface SaveAttempt<T> {
    failed: boolean;
    attempted: boolean;
    latestValue: T | undefined;
}

export function createLatestValueSaveQueue<T>(
    dependencies: LatestValueSaveQueueDependencies<T>,
): LatestValueSaveQueue<T> {
    const controller = new LatestValueSaveQueueController(dependencies);
    return {
        enqueue: value => controller.enqueue(value),
        retry: () => controller.retry(),
        isSaving: () => controller.isSaving(),
        hasPending: () => controller.hasPending(),
        whenIdle: () => controller.whenIdle(),
    };
}

class LatestValueSaveQueueController<T> {
    private readonly dependencies: LatestValueSaveQueueDependencies<T>;
    private pendingValue: T | undefined;
    private pending = false;
    private saving = false;
    private error: Error | null = null;
    private idleWaiters: Array<() => void> = [];

    constructor(dependencies: LatestValueSaveQueueDependencies<T>) {
        this.dependencies = dependencies;
    }

    enqueue(value: T): void {
        this.pendingValue = value;
        this.pending = true;
        this.error = null;
        if (!this.saving) void this.flush();
    }

    retry(): void {
        if (this.saving || !this.pending) return;
        this.error = null;
        void this.flush();
    }

    isSaving(): boolean {
        return this.saving;
    }

    hasPending(): boolean {
        return this.pending;
    }

    whenIdle(): Promise<void> {
        if (!this.saving) return Promise.resolve();
        return new Promise(resolve => this.idleWaiters.push(resolve));
    }

    private async flush(): Promise<void> {
        if (this.saving || !this.pending) return;
        this.saving = true;
        this.publishState();
        const attempt: SaveAttempt<T> = {failed: false, attempted: false, latestValue: undefined};
        try {
            await this.saveUntilConfirmed(attempt);
        } catch (cause) {
            attempt.failed = true;
            this.restoreFailedAttempt(attempt);
            this.error = toError(cause);
        } finally {
            this.finishFlush(attempt.failed);
        }
    }

    private async saveUntilConfirmed(attempt: SaveAttempt<T>): Promise<void> {
        while (true) {
            const latest = await this.savePendingValues();
            if (latest.attempted) {
                attempt.latestValue = latest.value;
                attempt.attempted = true;
            }
            const confirmed = await this.dependencies.reload();
            if (this.pending) continue;
            this.dependencies.onConfirmed(confirmed);
            return;
        }
    }

    private async savePendingValues(): Promise<{attempted: boolean; value: T | undefined}> {
        let latestValue: T | undefined;
        let attempted = false;
        while (this.pending) {
            const value = this.takePendingValue();
            latestValue = value;
            attempted = true;
            try {
                await this.dependencies.save(value);
            } catch (cause) {
                this.restoreUnsupersededValue(value);
                throw cause;
            }
        }
        return {attempted, value: latestValue};
    }

    private takePendingValue(): T {
        const value = this.pendingValue as T;
        this.pending = false;
        return value;
    }

    private restoreUnsupersededValue(value: T): void {
        if (this.pending) return;
        this.pendingValue = value;
        this.pending = true;
    }

    private restoreFailedAttempt(attempt: SaveAttempt<T>): void {
        if (this.pending || !attempt.attempted) return;
        this.pendingValue = attempt.latestValue as T;
        this.pending = true;
    }

    private finishFlush(failed: boolean): void {
        this.saving = false;
        this.publishState();
        this.resolveIdleWaiters();
        if (this.pending && !failed) void this.flush();
    }

    private publishState(): void {
        this.dependencies.onStateChange?.({
            saving: this.saving,
            error: this.error,
            hasPending: this.pending,
        });
    }

    private resolveIdleWaiters(): void {
        const waiters = this.idleWaiters;
        this.idleWaiters = [];
        waiters.forEach(resolve => resolve());
    }
}

function toError(cause: unknown): Error {
    return cause instanceof Error ? cause : new Error("The settings update failed.");
}
