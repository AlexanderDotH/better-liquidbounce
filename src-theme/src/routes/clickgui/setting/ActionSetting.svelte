<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import type {ActionSetting, ModuleSetting} from "../../../integration/types";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import SettingButton from "./common/SettingButton.svelte";

    export let setting: ModuleSetting;

    let actionSetting: ActionSetting;
    $: actionSetting = setting as ActionSetting;
    const dispatch = createEventDispatcher();

    function invoke() {
        actionSetting.value = true;
        setting = {...actionSetting};
        dispatch("change");
    }
</script>

<div class="setting">
    <SettingButton
        value={$spaceSeperatedNames ? convertToSpacedString(actionSetting.name) : actionSetting.name}
        on:click={invoke}
    />
</div>

<style lang="scss">
  .setting {
    padding: var(--clickgui-setting-padding, 7px 0);
  }
</style>
