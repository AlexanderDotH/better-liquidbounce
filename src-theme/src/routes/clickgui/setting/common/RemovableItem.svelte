<script lang="ts">
    import {createEventDispatcher} from "svelte";

    const dispatch = createEventDispatcher();
</script>

<div class="removable-item">
    <slot/>

    <button class="button-remove" title="Remove" on:click={() => dispatch("remove")}>
        <img src="img/clickgui/icon-cross.svg" alt="remove">
    </button>
</div>

<style lang="scss">
  .removable-item {
    display: grid;
    grid-template-columns: 1fr max-content;
    column-gap: var(--clickgui-setting-control-gap, 5px);
    align-items: center;
    padding-right: var(--clickgui-removable-item-padding, 8px);
  }

  .button-remove {
    background-color: transparent;
    border: none;
    cursor: pointer;
  }
</style>
