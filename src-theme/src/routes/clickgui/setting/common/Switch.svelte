<script lang="ts">
    import {createEventDispatcher} from "svelte";

    export let value: boolean;
    export let name: string;

    const dispatch = createEventDispatcher();
</script>

<label class="switch-container">
    <span class="switch">
        <input type="checkbox" bind:checked={value} on:change={() => dispatch("change")}/>
        <span class="slider"></span>
    </span>

    <span class="name">{name}</span>
</label>

<style lang="scss">

  .switch-container {
    display: flex;
    align-items: center;
    cursor: pointer;
  }

  .name {
    font-weight: 500;
    color: var(--clickgui-text-color);
    font-size: var(--clickgui-control-font-size, 12px);
    margin-left: var(--clickgui-switch-label-gap, 7px);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .slider {
    position: absolute;
    top: var(--clickgui-switch-track-offset, 2px);
    left: 0;
    right: 0;
    bottom: 0;
    background-color: var(--clickgui-switch-track-color);
    transition: ease var(--clickgui-switch-transition-duration, .4s);
    height: var(--clickgui-switch-track-height, 8px);
    border-radius: var(--clickgui-switch-track-radius, 4px);

    &::before {
      position: absolute;
      content: "";
      height: var(--clickgui-switch-thumb-size, 12px);
      width: var(--clickgui-switch-thumb-size, 12px);
      top: var(--clickgui-switch-thumb-offset, -2px);
      left: 0;
      background-color: var(--clickgui-switch-thumb-color);
      transition: ease var(--clickgui-switch-transition-duration, .4s);
      border-radius: var(--clickgui-switch-thumb-radius, 50%);
    }
  }

  .switch {
    position: relative;
    width: var(--clickgui-switch-width, 22px);
    height: var(--clickgui-switch-height, 12px);

    input {
      display: none;
    }

    input:checked + .slider {
      background-color: var(--clickgui-switch-track-active-color);
    }

    input:checked + .slider:before {
      transform: translateX(var(--clickgui-switch-thumb-travel, 10px));
      background-color: var(--clickgui-switch-thumb-active-color);
    }
  }
</style>
