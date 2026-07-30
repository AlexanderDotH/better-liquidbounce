<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../../theme/theme_config";
    import {shiftDescription} from "./shiftDescription";

    export let name: string | null;
    export let options: string[];
    export let value: string;
    export let extendedDescriptions: Record<string, string | undefined> | undefined = undefined;

    const dispatch = createEventDispatcher();

    let expanded = false;
    let dropdownHead: HTMLElement;

    function windowClickHide(e: MouseEvent) {
        if (!dropdownHead.contains(e.target as Node)) {
            expanded = false;
        }
    }

    function updateValue(v: string) {
        value = v;
        dispatch("change");
    }
</script>

<svelte:window on:click={windowClickHide}/>
<!-- svelte-ignore a11y-click-events-have-key-events -->
<!-- svelte-ignore a11y-no-static-element-interactions -->
<div class="dropdown" class:expanded on:click={() => (expanded = !expanded)}>
    <div
            class="head"
            bind:this={dropdownHead}
            use:shiftDescription={{ getText: () => extendedDescriptions?.[value] }}
    >
        {#if name !== null}
            <span class="text">{$spaceSeperatedNames ? convertToSpacedString(name) : name}
                &bull; {$spaceSeperatedNames ? convertToSpacedString(value) : value}</span>
        {:else}
            <span class="text">{$spaceSeperatedNames ? convertToSpacedString(value) : value}</span>
        {/if}
    </div>

    {#if expanded}
        <div class="options">
            {#each options as o (o)}
                <div
                        class="option"
                        class:active={o === value}
                        use:shiftDescription={{ getText: () => extendedDescriptions?.[o] }}
                        on:click={() => updateValue(o)}
                >
                    {$spaceSeperatedNames ? convertToSpacedString(o) : o}
                </div>
            {/each}
        </div>
    {/if}
</div>

<style lang="scss">
  @use "../../icon-settings-expand" as *;

  .dropdown {
    position: relative;

    &.expanded {
      .text::after {
        transform: translateY(-50%) rotate(0);
        opacity: 1;
      }

      .head {
        border-radius:
          var(--clickgui-dropdown-radius, 3px)
          var(--clickgui-dropdown-radius, 3px)
          0
          0;
      }
    }
  }

  .head {
    background-color: var(--clickgui-dropdown-trigger-background-color);
    padding: var(--clickgui-control-padding, 6px 10px);
    cursor: pointer;
    display: flex;
    align-items: center;
    position: relative;
    border-radius: var(--clickgui-dropdown-radius, 3px);
    transition: ease border-radius var(--clickgui-control-transition-duration, .2s);

    .text {
      font-weight: 500;
      color: var(--clickgui-text-color);
      font-size: var(--clickgui-control-font-size, 12px);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      margin-right: 20px;
    }

    .text::after {
      @include icon-settings-expand();
    }
  }

  .options {
    padding: var(--clickgui-control-padding, 6px 10px);
    background-color: var(--clickgui-dropdown-background-color);
    border: solid var(--clickgui-dropdown-border-width, 1px) var(--clickgui-dropdown-border-color);
    border-top: none;
    border-radius:
      0
      0
      var(--clickgui-dropdown-radius, 3px)
      var(--clickgui-dropdown-radius, 3px);
    z-index: 9999;
    width: 100%;
    position: absolute;

    .option {
      color: var(--clickgui-dropdown-option-color);
      font-weight: 500;
      font-size: var(--clickgui-control-font-size, 12px);
      padding: var(--clickgui-dropdown-option-padding, 5px 0);
      cursor: pointer;
      text-align: center;
      transition: ease color var(--clickgui-control-transition-duration, 0.2s);

      &:hover {
        color: var(--clickgui-dropdown-option-hover-color);
      }

      &.active {
        color: var(--clickgui-dropdown-option-selected-color);
      }
    }
  }
</style>
