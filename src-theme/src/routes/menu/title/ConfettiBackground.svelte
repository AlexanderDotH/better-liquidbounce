<script lang="ts">
    import {fade} from "svelte/transition";
    import {portal} from "../../../integration/util";
    import {createConfettiPieces} from "./confettiPieceModel.ts";

    const pieces = createConfettiPieces();
</script>

<div class="confetti-layer" aria-hidden="true" transition:fade|global={{duration: 500}} use:portal>
    {#each pieces as piece (piece.id)}
        <span class={`confetti-piece ${piece.shapeClass}`} style={piece.style}>
            <span class="confetti-bit"></span>
        </span>
    {/each}
</div>

<style>
    .confetti-layer {
        position: fixed;
        inset: 0;
        overflow: hidden;
        pointer-events: none;
        z-index: -1;
        contain: layout style paint;
    }

    .confetti-piece {
        position: absolute;
        top: -18vh;
        left: var(--left);
        opacity: var(--opacity);
        will-change: transform;
        animation: confetti-fall var(--fall-duration) linear infinite;
        animation-delay: var(--fall-delay);
    }

    .confetti-bit {
        display: block;
        width: var(--width);
        height: var(--height);
        background: var(--color);
        border-radius: var(--border-radius);
        filter: blur(var(--blur));
        box-shadow: 0 0 10px var(--confetti-shadow-color);
        transform-origin: center;
        transform-style: preserve-3d;
        backface-visibility: hidden;
        animation: confetti-spin var(--spin-duration) ease-in-out infinite alternate;
    }

    .confetti-piece.is-streamer .confetti-bit {
        border-radius: 999px;
    }

    @keyframes confetti-fall {
        from {
            transform: translate3d(0, -20vh, 0);
        }

        to {
            transform: translate3d(var(--drift), 120vh, 0);
        }
    }

    @keyframes confetti-spin {
        0% {
            transform: rotate(0deg) scaleX(var(--scale)) scaleY(var(--scale));
        }

        25% {
            transform: rotate(calc(var(--flip) * 0.25)) scaleX(calc(var(--scale) * 0.68)) scaleY(calc(var(--scale) * 0.94));
        }

        50% {
            transform: rotate(calc(var(--flip) * 0.5)) scaleX(calc(var(--scale) * 0.48)) scaleY(calc(var(--scale) * 0.88));
        }

        75% {
            transform: rotate(calc(var(--flip) * 0.75)) scaleX(calc(var(--scale) * 0.72)) scaleY(calc(var(--scale) * 0.96));
        }

        100% {
            transform: rotate(var(--flip)) scaleX(var(--scale)) scaleY(var(--scale));
        }
    }
</style>
