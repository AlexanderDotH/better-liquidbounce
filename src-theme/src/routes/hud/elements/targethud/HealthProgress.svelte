<script lang="ts">
    export let maxHealth: number;
    export let health: number;
    export let compact = false;

    function healthPercentage(health: number, maxHealth: number): number {
        if (!Number.isFinite(maxHealth) || maxHealth <= 0 || !Number.isFinite(health)) {
            return 0;
        }

        return Math.min(100, Math.max(0, Math.ceil(health / maxHealth * 100)));
    }

    let width = healthPercentage(health, maxHealth);
    $: width = healthPercentage(health, maxHealth);
</script>

<div
        class="health-progress"
        class:health-progress--compact={compact}
        data-compact={compact ? "true" : undefined}
>
    <div class="thumb" style="width: {width}%;"></div>
</div>

<style lang="scss">

    .health-progress {
        position: relative;
    }

    .thumb {
        height: 8px;
        background-color: var(--targethud-health-progress-color);
        transition: ease width 0.5s;
    }

    .health-progress.health-progress--compact[data-compact="true"] {
        height: 3px;
        padding: 0;
        overflow: hidden;
        background: rgba(255, 255, 255, 0.08);
        border-radius: 999px;

        .thumb {
            height: 3px;
            background: var(--accent-color, #4677ff);
            border-radius: 999px;
            transition: width 180ms cubic-bezier(0.2, 0.8, 0.2, 1);
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .health-progress.health-progress--compact .thumb {
            transition-duration: 0ms;
        }
    }
</style>
