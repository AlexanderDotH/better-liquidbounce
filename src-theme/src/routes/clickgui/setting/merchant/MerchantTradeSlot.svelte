<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import type {MerchantRegistryItem} from "./merchantTradeEditorModel";
    import MerchantItemIcon from "./MerchantItemIcon.svelte";

    export let label: string;
    export let emptyLabel: string;
    export let selected: string[];
    export let itemLookup: ReadonlyMap<string, MerchantRegistryItem>;
    export let invalid = false;

    const dispatch = createEventDispatcher<{open: void}>();

    $: firstIdentifier = selected[0];
    $: firstItem = firstIdentifier ? itemLookup.get(firstIdentifier) : undefined;
    $: itemNames = selected.map(identifier => itemLookup.get(identifier)?.name ?? identifier);
    $: title = selected.length > 0 ? `${label}: ${itemNames.join(", ")}` : `${label}: ${emptyLabel}`;
</script>

<button
    type="button"
    class="trade-slot"
    class:empty={selected.length === 0}
    class:invalid
    {title}
    aria-label={title}
    on:click={() => dispatch("open")}
>
    <span class="slot-content">
        {#if firstIdentifier}
            <MerchantItemIcon identifier={firstIdentifier} icon={firstItem?.icon}/>
        {:else}
            <span class="add-symbol" aria-hidden="true">+</span>
        {/if}

    </span>
    <span class="slot-label">{selected.length === 0 ? emptyLabel : label}</span>
</button>

<style lang="scss">
    .trade-slot {
        position: relative;
        display: grid;
        grid-template-rows: 1fr auto;
        justify-items: center;
        min-width: 0;
        height: 50px;
        padding: 4px 2px 3px;
        overflow: hidden;
        color: var(--clickgui-text-color);
        background: var(--clickgui-input-background-color);
        border: solid var(--clickgui-control-border-width, 1px) color-mix(in srgb, var(--clickgui-text-color) 18%, transparent);
        border-radius: var(--clickgui-merchant-slot-radius, 7px);
        cursor: pointer;
        transition:
            border-color var(--clickgui-control-transition-duration, .2s),
            background-color var(--clickgui-control-transition-duration, .2s);

        &:hover,
        &:focus-visible {
            border-color: var(--clickgui-input-border-color);
            outline: none;
        }

        &.invalid {
            border-color: color-mix(in srgb, var(--error-color) 58%, transparent);
        }
    }

    .slot-content {
        position: relative;
        display: grid;
        place-items: center;
        min-width: 26px;
        min-height: 26px;
    }

    .add-symbol {
        color: var(--clickgui-text-dimmed-color);
        font-size: 18px;
        font-weight: 300;
        line-height: 1;
    }

    .slot-label {
        display: block;
        width: 100%;
        overflow: hidden;
        color: var(--clickgui-text-dimmed-color);
        font-size: 8px;
        line-height: 1.1;
        text-align: center;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .trade-slot.empty .slot-label {
        font-size: 7px;
        letter-spacing: -0.12px;
    }
</style>
