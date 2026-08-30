<script lang="ts">
    import {fly} from "svelte/transition";

    import type {ContextualBarData, LocatorMarker} from "../../../../integration/types";
    import {REST_BASE} from "../../../../integration/host";
    import {hudMotionDuration, prefersReducedMotion} from "../../motion/hudMotion";
    import {
        clampContextualProgress,
        locatorMarkerPercent,
        locatorRgbColor,
        sortLocatorMarkersForRendering,
        waypointEmoji,
    } from "./contextualBarModel";

    export let data: ContextualBarData;

    $: motionDuration = hudMotionDuration("modern", $prefersReducedMotion, 180);
    $: progress = clampContextualProgress(data.cooldown ? 1 : data.progress);
    $: locatorMarkers = sortLocatorMarkersForRendering(data.markers);

    function markerDescription(marker: LocatorMarker): string {
        const elevation = marker.elevation === "above"
            ? "above"
            : marker.elevation === "below"
                ? "below"
                : "level";

        return `${marker.label}, ${marker.distance} blocks, ${elevation}`;
    }
</script>

<section
            class="contextual-island"
            class:contextual-island--experience={data.mode === "experience"}
            class:contextual-island--locator={data.mode === "locator"}
            data-mode={data.mode}
            aria-label="Contextual information"
            transition:fly={{y: 4, duration: motionDuration}}
    >
        {#if data.mode === "experience"}
            <strong class="mode-value mode-value--experience" aria-label="Experience level">{data.level}</strong>
            <div class="contextual-progress contextual-progress--experience" aria-label="Experience progress">
                <span style="width: {progress * 100}%"></span>
            </div>
        {:else if data.mode === "jumpableVehicle"}
            <div class="mode-summary">
                <span class="mode-emoji" aria-hidden="true">🐎</span>
                <span class="mode-copy">{data.cooldown ? "Jump recharging" : "Jump charge"}</span>
                <strong class="mode-value">{data.cooldown ? "Locked" : `${Math.round(progress * 100)}%`}</strong>
            </div>
            <div
                    class="contextual-progress contextual-progress--jump"
                    class:contextual-progress--cooldown={data.cooldown}
                    aria-label={data.cooldown ? "Vehicle jump cooldown" : "Vehicle jump charge"}
            >
                <span style="width: {progress * 100}%"></span>
            </div>
        {:else if data.mode === "locator"}
            <div class="locator-heading" aria-hidden="true">
                <span class="mode-emoji">🧭</span>
                <span>Locator</span>
            </div>
            <div class="locator-track" aria-label="Nearby waypoints">
                <span class="locator-axis"></span>
                <span class="locator-center"></span>

                {#each locatorMarkers as marker (marker.id)}
                    <span
                            class="locator-marker"
                            class:locator-marker--player={marker.kind === "player" && marker.playerUuid !== null}
                            style="left: {locatorMarkerPercent(marker.offset)}%; --marker-color: {locatorRgbColor(marker.color)}"
                            aria-label={markerDescription(marker)}
                    >
                        {#if marker.kind === "player" && marker.playerUuid !== null}
                            <span class="player-head" aria-hidden="true">
                                <img
                                        class="player-head-layer player-head-layer--face"
                                        src="{REST_BASE}/api/v1/client/resource/skin?uuid={marker.playerUuid}"
                                        alt=""
                                />
                                <img
                                        class="player-head-layer player-head-layer--hat"
                                        src="{REST_BASE}/api/v1/client/resource/skin?uuid={marker.playerUuid}"
                                        alt=""
                                />
                            </span>
                        {:else}
                            <span class="waypoint-emoji" aria-hidden="true">{waypointEmoji(marker.style)}</span>
                        {/if}

                        {#if marker.elevation !== "level"}
                            <span class="elevation" aria-hidden="true">
                                {marker.elevation === "above" ? "↑" : "↓"}
                            </span>
                        {/if}
                    </span>
                {/each}
            </div>
            <span class="locator-count" aria-label="Waypoint count">{data.markers.length}</span>
        {/if}
</section>

<style lang="scss">
  @use "./ModernContextualBar.styles";
</style>
