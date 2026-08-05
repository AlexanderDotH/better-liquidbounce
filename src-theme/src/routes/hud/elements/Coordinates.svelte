<script lang="ts">
    import {onMount} from "svelte";
    import type {ClientPlayerDataEvent} from "../../../integration/events";
    import {getPlayerData} from "../../../integration/rest";
    import type {PlayerData, Vec3} from "../../../integration/types";
    import {listen} from "../../../integration/ws";

    const EMPTY_COORDINATE = "—";

    let playerData: PlayerData | null = null;
    let refreshGeneration = 0;
    let blockPosition: Vec3 | undefined;
    let x = EMPTY_COORDINATE;
    let y = EMPTY_COORDINATE;
    let z = EMPTY_COORDINATE;

    listen("clientPlayerData", (event: ClientPlayerDataEvent) => {
        refreshGeneration += 1;
        playerData = event.playerData;
    });

    listen("socketReady", () => {
        void refreshPlayerData();
    });

    listen("disconnect", () => {
        refreshGeneration += 1;
        playerData = null;
    });

    async function refreshPlayerData() {
        const generation = ++refreshGeneration;

        try {
            const nextPlayerData = await getPlayerData();
            if (generation === refreshGeneration) {
                playerData = hasBlockPosition(nextPlayerData) ? nextPlayerData : null;
            }
        } catch {
            if (generation === refreshGeneration) {
                playerData = null;
            }
        }
    }

    function hasBlockPosition(candidate: PlayerData): boolean {
        const position = candidate?.blockPosition;

        return position !== undefined
            && Number.isFinite(position.x)
            && Number.isFinite(position.y)
            && Number.isFinite(position.z);
    }

    function formatCoordinate(value: number | undefined): string {
        if (value === undefined || !Number.isFinite(value)) {
            return EMPTY_COORDINATE;
        }

        return Math.trunc(value).toString();
    }

    onMount(refreshPlayerData);

    $: blockPosition = playerData?.blockPosition;
    $: x = formatCoordinate(blockPosition?.x);
    $: y = formatCoordinate(blockPosition?.y);
    $: z = formatCoordinate(blockPosition?.z);
</script>

<div class="coordinate-pill" aria-label={`Coordinates: X ${x}, Y ${y}, Z ${z}`}>
    <span class="coordinate-axis"><span class="axis-label">X</span><span class="axis-value">{x}</span></span>
    <span class="coordinate-axis"><span class="axis-label">Y</span><span class="axis-value">{y}</span></span>
    <span class="coordinate-axis"><span class="axis-label">Z</span><span class="axis-value">{z}</span></span>
</div>

<style lang="scss">
    .coordinate-pill {
        display: inline-flex;
        align-items: center;
        gap: 0;
        padding: 6px 9px;
        color: rgba(255, 255, 255, 0.96);
        background: rgba(15, 18, 23, 0.84);
        border: 1px solid rgba(255, 255, 255, 0.1);
        border-radius: 999px;
        box-shadow: 0 5px 14px rgba(0, 0, 0, 0.16);
        font-family: Inter, sans-serif;
        font-size: 12px;
        font-variant-numeric: tabular-nums;
        line-height: 1.15;
        white-space: nowrap;
        user-select: none;
        pointer-events: none;
    }

    .coordinate-axis {
        display: inline-flex;
        align-items: baseline;
        gap: 4px;
    }

    .coordinate-axis + .coordinate-axis {
        margin-left: 8px;
        padding-left: 8px;
        border-left: 1px solid rgba(255, 255, 255, 0.12);
    }

    .axis-label {
        color: rgba(255, 255, 255, 0.48);
        font-size: 10px;
        font-weight: 600;
    }

    .axis-value {
        min-width: 1ch;
        color: rgba(255, 255, 255, 0.96);
        font-weight: 600;
        text-align: right;
    }
</style>
