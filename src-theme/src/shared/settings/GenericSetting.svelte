<script lang="ts">
    import type {ModuleSetting} from "../../integration/types";
    import ActionSetting from "../../routes/clickgui/setting/ActionSetting.svelte";
    import BooleanSetting from "../../routes/clickgui/setting/BooleanSetting.svelte";
    import ChoiceSetting from "../../routes/clickgui/setting/ChoiceSetting.svelte";
    import ChooseSetting from "../../routes/clickgui/setting/ChooseSetting.svelte";
    import ConfigurableSetting from "../../routes/clickgui/setting/ConfigurableSetting.svelte";
    import FloatRangeSetting from "../../routes/clickgui/setting/FloatRangeSetting.svelte";
    import FloatSetting from "../../routes/clickgui/setting/FloatSetting.svelte";
    import IntRangeSetting from "../../routes/clickgui/setting/IntRangeSetting.svelte";
    import IntSetting from "../../routes/clickgui/setting/IntSetting.svelte";
    import TogglableSetting from "../../routes/clickgui/setting/TogglableSetting.svelte";
    import ColorSetting from "../../routes/clickgui/setting/ColorSetting.svelte";
    import TextSetting from "../../routes/clickgui/setting/TextSetting.svelte";
    import {slide} from "svelte/transition";
    import BindSetting from "../../routes/clickgui/setting/bind/BindSetting.svelte";
    import VectorSetting from "../../routes/clickgui/setting/VectorSetting.svelte";
    import KeySetting from "../../routes/clickgui/setting/KeySetting.svelte";
    import MultiChooseSetting from "../../routes/clickgui/setting/MultiChooseSetting.svelte";
    import FileSetting from "../../routes/clickgui/setting/FileSetting.svelte";
    import MutableListSetting from "../../routes/clickgui/setting/list/MutableListSetting.svelte";
    import ItemListSetting from "../../routes/clickgui/setting/list/ItemListSetting.svelte";
    import RegistryListSetting from "../../routes/clickgui/setting/list/RegistryListSetting.svelte";
    import CurveSetting from "../../routes/clickgui/setting/CurveSetting.svelte";
    import RegistryMutableListSetting from "../../routes/clickgui/setting/list/RegistryMutableListSetting.svelte";
    import MerchantTradeFiltersSetting from "../../routes/clickgui/setting/merchant/MerchantTradeFiltersSetting.svelte";
    import MerchantReachSetting from "../../routes/clickgui/setting/merchant/MerchantReachSetting.svelte";
    import {shiftDescription} from "../../routes/clickgui/setting/common/shiftDescription";
    import {settingShiftDescription} from "../../routes/clickgui/setting/common/settingDescription";
    export let setting: ModuleSetting;
    export let path: string;
</script>


<div
        use:shiftDescription={{getText: () => settingShiftDescription(setting)}}
        in:slide={{duration: 200, axis: "y"}}
        out:slide={{duration: 200, axis: "y"}}
>
    {#if setting.valueType === "BOOLEAN"}
        <BooleanSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "ACTION"}
        <ActionSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "CHOICE"}
        <ChoiceSetting {path} bind:setting={setting} on:change/>
    {:else if setting.valueType === "FILE"}
        <FileSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "CHOOSE"}
        <ChooseSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "MULTI_CHOOSE"}
        <MultiChooseSetting {path} bind:setting={setting} on:change/>
    {:else if setting.valueType === "TOGGLEABLE"}
        <TogglableSetting {path} bind:setting={setting} on:change/>
    {:else if setting.valueType === "INT"}
        <IntSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "INT_RANGE"}
        <IntRangeSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "FLOAT"}
        <FloatSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "FLOAT_RANGE"}
        <FloatRangeSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "MERCHANT_TRADE_FILTERS"}
        <MerchantTradeFiltersSetting {path} bind:setting on:change/>
    {:else if setting.valueType === "MERCHANT_REACH"}
        <MerchantReachSetting bind:setting on:change/>
    {:else if setting.valueType === "CONFIGURABLE"}
        <ConfigurableSetting {path} bind:setting={setting} on:change/>
    {:else if setting.valueType === "COLOR"}
        <ColorSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "TEXT" || setting.valueType === "PLAYER"}
        <TextSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "MUTABLE_LIST" }
        <MutableListSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "ITEM_LIST" }
        <ItemListSetting {path} bind:setting={setting} on:change/>
    {:else if setting.valueType === "REGISTRY_LIST" }
        <RegistryListSetting {path} bind:setting={setting} on:change/>
    {:else if setting.valueType === "REGISTRY_MUTABLE_LIST" }
        <RegistryMutableListSetting {path} bind:setting={setting} on:change/>
    {:else if setting.valueType === "BIND"}
        <BindSetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "VECTOR3_I" }
        <VectorSetting vecAxes={["x", "y", "z"]} step={1} bind:setting={setting} on:change/>
    {:else if setting.valueType === "VECTOR3_D" }
        <VectorSetting vecAxes={["x", "y", "z"]} step={0.01} bind:setting={setting} on:change/>
    {:else if setting.valueType === "VECTOR2_F" }
        <VectorSetting vecAxes={["x", "y"]} step={0.01} bind:setting={setting} on:change/>
    {:else if setting.valueType === "KEY"}
        <KeySetting bind:setting={setting} on:change/>
    {:else if setting.valueType === "CURVE"}
        <CurveSetting {path} bind:setting={setting} on:change/>
    {:else}
        <div style="color: var(--clickgui-text-color)">Unsupported setting {setting.valueType}</div>
    {/if}
</div>
