<script lang="ts">
    import {fly} from "svelte/transition";
    import {MENU_HORIZONTAL_OFFSET_PX, MENU_TRANSITION_MS} from "../menuMotion";
</script>

<div class="option-bar" transition:fly|global={{duration: MENU_TRANSITION_MS, x: MENU_HORIZONTAL_OFFSET_PX}}>
    <slot />
</div>

<style lang="scss">

    .option-bar {
      background-color: var(--menu-button-container-background-color);
      padding: 15px 30px;
      display: flex;
      border-radius: 5px;
      align-items: center;
      column-gap: 30px;
      row-gap: 15px;
      flex-wrap: wrap;
      margin-bottom: 25px;
    }
</style>
