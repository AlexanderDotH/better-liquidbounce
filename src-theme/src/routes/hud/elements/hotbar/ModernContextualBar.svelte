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

{#key data.mode}
    <section
            class="contextual-island"
            class:contextual-island--locator={data.mode === "locator"}
            data-mode={data.mode}
            aria-label="Contextual information"
            transition:fly={{y: 4, duration: motionDuration}}
    >
        {#if data.mode === "experience"}
            <div class="mode-summary">
                <span class="mode-emoji" aria-hidden="true">✨</span>
                <span class="mode-copy">Experience</span>
                <strong class="mode-value" aria-label="Experience level">{data.level}</strong>
            </div>
            <div class="contextual-progress" aria-label="Experience progress">
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
{/key}

<style lang="scss">
  .contextual-island {
    display: grid;
    grid-template-columns: 116px minmax(0, 1fr);
    align-items: center;
    gap: 10px;
    width: 405px;
    min-height: 30px;
    margin-bottom: 5px;
    padding: 5px 8px;
    overflow: hidden;
    color: var(--modern-hud-text, #eef1f5);
    background: var(--modern-hud-surface-soft, rgba(15, 18, 23, 0.84));
    border: 1px solid var(--modern-hud-border, rgba(255, 255, 255, 0.1));
    border-radius: 10px;
    box-shadow: var(--modern-hud-shadow, 0 12px 30px rgba(0, 0, 0, 0.24));
  }

  .mode-summary,
  .locator-heading {
    display: flex;
    align-items: center;
    min-width: 0;
    gap: 6px;
  }

  .mode-emoji,
  .waypoint-emoji {
    font-family: "Noto Color Emoji", "Segoe UI Emoji", "Twemoji Mozilla", sans-serif;
  }

  .mode-emoji {
    flex: 0 0 auto;
    font-size: 14px;
    line-height: 1;
  }

  .mode-copy,
  .locator-heading span:last-child {
    min-width: 0;
    overflow: hidden;
    color: var(--modern-hud-text-muted, #919aa6);
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.02em;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mode-value {
    margin-left: auto;
    color: var(--modern-hud-text, #eef1f5);
    font-size: 10px;
    font-variant-numeric: tabular-nums;
    font-weight: 700;
  }

  .contextual-progress {
    height: 8px;
    overflow: hidden;
    background: var(--modern-hud-surface-muted, rgba(15, 18, 23, 0.78));
    border: 1px solid var(--modern-hud-border-soft, rgba(255, 255, 255, 0.07));
    border-radius: 999px;
  }

  .contextual-progress span {
    display: block;
    height: 100%;
    background: linear-gradient(90deg, color-mix(in srgb, var(--accent-color) 72%, #9bb1ff), var(--accent-color));
    border-radius: inherit;
    box-shadow: 0 0 10px color-mix(in srgb, var(--accent-color) 35%, transparent);
    transition: width var(--modern-hud-motion, 180ms) var(--modern-hud-easing, ease-out);
  }

  .contextual-progress--jump span {
    background: linear-gradient(90deg, #64c6b0, #8be0c1);
    box-shadow: 0 0 10px rgba(100, 198, 176, 0.28);
  }

  .contextual-progress--cooldown span {
    background: linear-gradient(90deg, #d08a53, #efb56d);
    box-shadow: none;
  }

  .contextual-island--locator {
    grid-template-columns: 70px minmax(0, 1fr) 24px;
    gap: 7px;
    min-height: 34px;
    padding-block: 4px;
  }

  .locator-track {
    position: relative;
    height: 24px;
  }

  .locator-axis {
    position: absolute;
    top: 50%;
    right: 0;
    left: 0;
    height: 4px;
    background: rgba(255, 255, 255, 0.065);
    border: 1px solid rgba(255, 255, 255, 0.045);
    border-radius: 999px;
    transform: translateY(-50%);
  }

  .locator-center {
    position: absolute;
    top: 50%;
    left: 50%;
    z-index: 1;
    width: 2px;
    height: 10px;
    background: rgba(238, 241, 245, 0.62);
    border-radius: 999px;
    transform: translate(-50%, -50%);
  }

  .locator-marker {
    position: absolute;
    top: 50%;
    z-index: 2;
    display: grid;
    place-items: center;
    width: 20px;
    height: 20px;
    color: white;
    background: color-mix(in srgb, var(--marker-color) 80%, #11151b);
    border: 1px solid color-mix(in srgb, var(--marker-color) 78%, white 14%);
    border-radius: 7px;
    box-shadow: 0 3px 9px rgba(0, 0, 0, 0.28);
    transform: translate(-50%, -50%);
    transition: left var(--modern-hud-motion, 180ms) var(--modern-hud-easing, ease-out);
  }

  .locator-marker--player {
    width: 22px;
    height: 22px;
    overflow: visible;
    background: #11151b;
    border-radius: 7px;
  }

  .player-head {
    position: relative;
    display: block;
    width: 18px;
    height: 18px;
    overflow: hidden;
    border-radius: 5px;
    image-rendering: pixelated;
  }

  .player-head-layer {
    position: absolute;
    width: 144px;
    max-width: none;
    height: auto;
    image-rendering: pixelated;
  }

  .player-head-layer--face {
    top: -18px;
    left: -18px;
  }

  .player-head-layer--hat {
    top: -18px;
    left: -90px;
  }

  .waypoint-emoji {
    font-size: 12px;
    filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.35));
  }

  .elevation {
    position: absolute;
    right: -4px;
    bottom: -4px;
    display: grid;
    place-items: center;
    width: 10px;
    height: 10px;
    color: #f7f9fc;
    font-size: 8px;
    font-weight: 800;
    line-height: 1;
    background: #202731;
    border: 1px solid rgba(255, 255, 255, 0.18);
    border-radius: 999px;
  }

  .locator-count {
    display: grid;
    place-items: center;
    min-width: 22px;
    height: 20px;
    color: var(--modern-hud-text-muted, #919aa6);
    font-size: 9px;
    font-variant-numeric: tabular-nums;
    font-weight: 700;
    background: rgba(255, 255, 255, 0.045);
    border-radius: 6px;
  }
</style>
