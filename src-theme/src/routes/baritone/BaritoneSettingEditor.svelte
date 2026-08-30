<script lang="ts">
    import {
        coerceBaritoneSettingValue,
        type BaritoneSetting,
        type BaritoneSettingValue,
    } from "../../integration/baritone";
    import BaritoneField from "./BaritoneField.svelte";

    let {
        setting,
        busy = false,
        nativeTextInput = false,
        onSave,
        onReset,
    } = $props<{
        setting: BaritoneSetting;
        busy?: boolean;
        nativeTextInput?: boolean;
        onSave: (setting: BaritoneSetting, value: BaritoneSettingValue) => Promise<void>;
        onReset: (setting: BaritoneSetting) => Promise<void>;
    }>();

    let draft = $state("");
    let error = $state<string | null>(null);
    let saving = $state(false);

    $effect(() => {
        draft = Array.isArray(setting.value) ? setting.value.join(", ") : String(setting.value);
        error = null;
    });

    async function save(): Promise<void> {
        error = null;
        saving = true;
        try {
            await onSave(setting, coerceBaritoneSettingValue(setting, draft));
        } catch (caught) {
            error = describeError(caught, "Unable to save this setting.");
        } finally {
            saving = false;
        }
    }

    async function reset(): Promise<void> {
        error = null;
        saving = true;
        try {
            await onReset(setting);
        } catch (caught) {
            error = describeError(caught, "Unable to reset this setting.");
        } finally {
            saving = false;
        }
    }

    function setBoolean(value: boolean): void {
        draft = String(value);
        void save();
    }

    function describeError(caught: unknown, fallback: string): string {
        if (caught instanceof Error && caught.message.trim()) {
            return `${fallback} ${caught.message}`;
        }
        return fallback;
    }
</script>

<article class="setting-card" class:locked={!setting.mutable}>
    <div class="setting-copy">
        <div class="setting-title">
            <strong>{setting.name}</strong>
            <span>{setting.type}</span>
            {#if !setting.mutable}<span class="lock">Managed</span>{/if}
        </div>
        <p>{setting.description}</p>
        <small>Default: {String(setting.defaultValue)}</small>
    </div>

    <div class="setting-control">
        {#if setting.type === "BOOLEAN"}
            <div class="boolean-control" role="group" aria-label={`${setting.name} value`}>
                <button
                        type="button"
                        class:active={draft === "true"}
                        disabled={!setting.mutable || busy || saving}
                        aria-pressed={draft === "true"}
                        onclick={() => setBoolean(true)}
                >On</button>
                <button
                        type="button"
                        class:active={draft === "false"}
                        disabled={!setting.mutable || busy || saving}
                        aria-pressed={draft === "false"}
                        onclick={() => setBoolean(false)}
                >Off</button>
            </div>
        {:else if setting.type === "ENUM"}
            <select
                    aria-label={`${setting.name} value`}
                    value={draft}
                    disabled={!setting.mutable || busy || saving}
                    onchange={event => {
                        draft = event.currentTarget.value;
                        void save();
                    }}
            >
                {#each setting.options ?? [] as option}
                    <option value={option}>{option}</option>
                {/each}
            </select>
        {:else}
            <BaritoneField
                    id={`baritone-setting-${setting.name}`}
                    label="Canonical value"
                    value={draft}
                    onValueChange={value => draft = value}
                    inputMode={setting.type === "STRING" ? "text" : "decimal"}
                    disabled={!setting.mutable || busy || saving}
                    {nativeTextInput}
            />
        {/if}

        <div class="setting-actions">
            {#if setting.type !== "BOOLEAN" && setting.type !== "ENUM"}
                <button class="apply" type="button" disabled={!setting.mutable || busy || saving} onclick={save}>
                    {saving ? "Saving…" : "Apply"}
                </button>
            {/if}
            <button class="reset" type="button" disabled={!setting.mutable || busy || saving} onclick={reset}>
                Reset
            </button>
        </div>
        {#if error}<p class="setting-error" role="alert">{error}</p>{/if}
    </div>
</article>

<style lang="scss">
  @use "./BaritoneSettingEditor.styles";
</style>
