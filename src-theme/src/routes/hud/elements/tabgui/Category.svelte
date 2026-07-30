<script lang="ts">
    import {fade} from "svelte/transition";
    import {hudMotionDuration, prefersReducedMotion} from "../../motion/hudMotion";

    export let name: string;
    export let selected: boolean;
    export let variant: "classic" | "modern" = "classic";

    let motionDuration = 200;

    $: iconPath = `img/hud/tabgui/${name.toLowerCase()}.svg`;
    $: iconUrl = new URL(iconPath, location.href).href;
    $: motionDuration = hudMotionDuration(variant, $prefersReducedMotion);
</script>

<div class="category" class:selected class:modern={variant === "modern"}>
    <div class="icon">
        <span
            class="category-icon"
            aria-hidden="true"
            transition:fade={{ duration: motionDuration }}
            style={`mask-image: url('${iconUrl}');`}
        >
            <img class="category-icon-size" src={iconPath} alt="" />
        </span>
    </div>
    <div class="name">
        {name}
    </div>
</div>

<style lang="scss">

    .name {
        font-weight: 500;
        color: var(--tabgui-text-color);
        font-size: 14px;
        width: 100%;
        padding: 7px 12px 7px 12px;

        background: linear-gradient(
            to left,
            var(--tabgui-category-background-color) 50%,
            var(--tabgui-category-active-background-color) 50%
        );
        background-size: 200% 100%;
        background-position: right bottom;
        will-change: background-position;
        transition: background-position 0.2s ease-out;
        overflow: hidden;
    }

    .category {
        display: flex;

        &.selected .icon {
            color: var(--accent-color);
        }

        &.selected .name {
            background-position: left bottom;
        }
    }

    .category.modern {
        align-items: center;
        gap: 7px;
        min-height: 28px;
        padding: 0 8px;
        border-radius: 8px;
        transition:
            color var(--modern-hud-motion) var(--modern-hud-easing),
            background-color var(--modern-hud-motion) var(--modern-hud-easing);
    }

    .category.modern.selected {
        background: rgba(70, 119, 255, 0.16);
    }

    .category.modern .name,
    .category.modern.selected .name {
        width: auto;
        padding: 0;
        color: var(--modern-hud-text);
        font-size: 11px;
        background: none;
    }

    .category.modern .icon {
        flex: 0 0 18px;
        width: 18px;
        height: 18px;
        color: var(--modern-hud-text-muted);
        background: transparent;
    }

    .category.modern.selected .icon {
        color: #4677ff;
    }

    .icon {
        background-color: var(--tabgui-icon-background-color);
        color: var(--tabgui-text-color);
        width: 62px;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: color 0.2s ease-out;
    }

    .category-icon {
        display: inline-block;
        background-color: currentColor;
        mask-position: center;
        mask-repeat: no-repeat;
        mask-size: contain;
    }

    .category-icon-size {
        display: block;
        visibility: hidden;
    }
</style>
