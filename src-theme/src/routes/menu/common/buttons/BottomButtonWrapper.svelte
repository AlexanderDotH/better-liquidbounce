<script lang="ts">
    import {fly} from "svelte/transition";
    import {MENU_TRANSITION_MS, MENU_VERTICAL_OFFSET_PX} from "../menuMotion";
</script>

<div class="bottom-button-wrapper" transition:fly|global={{duration: MENU_TRANSITION_MS, y: MENU_VERTICAL_OFFSET_PX}}>
    <slot/>
</div>

<style lang="scss">
  .bottom-button-wrapper {
    display: flex;
    justify-content: space-between;
  }
</style>
