import type {ClickGuiVisualTheme} from "../../../theme/clickGuiThemeState";

export interface ModernThemeOption {
    value: ClickGuiVisualTheme;
    title: string;
    eyebrow: string;
    description: string;
}

export const MODERN_THEME_OPTIONS: readonly ModernThemeOption[] = [
    {
        value: "Modern",
        title: "Graphite Glass",
        eyebrow: "Modern",
        description: "Balanced floating panels, a compact command bar, and restrained motion.",
    },
    {
        value: "Classic",
        title: "Original",
        eyebrow: "Classic",
        description: "The familiar LiquidBounce ClickGUI with its existing layout and interactions.",
    },
];

export function describeModernSettingsError(error: unknown, fallback: string): string {
    if (!(error instanceof Error) || !error.message.trim()) {
        return fallback;
    }

    return `${fallback} ${error.message}`;
}
