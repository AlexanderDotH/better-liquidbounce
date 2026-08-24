<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import {cefTextInput} from "./cefTextInput";

    export let value: number;
    export let valueType: "int" | "float";

    let inputElement: HTMLInputElement;
    let inputValue = "";

    $: {
        if (document.activeElement !== inputElement) {
            inputValue = value.toString();
        }
    }

    const dispatch = createEventDispatcher<{
        change: { value: number }
    }>();

    function handleInput(value: string) {
        inputValue = value;
        let parsed: number;
        if (valueType === "float") {
            parsed = parseFloat(inputValue);
        } else {
            parsed = parseInt(inputValue);
        }

        if (!isNaN(parsed)) {
            dispatch("change", {value: parsed});
        }
    }

    function handleKeyDown(e: KeyboardEvent) {
        if (e.key === "Enter") {
            e.preventDefault();
        }
    }
</script>

<input
        type="text"
        inputmode="decimal"
        class="value"
        readonly
        value={inputValue}
        size={Math.max(inputValue.length, 1)}
        bind:this={inputElement}
        use:cefTextInput={{
            getValue: () => inputValue,
            onChange: handleInput,
        }}
        on:keydown={handleKeyDown}
/>

<style lang="scss">

  .value {
    font-family: monospace;
    color: var(--clickgui-text-color);
    font-weight: 500;
    font-size: var(--clickgui-control-font-size, 12px);
    background-color: transparent;
    border: none;
    padding: 0;
    min-width: var(--clickgui-value-input-min-width, 5px);
    display: inline-block;
  }
</style>
