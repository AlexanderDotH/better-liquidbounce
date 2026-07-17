<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import type {ModuleSetting, TextSetting,} from "../../../integration/types";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import {cefTextInput} from "./common/cefTextInput";
    import {listen} from "../../../integration/ws";

    export let setting: ModuleSetting;

    const cSetting = setting as TextSetting;

    const dispatch = createEventDispatcher();

    listen("valueChanged", (event) => {
        if (event.value.name !== cSetting.name) {
            return;
        }

        cSetting.value = event.value.value as string;
        setting = {...cSetting};
    });

    function handleChange(value: string) {
        cSetting.value = value;
        setting = {...cSetting};
        dispatch("change");
    }
</script>

<div class="setting">
    <div class="name">{$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}</div>
    <input
            type="text"
            class="value"
            spellcheck="false"
            readonly
            placeholder={$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}
            value={cSetting.value ?? ""}
            use:cefTextInput={{
                getValue: () => cSetting.value ?? "",
                onChange: handleChange,
            }}
    >
</div>

<style lang="scss">

  .setting {
    padding: 7px 0px;
  }

  .name {
    font-weight: 500;
    color: var(--clickgui-text-color);
    font-size: 12px;
    margin-bottom: 5px;
  }

  .value {
    width: 100%;
    background-color: var(--clickgui-input-background-color);
    font-family: monospace;
    font-size: 12px;
    color: var(--clickgui-text-color);
    border: none;
    border-bottom: solid 2px var(--clickgui-input-border-color);
    padding: 5px;
    border-radius: 3px;
    transition: ease border-color .2s;
    cursor: text;

    &::-webkit-scrollbar {
      background-color: transparent;
    }
  }
</style>
