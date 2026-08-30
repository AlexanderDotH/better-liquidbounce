import type {
    BaritoneFlyOwnership,
    BaritoneLocomotion,
    BaritoneNavigationPhase,
    BaritoneStatus,
} from "../../integration/baritone";

export function coordinateValue(value: string, name: string): number {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
        return parsed;
    }
    throw new Error(`${name} must be a finite number.`);
}

export function nextBaritoneTabIndex(
    key: string,
    currentIndex: number,
    lastIndex: number,
): number | null {
    if (key === "ArrowDown" || key === "ArrowRight") {
        return currentIndex === lastIndex ? 0 : currentIndex + 1;
    }
    if (key === "ArrowUp" || key === "ArrowLeft") {
        return currentIndex === 0 ? lastIndex : currentIndex - 1;
    }
    if (key === "Home") {
        return 0;
    }
    return key === "End" ? lastIndex : null;
}

export function describeBaritoneError(caught: unknown, fallback: string): string {
    if (!(caught instanceof Error) || !caught.message.trim()) {
        return fallback;
    }
    return `${fallback} ${caught.message}`;
}

export function baritoneStatusLabel(status: BaritoneStatus): string {
    return {
        UNAVAILABLE: "Unavailable",
        NO_WORLD: "No world",
        IDLE: "Idle",
        CALCULATING: "Calculating",
        PATHING: "Pathing",
        PAUSED: "Paused",
        FAILED: "Failed",
        ARRIVED: "Arrived",
    }[status];
}

export function baritoneEtaLabel(etaSeconds: number | null): string {
    if (etaSeconds === null) {
        return "—";
    }
    const minutes = Math.floor(etaSeconds / 60);
    const seconds = Math.max(0, Math.round(etaSeconds % 60));
    return minutes ? `${minutes}m ${seconds}s` : `${seconds}s`;
}

export function baritoneProgressPercent(progress: number | null): number {
    if (progress === null || !Number.isFinite(progress)) {
        return 0;
    }
    return Math.round(Math.max(0, Math.min(1, progress)) * 100);
}

export function locomotionLabel(locomotion: BaritoneLocomotion | null): string {
    if (locomotion === "FLY") return "Fly";
    if (locomotion === "WALK") return "Walk";
    return "None";
}

export function navigationPhaseLabel(phase: BaritoneNavigationPhase): string {
    return {
        IDLE: "Idle",
        WAITING_FOR_PATH: "Waiting for path",
        PLANNING: "Planning flight",
        ARMING: "Arming Fly",
        FLYING: "Flying",
        WALK_FALLBACK: "Walking fallback",
        WAITING_FOR_USER: "Waiting for user",
    }[phase];
}

export function ownershipLabel(
    ownership: BaritoneFlyOwnership | null,
    active: BaritoneLocomotion | null,
): string {
    if (ownership === "BARITONE") return "Baritone-owned Fly";
    if (ownership === "USER") return "User-owned Fly";
    return active === "WALK" ? "Baritone walking" : "No Fly lease";
}

export function routeHeading(active: BaritoneLocomotion | null): string {
    if (active === "FLY") return "Active flight route";
    if (active === "WALK") return "Active walking route";
    return "Planned route";
}

export function routeDescription(active: BaritoneLocomotion | null): string {
    if (active === "FLY") return "Collision-safe aerial route, capped at 512 points.";
    if (active === "WALK") return "Current upstream walking route, capped at 512 points.";
    return "Direction-preserving path preview, capped at 512 points.";
}
