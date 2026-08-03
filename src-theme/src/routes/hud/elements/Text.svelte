<script lang="ts">
    import type {ClientPlayerDataEvent} from "../../../integration/events";
    import type {PlayerData} from "../../../integration/types";
    import {intToRgba, rgbaToHex} from "../../../integration/util";
    import {listen} from "../../../integration/ws";

    let playerData: PlayerData | null = null;

    export let settings: { [name: string]: any };

    let cSettings: HudTextSettings;
    let processedText = "";
    let textStyle = "";
    let container: "Plain" | "Pill" = "Plain";

    const PLACEHOLDER_PATTERN = /{(\w+(?:\.\w+)*)}/g;
    const UPDATE_TIMEOUT = 100;

    let lastUpdate = Date.now();

    listen("clientPlayerData", (event: ClientPlayerDataEvent) => {
        if (Date.now() - lastUpdate < UPDATE_TIMEOUT) return;
        lastUpdate = Date.now();

        playerData = event.playerData;
    });

    function processText(text: string, playerData: PlayerData | null): string {
        if (!text || !playerData) {
            return text || "";
        }

        return text.replace(PLACEHOLDER_PATTERN, (match, path: string) => {
            const value = resolvePath(playerData, path);

            return value === null || value === undefined
                ? match
                : formatValue(value);
        });
    }

    function resolvePath(source: PlayerData, path: string): unknown {
        let value: any = source;

        for (const key of path.split(".")) {
            value = value ? value[key] : null;
        }

        return value;
    }

    function formatValue(value: unknown): string {
        switch (typeof value) {
            case "number":
                return Number.isInteger(value) ? value.toString() : value.toFixed(2);
            case "object":
                return JSON.stringify(value);
            default:
                return String(value);
        }
    }

    function toColor(value: number): string {
        return rgbaToHex(intToRgba(value));
    }

    function getTextDecoration(decorations: HudTextSettings["decorations"]): string {
        return [
            decorations.underline ? "underline" : undefined,
            decorations.strikethrough ? "line-through" : undefined
        ].filter(Boolean).join(" ") || "none";
    }

    function getTextShadow(shadow: HudTextSettings["shadow"]): string {
        return shadow.enabled
            ? `${shadow.offsetX}px ${shadow.offsetY}px ${shadow.blurRadius}px ${toColor(shadow.color)}`
            : "none";
    }

    function getGlow(glow: HudTextSettings["glow"]): string {
        return glow.enabled
            ? `drop-shadow(0px 0px ${glow.radius}px ${toColor(glow.color)})`
            : "none";
    }

    function createTextStyle(settings: HudTextSettings): string {
        return [
            `font-family: ${settings.font}`,
            `font-size: ${settings.size}px`,
            `color: ${toColor(settings.color)}`,
            `font-weight: ${settings.decorations.bold ? "bold" : "normal"}`,
            `font-style: ${settings.decorations.italic ? "italic" : "normal"}`,
            `text-decoration: ${getTextDecoration(settings.decorations)}`,
            `text-shadow: ${getTextShadow(settings.shadow)}`,
            `filter: ${getGlow(settings.glow)}`
        ].join("; ");
    }

    $: cSettings = settings as HudTextSettings;
    $: container = cSettings.container ?? "Plain";
    $: processedText = processText(cSettings.text, playerData);
    $: textStyle = createTextStyle(cSettings);
</script>

<div class="text" class:text--pill={container === "Pill"} style={textStyle}>
    {processedText}
</div>

<style lang="scss">
    .text {
        white-space: nowrap;
        user-select: none;
        pointer-events: none;
        z-index: 1000;
    }

    .text.text--pill {
        padding: 6px 9px;
        line-height: 1.15;
        background: var(--modern-hud-surface-soft, rgba(15, 18, 23, 0.84));
        border-radius: 999px;
        box-shadow: 0 5px 14px rgba(0, 0, 0, 0.16);
    }
</style>
