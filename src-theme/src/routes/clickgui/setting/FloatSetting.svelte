<script lang="ts">
    import "nouislider/dist/nouislider.css";
    import "./nouislider.scss";
    import {createEventDispatcher, onMount} from "svelte";
    import noUiSlider, {type API} from "nouislider";
    import type {FloatSetting, ModuleSetting,} from "../../../integration/types";
    import ValueInput from "./common/ValueInput.svelte";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";

    export let setting: ModuleSetting;

    const cSetting = setting as FloatSetting;

    const dispatch = createEventDispatcher();

    let slider: HTMLElement;
    let apiSlider: API;
    let warningMessage: string | undefined;

    $: warningMessage = cSetting.warning !== undefined && cSetting.value > cSetting.warning.threshold
        ? cSetting.warning.message
        : undefined;

    onMount(() => {
        let step = 0.01;

        if (cSetting.range.to > 100) {
            step = 0.1;
        } else if (cSetting.range.to <= 0.1) {
            step = 0.0001;
        } else if (cSetting.range.to <= 1.0) {
            step = 0.001;
        }

        apiSlider = noUiSlider.create(slider, {
            start: cSetting.value,
            connect: "lower",
            range: {
                min: cSetting.range.from,
                max: cSetting.range.to,
            },
            step: step,
            format: {
                to: (value) => parseFloat(value.toFixed(4)), // Display up to 4 decimal places
                from: (value) => parseFloat(value), // Convert back to float
            }
        });

        apiSlider.on("update", (values) => {
            const newValue = parseFloat(values[0].toString());

            cSetting.value = newValue;
            setting = { ...cSetting };
        });

        apiSlider.on("set", () => {
            dispatch("change");
        });
    });
</script>

<div class="setting" class:has-suffix={cSetting.suffix !== ""} class:has-warning={warningMessage !== undefined}>
    <div class="name">{$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}</div>
    <div class="value">
        <ValueInput valueType="float" value={cSetting.value}
                    on:change={(e) => apiSlider.set(e.detail.value)}/>
    </div>
    {#if cSetting.suffix !== ""}
        <div class="suffix">{cSetting.suffix}</div>
    {/if}
    <div bind:this={slider} class="slider"></div>
    {#if warningMessage !== undefined}
        <div class="warning" role="status">{warningMessage}</div>
    {/if}
</div>

<style lang="scss">

    .setting {
        padding: var(--clickgui-slider-setting-padding, 7px 0 2px 0);
        display: grid;
        grid-template-areas:
            "a b"
            "d d";
        grid-template-columns: 1fr max-content;
        column-gap: var(--clickgui-setting-control-gap, 5px);

        /* animation fix */
        min-height: var(--clickgui-slider-setting-min-height, 46px);
    }

    .setting.has-suffix {
        grid-template-areas:
            "a b c"
            "d d d";
        grid-template-columns: 1fr max-content max-content;
    }

    .setting.has-warning {
        grid-template-areas:
            "a b"
            "d d"
            "e e";
    }

    .setting.has-suffix.has-warning {
        grid-template-areas:
            "a b c"
            "d d d"
            "e e e";
    }

    .suffix,
    .setting {
        color: var(--clickgui-text-color);
        font-weight: 500;
        font-size: var(--clickgui-control-font-size, 12px);
    }

    .name {
        grid-area: a;
        font-weight: 500;
    }

    .value {
        grid-area: b;
    }

    .suffix {
        grid-area: c;
    }

    .slider {
        grid-area: d;
        padding-right: var(--clickgui-slider-end-padding, 10px);
    }

    .warning {
        grid-area: e;
        color: var(--warning-color);
        font-size: var(--clickgui-control-font-size, 12px);
        font-weight: 500;
        padding-top: 4px;
    }
</style>
