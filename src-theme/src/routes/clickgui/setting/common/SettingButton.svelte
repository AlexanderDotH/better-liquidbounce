<script lang="ts">
    export let value: string;
</script>

<button class="setting-button" on:click>{value}</button>

<style lang="scss">
  .setting-button {
    font-family: "Inter", sans-serif;
    font-weight: 500;
    font-size: var(--clickgui-control-font-size, 12px);
    color: var(--clickgui-text-color);
    background-color: var(--clickgui-button-background-color);
    border: none;
    padding: var(--clickgui-control-padding, 6px 10px);
    border-radius: var(--clickgui-control-radius, 3px);
    width: 100%;
    cursor: pointer;
    transition: ease background-color var(--clickgui-control-transition-duration, .2s);

    &:hover {
      background-color: var(--clickgui-button-hover-background-color);
    }
  }
</style>
