<script lang="ts">
    import {
        filterBaritoneSettings,
        type BaritoneSetting,
        type BaritoneSettingValue,
    } from "../../integration/baritone";
    import BaritoneField from "./BaritoneField.svelte";
    import BaritoneSettingEditor from "./BaritoneSettingEditor.svelte";
    import type {BaritoneDashboardFields} from "./baritoneDashboardState.ts";

    let {fields, settings, busy, nativeTextInput, onSave, onReset, onResetAll} = $props<{
        fields: BaritoneDashboardFields;
        settings: BaritoneSetting[];
        busy: boolean;
        nativeTextInput: boolean;
        onSave: (setting: BaritoneSetting, value: BaritoneSettingValue) => Promise<void>;
        onReset: (setting: BaritoneSetting) => Promise<void>;
        onResetAll: () => Promise<void>;
    }>();
    let filteredSettings = $derived(filterBaritoneSettings(settings, fields.settingQuery));
</script>

<section class="settings-layout" aria-labelledby="settings-heading">
    <div class="section-heading settings-heading">
        <div><h3 id="settings-heading">Baritone settings</h3><p>Canonical typed values are persisted by LiquidBounce.</p></div>
        <button type="button" disabled={busy} onclick={onResetAll}>Reset all</button>
    </div>
    <BaritoneField
            id="baritone-setting-search"
            label="Search settings"
            value={fields.settingQuery}
            onValueChange={value => fields.settingQuery = value}
            placeholder="Search by name or description"
            {nativeTextInput}
    />
    <p class="result-count" aria-live="polite">{filteredSettings.length} of {settings.length} settings</p>
    <div class="setting-list">
        {#each filteredSettings as setting (setting.name)}
            <BaritoneSettingEditor {setting} {busy} {nativeTextInput} onSave={onSave} onReset={onReset}/>
        {/each}
    </div>
    {#if !filteredSettings.length}<p class="empty-copy">No settings match “{fields.settingQuery}”.</p>{/if}
</section>
