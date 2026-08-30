import type {TextComponent} from "../../integration/types";

export const MINECRAFT_COLORS: Record<string, string> = {
    black: "#000000",
    dark_blue: "#0000aa",
    dark_green: "#00aa00",
    dark_aqua: "#00aaaa",
    dark_red: "#aa0000",
    dark_purple: "#aa00aa",
    gold: "#ffaa00",
    gray: "#aaaaaa",
    dark_gray: "#555555",
    blue: "#5555ff",
    green: "#55ff55",
    aqua: "#55ffff",
    red: "#ff5555",
    light_purple: "#ff55ff",
    yellow: "#ffff55",
    white: "#ffffff",
};

interface LegacyFormattingState {
    color: string;
    bold: boolean;
    italic: boolean;
    underlined: boolean;
    obfuscated: boolean;
    strikethrough: boolean;
}

export function translateTextColor(color: string): string {
    if (!color) return MINECRAFT_COLORS.white;
    return color.startsWith("#") ? color : MINECRAFT_COLORS[color];
}

export function convertLegacyCodes(text: string): TextComponent {
    let formatting = resetFormatting();
    const components: TextComponent[] = [];
    const textParts = (text.startsWith("§") ? text : `§f${text}`).split("§");
    for (const part of textParts) {
        formatting = applyLegacyCode(formatting, part.charAt(0));
        components.push({...formatting, text: part.slice(1)});
    }
    return {extra: components} as TextComponent;
}

function applyLegacyCode(
    formatting: LegacyFormattingState,
    code: string,
): LegacyFormattingState {
    switch (code) {
        case "k": return {...formatting, obfuscated: true};
        case "l": return {...formatting, bold: true};
        case "m": return {...formatting, strikethrough: true};
        case "n": return {...formatting, underlined: true};
        case "o": return {...formatting, italic: true};
        case "r": return resetFormatting();
        default: return {...formatting, color: colorForCode(code)};
    }
}

function colorForCode(code: string): string {
    const colorName = Object.keys(MINECRAFT_COLORS)[parseInt(code, 16)];
    return MINECRAFT_COLORS[colorName] ?? MINECRAFT_COLORS.black;
}

function resetFormatting(): LegacyFormattingState {
    return {
        obfuscated: false,
        bold: false,
        strikethrough: false,
        underlined: false,
        italic: false,
        color: MINECRAFT_COLORS.black,
    };
}
