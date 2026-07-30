import {readable} from "svelte/store";

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";
const CLASSIC_MOTION_DURATION_MS = 200;
const MODERN_MOTION_DURATION_MS = 160;

export const prefersReducedMotion = readable(false, (set) => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
        return;
    }

    const mediaQuery = window.matchMedia(REDUCED_MOTION_QUERY);
    const synchronize = () => set(mediaQuery.matches);

    synchronize();
    mediaQuery.addEventListener("change", synchronize);

    return () => mediaQuery.removeEventListener("change", synchronize);
});

export function hudMotionDuration(
    presentation: "classic" | "modern",
    reducedMotion: boolean,
    modernDuration = MODERN_MOTION_DURATION_MS,
): number {
    return reducedMotion ? 0 : presentation === "modern" ? modernDuration : CLASSIC_MOTION_DURATION_MS;
}
