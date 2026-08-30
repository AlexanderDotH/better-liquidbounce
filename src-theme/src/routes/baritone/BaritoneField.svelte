<script lang="ts">
    import {cefTextInput} from "../../integration/input/cefTextInput";

    let {
        id,
        label,
        value,
        onValueChange,
        placeholder = "",
        help = "",
        disabled = false,
        nativeTextInput = false,
        inputMode = "text",
        list,
    } = $props<{
        id: string;
        label: string;
        value: string;
        onValueChange: (value: string) => void;
        placeholder?: string;
        help?: string;
        disabled?: boolean;
        nativeTextInput?: boolean;
        inputMode?: "text" | "numeric" | "decimal";
        list?: string;
    }>();

    function handleNativeInput(event: Event): void {
        onValueChange((event.currentTarget as HTMLInputElement).value);
    }
</script>

<label class="baritone-field" for={id}>
    <span class="field-label">{label}</span>
    <input
            {id}
            {placeholder}
            {disabled}
            {list}
            value={value}
            inputmode={inputMode}
            autocomplete="off"
            spellcheck="false"
            readonly={!nativeTextInput}
            use:cefTextInput={{
                getValue: () => value,
                onChange: onValueChange,
                screenNames: ["baritone"],
            }}
            oninput={handleNativeInput}
    />
    {#if help}
        <span class="field-help">{help}</span>
    {/if}
</label>

<style lang="scss">
  .baritone-field {
    display: grid;
    gap: 6px;
    min-width: 0;
  }

  .field-label {
    color: var(--baritone-text-secondary, #aeb5bf);
    font-size: 11px;
    font-weight: 650;
    letter-spacing: 0.03em;
  }

  input {
    width: 100%;
    min-width: 0;
    height: 36px;
    box-sizing: border-box;
    padding: 0 11px;
    color: var(--baritone-text-primary, #eef1f5);
    background: var(--baritone-surface-raised, rgba(255, 255, 255, 0.055));
    border: 1px solid var(--baritone-border, rgba(255, 255, 255, 0.1));
    border-radius: 8px;
    font: 500 12px/1.2 "JetBrains Mono", "Roboto Mono", monospace;
    outline: none;
    transition:
      border-color var(--baritone-motion-duration, 140ms) ease,
      background-color var(--baritone-motion-duration, 140ms) ease;
  }

  input:hover:not(:disabled) {
    background: var(--baritone-surface-raised-hover, rgba(255, 255, 255, 0.08));
  }

  input:focus-visible {
    border-color: color-mix(in srgb, var(--accent-color) 78%, white);
    outline: 2px solid color-mix(in srgb, var(--accent-color) 24%, transparent);
    outline-offset: 1px;
  }

  input:disabled {
    cursor: not-allowed;
    opacity: 0.48;
  }

  .field-help {
    color: var(--baritone-text-muted, #8d96a3);
    font-size: 10px;
    line-height: 1.4;
  }

  @media (prefers-reduced-motion: reduce) {
    input {
      transition: none;
    }
  }
</style>
