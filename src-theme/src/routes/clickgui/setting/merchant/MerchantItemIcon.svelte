<script lang="ts">
    import {itemTextureUrl} from "../../../../integration/rest";

    export let identifier: string;
    export let icon: string | undefined;
    export let size = 24;

    function showFallbackIcon(event: Event) {
        const image = event.currentTarget as HTMLImageElement;

        if (image.dataset.fallback === "true") {
            image.style.visibility = "hidden";
            return;
        }

        image.dataset.fallback = "true";
        image.src = itemTextureUrl("minecraft:barrier");
    }
</script>

<img
    class="merchant-item-icon"
    src={icon ?? itemTextureUrl(identifier)}
    alt=""
    width={size}
    height={size}
    draggable="false"
    on:error={showFallbackIcon}
/>

<style>
    .merchant-item-icon {
        display: block;
        flex: 0 0 auto;
        image-rendering: pixelated;
        object-fit: contain;
        pointer-events: none;
        user-select: none;
        -webkit-user-drag: none;
    }
</style>
