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
  .setting-card {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(220px, 0.58fr);
    gap: 18px;
    align-items: center;
    padding: 14px;
    background: var(--baritone-surface-raised, rgba(255, 255, 255, 0.045));
    border: 1px solid var(--baritone-border, rgba(255, 255, 255, 0.1));
    border-radius: 10px;
  }

  .setting-card.locked {
    background: rgba(255, 255, 255, 0.025);
  }

  .setting-title {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 7px;
  }

  .setting-title strong {
    color: var(--baritone-text-primary, #eef1f5);
    font: 650 12px/1.2 "JetBrains Mono", monospace;
  }

  .setting-title span {
    padding: 3px 5px;
    color: var(--baritone-text-muted, #8d96a3);
    background: rgba(255, 255, 255, 0.045);
    border-radius: 4px;
    font: 700 8px/1 "JetBrains Mono", monospace;
  }

  .setting-title .lock {
    color: #ffd69a;
    background: rgba(155, 98, 27, 0.18);
  }

  .setting-copy p {
    margin: 7px 0 4px;
    color: var(--baritone-text-secondary, #aeb5bf);
    font-size: 11px;
    line-height: 1.45;
  }

  .setting-copy small {
    color: var(--baritone-text-muted, #8d96a3);
    font: 9px/1.3 "JetBrains Mono", monospace;
  }

  .setting-control {
    display: grid;
    gap: 8px;
  }

  .setting-actions,
  .boolean-control {
    display: flex;
    justify-content: flex-end;
    gap: 7px;
  }

  button,
  select {
    height: 32px;
    padding: 0 11px;
    color: var(--baritone-text-secondary, #aeb5bf);
    background: rgba(255, 255, 255, 0.045);
    border: 1px solid var(--baritone-border, rgba(255, 255, 255, 0.1));
    border-radius: 7px;
    font-size: 10px;
    font-weight: 650;
  }

  button {
    cursor: pointer;
  }

  button:hover:not(:disabled),
  button.active {
    color: var(--baritone-text-primary, #eef1f5);
    background: color-mix(in srgb, var(--accent-color) 18%, rgba(255, 255, 255, 0.045));
    border-color: color-mix(in srgb, var(--accent-color) 48%, rgba(255, 255, 255, 0.1));
  }

  button.apply {
    color: #0b0d10;
    background: color-mix(in srgb, var(--accent-color) 78%, white);
    border-color: transparent;
  }

  button:focus-visible,
  select:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--accent-color) 72%, white);
    outline-offset: 2px;
  }

  button:disabled,
  select:disabled {
    cursor: not-allowed;
    opacity: 0.45;
  }

  .setting-error {
    margin: 0;
    color: #ffb4b8;
    font-size: 10px;
    text-align: right;
  }

  @media (max-width: 760px) {
    .setting-card {
      grid-template-columns: 1fr;
    }
  }
</style>
