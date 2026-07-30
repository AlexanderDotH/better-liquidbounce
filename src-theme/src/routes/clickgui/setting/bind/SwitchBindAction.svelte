<script lang="ts">
    import type {BindAction} from "../../../../integration/types";
    import {fly} from "svelte/transition";
    import {cubicOut} from 'svelte/easing';

    export let choices: BindAction[];
    export let chosen: typeof choices[number];
    export let onchange: () => any;

    let jiggle = 0;

    /**
     * Switch item among {@link choices}.
     */
    function switchAction() {
        const currentIndex = choices.indexOf(chosen);
        if (currentIndex === -1) {
            throw new Error("Unexpected action: " + chosen);
        }

        const nextIndex = (currentIndex + 1) % choices.length;
        chosen = choices[nextIndex];

        triggerArrowAnimation();
        onchange();
    }

    function triggerArrowAnimation() {
        jiggle++;
    }
</script>

<!-- svelte-ignore a11y_consider_explicit_label -->
<button on:click|stopPropagation={switchAction}>
    <span class="chosen-holder">
        {#key chosen}
            <span
                    class="chosen"
                    in:fly={{ x: 5, duration: 100, delay: 100, easing: cubicOut }}
                    out:fly={{ x: -5, duration: 100, easing: cubicOut }}
            >{chosen}</span>
        {/key}
    </span>

    {#key jiggle}
        <span class="arrow arrow-right"></span>
    {/key}
</button>

<style lang="scss">
  @use "../../icon-settings-expand" as *;

  @keyframes jiggle {
    0% {
      transform: translateX(0);
    }
    50% {
      transform: translateX(2px);
    }
    100% {
      transform: translateX(0);
    }
  }

  .arrow {
    width: var(--clickgui-bind-action-arrow-size, 10px);
    animation: jiggle var(--clickgui-control-transition-duration, 200ms) ease;

    &.arrow-right::after {
      @include icon-settings-expand($size: var(--clickgui-bind-action-arrow-size, 10px), $right: auto);
      color: var(--clickgui-text-dimmed-color);
    }
  }

  .chosen-holder {
    display: grid;

    .chosen {
      font-weight: 500;
      color: var(--clickgui-text-color);
      font-size: var(--clickgui-control-font-size, 12px);
      text-overflow: ellipsis;
      white-space: nowrap;
      grid-column: 1/1;
      grid-row: 1/1;
    }
  }

  button {
    all: unset;
    background: none;
    padding: 0;
    cursor: pointer;
    display: flex;
    gap: var(--clickgui-bind-action-gap, 3px);
    align-items: center;
    position: relative;
    border: none;
  }
</style>
