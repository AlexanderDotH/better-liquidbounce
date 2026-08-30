import type {ModernPanelPosition} from "./modernPanelState";

interface PendingPointer {
    clientX: number;
    clientY: number;
    shiftKey: boolean;
}

export interface ModernPanelDragHost {
    isMoving(): boolean;
    setMoving(moving: boolean): void;
    visiblePosition(): ModernPanelPosition;
    toLogicalPosition(clientX: number, clientY: number): ModernPanelPosition;
    constrain(position: ModernPanelPosition, shiftKey: boolean): ModernPanelPosition;
    setPosition(position: ModernPanelPosition): void;
    bringToFront(): void;
    updateGridVisibility(shiftKey: boolean): void;
    save(): void;
}

export class ModernPanelDragSession {
    private captureTarget: HTMLElement | null = null;
    private activePointerId: number | null = null;
    private offset: ModernPanelPosition = {left: 0, top: 0};
    private pendingPointer: PendingPointer | null = null;
    private frame: number | null = null;

    constructor(private readonly host: ModernPanelDragHost) {}

    start(event: PointerEvent): void {
        const target = event.target as HTMLElement;
        if (this.host.isMoving() || !event.isPrimary || event.button !== 0 || target.closest("button")) return;
        event.preventDefault();
        const pointer = this.host.toLogicalPosition(event.clientX, event.clientY);
        const position = this.host.visiblePosition();
        this.activePointerId = event.pointerId;
        this.offset = {left: pointer.left - position.left, top: pointer.top - position.top};
        this.captureTarget = event.currentTarget as HTMLElement;
        this.captureTarget.setPointerCapture(event.pointerId);
        this.host.setMoving(true);
        this.host.bringToFront();
        this.host.updateGridVisibility(event.shiftKey);
    }

    move(event: PointerEvent): void {
        if (!this.host.isMoving() || event.pointerId !== this.activePointerId) return;
        this.pendingPointer = {clientX: event.clientX, clientY: event.clientY, shiftKey: event.shiftKey};
        if (this.frame === null) this.frame = requestAnimationFrame(() => this.applyPending());
    }

    finish(event?: PointerEvent): void {
        if (!this.host.isMoving() || event && event.pointerId !== this.activePointerId) return;
        this.cancelFrame(true);
        const pointerId = this.activePointerId;
        this.activePointerId = null;
        this.host.setMoving(false);
        this.host.updateGridVisibility(true);
        this.releasePointerCapture(pointerId);
        this.host.save();
    }

    reset(): void {
        this.cancelFrame();
        const pointerId = this.activePointerId;
        this.activePointerId = null;
        this.host.setMoving(false);
        this.host.updateGridVisibility(true);
        this.releasePointerCapture(pointerId);
    }

    destroy(): void {
        this.cancelFrame();
        this.releasePointerCapture();
    }

    private applyPending(): void {
        this.frame = null;
        const pointer = this.pendingPointer;
        this.pendingPointer = null;
        if (!pointer || !this.host.isMoving()) return;
        const logical = this.host.toLogicalPosition(pointer.clientX, pointer.clientY);
        const raw = {left: logical.left - this.offset.left, top: logical.top - this.offset.top};
        this.host.setPosition(this.host.constrain(raw, pointer.shiftKey));
        this.host.updateGridVisibility(pointer.shiftKey);
    }

    private cancelFrame(applyPending = false): void {
        if (this.frame !== null) cancelAnimationFrame(this.frame);
        this.frame = null;
        if (applyPending) this.applyPending();
        else this.pendingPointer = null;
    }

    private releasePointerCapture(pointerId = this.activePointerId): void {
        if (this.captureTarget && pointerId !== null && this.captureTarget.hasPointerCapture(pointerId)) {
            this.captureTarget.releasePointerCapture(pointerId);
        }
        this.captureTarget = null;
    }
}
