export const MODERN_PANEL_STAGGER_LIMIT = 7;
export const MODERN_MODULE_STAGGER_LIMIT = 8;
export const MODERN_RESULT_STAGGER_LIMIT = 6;
export const MODERN_SETTING_STAGGER_LIMIT = 6;

export function motionStaggerIndex(index: number, maximum: number): number {
    if (!Number.isFinite(index) || !Number.isFinite(maximum)) {
        return 0;
    }

    const normalizedIndex = Math.max(0, Math.trunc(index));
    const normalizedMaximum = Math.max(0, Math.trunc(maximum));
    return Math.min(normalizedIndex, normalizedMaximum);
}
