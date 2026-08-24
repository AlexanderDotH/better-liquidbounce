<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import type {BlockHitResult, ModuleSetting, Setting, Vec, Vec3Setting, VecAxis} from "../../../integration/types";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import {getCrosshairData, getPlayerData} from "../../../integration/rest";
    import {cefTextInput} from "./common/cefTextInput";

    export let setting: ModuleSetting;
    export let vecAxes: VecAxis[];
    export let step: number;

    const cSetting = setting as Setting<Vec<typeof vecAxes[number]>>;
    const useLocateButton = (setting as Vec3Setting).useLocateButton ?? false;
    const axisInputValues = Object.fromEntries(
        vecAxes.map(axis => [axis, String(cSetting.value[axis])]),
    ) as Record<VecAxis, string>;

    const dispatch = createEventDispatcher();

    function handleChange() {
        setting = {...cSetting};
        dispatch("change");
    }

    function handleAxisChange(axis: VecAxis, value: string) {
        axisInputValues[axis] = value;
        const parsed = Number(value);
        if (value.trim() === "" || !Number.isFinite(parsed)) {
            return;
        }

        cSetting.value[axis] = parsed;
        handleChange();
    }

    function syncAxisInputValues() {
        for (const axis of vecAxes) {
            axisInputValues[axis] = String(cSetting.value[axis]);
        }
    }

    async function locate() {
        const hitResult = await getCrosshairData();

        if (hitResult.type === "block") {
            const blockHitResult = hitResult as BlockHitResult;
            (cSetting as Vec3Setting).value = blockHitResult.blockPos;
        } else {
            const playerData = await getPlayerData();
            (cSetting as Vec3Setting).value = playerData.blockPosition;
        }
        syncAxisInputValues();
        handleChange();
    }
</script>

<div class="setting">
    <div class="name">{$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}</div>
    <div class="input-group"
         style="grid-template-columns: repeat({vecAxes.length}, 1fr) {useLocateButton ? '20px' : ''}">
        {#each vecAxes as axis (axis)}
            <input
                    type="text"
                    inputmode="decimal"
                    {step}
                    class="value"
                    spellcheck="false"
                    readonly
                    placeholder={axis.toUpperCase()}
                    value={axisInputValues[axis]}
                    use:cefTextInput={{
                        getValue: () => axisInputValues[axis],
                        onChange: (value) => handleAxisChange(axis, value),
                    }}
            />
        {/each}
        {#if useLocateButton}
            <button class="locate-btn" on:click={locate} title="Locate">&#x2299;</button>
        {/if}
    </div>
</div>

<style lang="scss">

  .setting {
    padding: var(--clickgui-setting-padding, 7px 0);
  }

  .name {
    font-weight: 500;
    color: var(--clickgui-text-color);
    font-size: var(--clickgui-control-font-size, 12px);
    margin-bottom: var(--clickgui-setting-label-gap, 5px);
  }

  .input-group {
    display: grid;
    column-gap: var(--clickgui-setting-control-gap, 5px);

    input.value {
      width: 100%;
      background-color: var(--clickgui-input-background-color);
      font-family: monospace;
      font-size: var(--clickgui-control-font-size, 12px);
      color: var(--clickgui-text-color);
      border: none;
      border-bottom: solid var(--clickgui-control-border-width, 2px) var(--clickgui-input-border-color);
      padding: var(--clickgui-input-padding, 5px);
      border-radius: var(--clickgui-control-radius, 3px);
      transition: ease border-color var(--clickgui-control-transition-duration, .2s);
      appearance: textfield;

      &::-webkit-scrollbar {
        background-color: transparent;
      }

      /* Hide the number input spinner buttons */
      &::-webkit-outer-spin-button,
      &::-webkit-inner-spin-button {
        -webkit-appearance: none;
        margin: 0;
      }
    }

    .locate-btn {
      display: block;
      background-color: transparent;
      border: none;
      cursor: pointer;
      color: var(--clickgui-text-color);
      font-size: var(--clickgui-control-font-size, 12px);
      text-align: right;
    }
  }
</style>
