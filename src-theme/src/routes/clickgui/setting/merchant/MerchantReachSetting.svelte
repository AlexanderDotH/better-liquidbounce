<script lang="ts">
    import "nouislider/dist/nouislider.css";
    import "../nouislider.scss";
    import {createEventDispatcher, onMount} from "svelte";
    import noUiSlider, {type API} from "nouislider";
    import type {
        MerchantReach,
        MerchantReachSetting,
        ModuleSetting,
        Range,
    } from "../../../../integration/types";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../../theme/theme_config";
    import ValueInput from "../common/ValueInput.svelte";

    export let setting: ModuleSetting;

    const dispatch = createEventDispatcher();

    let cSetting: MerchantReachSetting;
    let normalizedValue: MerchantReach;
    let rangeSlider: HTMLElement;
    let wallRangeSlider: HTMLElement;
    let rangeApi: API | undefined;
    let wallRangeApi: API | undefined;

    $: cSetting = setting as MerchantReachSetting;
    $: normalizedValue = normalizeReach(cSetting.value);
    $: syncSlider(rangeApi, normalizedValue.range);
    $: syncSlider(wallRangeApi, normalizedValue.wallRange);

    onMount(() => {
        rangeApi = createSlider(rangeSlider, normalizedValue.range, cSetting.rangeBounds);
        wallRangeApi = createSlider(wallRangeSlider, normalizedValue.wallRange, cSetting.wallRangeBounds);

        rangeApi.on("update", values => updateRange(parseSliderValue(values)));
        wallRangeApi.on("update", values => updateWallRange(parseSliderValue(values)));
        rangeApi.on("set", saveSetting);
        wallRangeApi.on("set", saveSetting);

        labelSlider(rangeApi, "Range");
        labelSlider(wallRangeApi, "Wall range");

        return () => {
            rangeApi?.destroy();
            wallRangeApi?.destroy();
        };
    });

    function createSlider(element: HTMLElement, value: number, bounds: Range): API {
        return noUiSlider.create(element, {
            start: value,
            connect: "lower",
            range: {min: bounds.from, max: bounds.to},
            step: 0.01,
            format: {
                to: current => parseFloat(current.toFixed(4)),
                from: current => parseFloat(current),
            },
        });
    }

    function updateRange(range: number): void {
        commitReach({range, wallRange: cSetting.value.wallRange});
    }

    function updateWallRange(wallRange: number): void {
        commitReach({range: cSetting.value.range, wallRange});
    }

    function commitReach(nextValue: MerchantReach): void {
        const next = normalizeReach(nextValue);
        if (next.range === cSetting.value.range && next.wallRange === cSetting.value.wallRange) {
            return;
        }

        setting = {...cSetting, value: next};
    }

    function normalizeReach(value: MerchantReach): MerchantReach {
        const range = clamp(value.range, cSetting.rangeBounds);
        const wallRange = Math.min(
            range,
            clamp(value.wallRange, cSetting.wallRangeBounds),
        );

        return {range, wallRange};
    }

    function clamp(value: number, bounds: Range): number {
        if (!Number.isFinite(value)) {
            return bounds.from;
        }

        return Math.min(bounds.to, Math.max(bounds.from, value));
    }

    function syncSlider(api: API | undefined, value: number): void {
        if (!api || parseFloat(api.get().toString()) === value) {
            return;
        }

        api.set(value, false);
    }

    function parseSliderValue(values: (string | number)[]): number {
        return parseFloat(values[0].toString());
    }

    function labelSlider(api: API, label: string): void {
        api.target.querySelector(".noUi-handle")?.setAttribute("aria-label", label);
    }

    function saveSetting(): void {
        dispatch("change");
    }
</script>

<div class="setting">
    <div class="title-row">
        <span class="title">{$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}</span>
        {#if cSetting.suffix}
            <span class="suffix">{cSetting.suffix}</span>
        {/if}
    </div>

    <div class="reach-controls">
        <div class="reach-control">
            <div class="control-header">
                <span>Range</span>
                <span class="number" title="Visible merchant range">
                    <ValueInput
                            valueType="float"
                            value={normalizedValue.range}
                            on:change={(event) => rangeApi?.set(event.detail.value)}
                    />
                </span>
            </div>
            <div bind:this={rangeSlider} class="slider"></div>
        </div>

        <div class="reach-control">
            <div class="control-header">
                <span>Wall Range</span>
                <span class="number" title="Occluded merchant range">
                    <ValueInput
                            valueType="float"
                            value={normalizedValue.wallRange}
                            on:change={(event) => wallRangeApi?.set(event.detail.value)}
                    />
                </span>
            </div>
            <div bind:this={wallRangeSlider} class="slider"></div>
        </div>
    </div>
</div>

<style lang="scss">
    .setting {
        min-height: var(--clickgui-merchant-reach-min-height, 112px);
        padding: var(--clickgui-slider-setting-padding, 7px 0 2px 0);
        color: var(--clickgui-text-color);
        font-size: var(--clickgui-control-font-size, 12px);
        font-weight: 500;
    }

    .title-row,
    .control-header {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: var(--clickgui-setting-control-gap, 5px);
    }

    .title {
        font-weight: 600;
    }

    .suffix {
        color: var(--clickgui-text-dimmed-color, var(--clickgui-text-color));
        font-size: var(--clickgui-merchant-reach-suffix-size, 10px);
    }

    .reach-controls {
        display: grid;
        grid-template-columns: minmax(0, 1fr);
        gap: var(--clickgui-merchant-reach-gap, 12px);
        margin-top: var(--clickgui-merchant-reach-top-gap, 6px);
    }

    .reach-control {
        min-width: 0;
    }

    .control-header {
        color: var(--clickgui-text-dimmed-color, var(--clickgui-text-color));
        font-size: var(--clickgui-merchant-reach-label-size, 10px);
    }

    .number {
        min-width: var(--clickgui-merchant-reach-value-width, 30px);
        color: var(--clickgui-text-color);
        text-align: right;
    }

    .number :global(.value) {
        max-width: var(--clickgui-merchant-reach-value-max-width, 44px);
        overflow: hidden;
        text-align: right;
        vertical-align: bottom;
    }

    .slider {
        padding-right: var(--clickgui-slider-end-padding, 10px);
    }
</style>
