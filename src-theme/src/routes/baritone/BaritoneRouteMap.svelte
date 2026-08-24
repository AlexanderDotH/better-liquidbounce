<script lang="ts">
    import type {BaritonePoint} from "../../integration/baritone";

    let {points = []} = $props<{points?: BaritonePoint[]}>();

    const width = 640;
    const height = 300;
    const padding = 24;

    let projected = $derived.by(() => projectRoute(points));
    let path = $derived(projected.map((point, index) => `${index === 0 ? "M" : "L"}${point.x} ${point.y}`).join(" "));

    function projectRoute(route: BaritonePoint[]): {x: number; y: number}[] {
        if (!route.length) {
            return [];
        }

        const xs = route.map(point => point.x);
        const zs = route.map(point => point.z);
        const minX = Math.min(...xs);
        const maxX = Math.max(...xs);
        const minZ = Math.min(...zs);
        const maxZ = Math.max(...zs);
        const spanX = Math.max(1, maxX - minX);
        const spanZ = Math.max(1, maxZ - minZ);
        const scale = Math.min((width - padding * 2) / spanX, (height - padding * 2) / spanZ);
        const drawnWidth = spanX * scale;
        const drawnHeight = spanZ * scale;
        const offsetX = (width - drawnWidth) / 2;
        const offsetY = (height - drawnHeight) / 2;

        return route.map(point => ({
            x: offsetX + (point.x - minX) * scale,
            y: offsetY + (point.z - minZ) * scale,
        }));
    }
</script>

<div class="route-map" class:empty={!projected.length}>
    {#if projected.length}
        <svg
                viewBox={`0 0 ${width} ${height}`}
                role="img"
                aria-label={`Top-down Baritone route with ${points.length} path points`}
                preserveAspectRatio="xMidYMid meet"
        >
            <defs>
                <pattern id="baritone-route-grid" width="24" height="24" patternUnits="userSpaceOnUse">
                    <path d="M 24 0 L 0 0 0 24" fill="none" stroke="currentColor" stroke-width="0.75"/>
                </pattern>
                <linearGradient id="baritone-route-line" x1="0" x2="1">
                    <stop offset="0" stop-color="color-mix(in srgb, var(--accent-color) 55%, white)"/>
                    <stop offset="1" stop-color="color-mix(in srgb, var(--accent-color) 88%, white)"/>
                </linearGradient>
            </defs>
            <rect class="grid" width={width} height={height} fill="url(#baritone-route-grid)"/>
            <path class="route-shadow" d={path}/>
            <path class="route-line" d={path}/>
            <circle class="route-start" cx={projected[0].x} cy={projected[0].y} r="6"/>
            <circle class="route-end" cx={projected.at(-1)?.x} cy={projected.at(-1)?.y} r="7"/>
        </svg>
        <div class="map-legend" aria-hidden="true">
            <span><i class="start"></i> Start</span>
            <span><i class="end"></i> Goal</span>
            <span>{points.length} points</span>
        </div>
    {:else}
        <div class="empty-route" role="status">
            <svg aria-hidden="true" viewBox="0 0 24 24">
                <path d="M5 4a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm14 10a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM7.7 8.3l8.6 7.4m-9.1 0 3.1-3.1m3.4-3.4 3.1-3.1"/>
            </svg>
            <strong>No route yet</strong>
            <span>Choose a task to calculate a path.</span>
        </div>
    {/if}
</div>

<style lang="scss">
  .route-map {
    position: relative;
    min-height: 220px;
    overflow: hidden;
    color: rgba(255, 255, 255, 0.09);
    background:
      radial-gradient(circle at 70% 18%, color-mix(in srgb, var(--accent-color) 10%, transparent), transparent 42%),
      rgba(4, 7, 10, 0.32);
    border: 1px solid var(--baritone-border, rgba(255, 255, 255, 0.1));
    border-radius: 12px;
  }

  .route-map.empty {
    display: grid;
    place-items: center;
  }

  svg {
    display: block;
    width: 100%;
    height: 100%;
    min-height: 220px;
  }

  .grid {
    color: rgba(255, 255, 255, 0.075);
  }

  .route-shadow,
  .route-line {
    fill: none;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  .route-shadow {
    stroke: rgba(0, 0, 0, 0.52);
    stroke-width: 8;
  }

  .route-line {
    stroke: url(#baritone-route-line);
    stroke-width: 3;
  }

  .route-start {
    fill: #e7edf4;
    stroke: rgba(0, 0, 0, 0.55);
    stroke-width: 2;
  }

  .route-end {
    fill: color-mix(in srgb, var(--accent-color) 86%, white);
    stroke: rgba(0, 0, 0, 0.6);
    stroke-width: 2;
  }

  .map-legend {
    position: absolute;
    right: 10px;
    bottom: 10px;
    display: flex;
    gap: 12px;
    padding: 7px 10px;
    color: var(--baritone-text-secondary, #aeb5bf);
    background: rgba(9, 12, 17, 0.82);
    border: 1px solid var(--baritone-border, rgba(255, 255, 255, 0.1));
    border-radius: 999px;
    font-size: 10px;
    backdrop-filter: blur(12px);
  }

  .map-legend span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }

  .map-legend i {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }

  .map-legend .start {
    background: #e7edf4;
  }

  .map-legend .end {
    background: color-mix(in srgb, var(--accent-color) 86%, white);
  }

  .empty-route {
    display: grid;
    justify-items: center;
    gap: 7px;
    color: var(--baritone-text-muted, #8d96a3);
    text-align: center;
  }

  .empty-route svg {
    width: 34px;
    min-height: 0;
    color: color-mix(in srgb, var(--accent-color) 65%, #c7cbd1);
    fill: none;
    stroke: currentColor;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.5;
  }

  .empty-route strong {
    color: var(--baritone-text-primary, #eef1f5);
    font-size: 13px;
  }

  .empty-route span {
    font-size: 11px;
  }

  @media (max-width: 720px) {
    .map-legend {
      left: 10px;
      justify-content: center;
    }
  }
</style>
