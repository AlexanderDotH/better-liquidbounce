<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import MerchantItemDrawer from "./MerchantItemDrawer.svelte";
    import MerchantTradeSlot from "./MerchantTradeSlot.svelte";
    import {
        isMerchantTradeFilterActive,
        type MerchantRegistryItem,
        type MerchantTradeFilter,
        type MerchantTradeFilterSlot,
    } from "./merchantTradeEditorModel";

    export let rule: MerchantTradeFilter;
    export let index: number;
    export let activeSlot: MerchantTradeFilterSlot | undefined;
    export let registryItems: MerchantRegistryItem[];
    export let itemLookup: ReadonlyMap<string, MerchantRegistryItem>;
    export let registryLoading: boolean;
    export let registryError: string | undefined;

    const dispatch = createEventDispatcher<{
        remove: void;
        open: {slot: MerchantTradeFilterSlot};
        toggle: {slot: MerchantTradeFilterSlot; value: string};
        close: void;
    }>();

    function drawerLabel(slot: MerchantTradeFilterSlot) {
        if (slot === "inputA") {
            return "Input A item";
        }

        if (slot === "inputB") {
            return "Input B item";
        }

        return "Output item";
    }
</script>

<article class="trade-card" class:inactive={!isMerchantTradeFilterActive(rule)}>
    <header class="trade-card-header">
        <button
            type="button"
            class="trade-drag-handle"
            title="Reorder trade"
            aria-label={`Reorder trade ${index + 1}`}
        >
            <img src="img/clickgui/icon-drag.svg" alt="" draggable="false"/>
        </button>
        <strong>Trade {index + 1}</strong>
        <span class="trade-status">
            {isMerchantTradeFilterActive(rule) ? "Ready" : "Needs input and output"}
        </span>
        <button
            type="button"
            class="remove-rule"
            title="Remove trade"
            aria-label={`Remove trade ${index + 1}`}
            on:click={() => dispatch("remove")}
        >
            <img src="img/clickgui/icon-cross.svg" alt=""/>
        </button>
    </header>

    <div class="vanilla-trade-row" aria-label={`Trade ${index + 1} filter`}>
        <MerchantTradeSlot
            label="Input A"
            emptyLabel="Choose input"
            selected={rule.inputA}
            {itemLookup}
            invalid={rule.inputA.length === 0}
            on:open={() => dispatch("open", {slot: "inputA"})}
        />
        <span class="operator" aria-hidden="true">+</span>
        <MerchantTradeSlot
            label="Input B"
            emptyLabel="No second cost"
            selected={rule.inputB}
            {itemLookup}
            on:open={() => dispatch("open", {slot: "inputB"})}
        />
        <span class="operator arrow" aria-hidden="true">→</span>
        <MerchantTradeSlot
            label="Output"
            emptyLabel="Choose output"
            selected={rule.outputs}
            {itemLookup}
            invalid={rule.outputs.length === 0}
            on:open={() => dispatch("open", {slot: "outputs"})}
        />
    </div>

    {#if activeSlot}
        {#key activeSlot}
            <MerchantItemDrawer
                label={drawerLabel(activeSlot)}
                items={registryItems}
                selected={rule[activeSlot]}
                loading={registryLoading}
                error={registryError}
                on:toggle={(event) => dispatch("toggle", {slot: activeSlot!, value: event.detail.value})}
                on:close={() => dispatch("close")}
            />
        {/key}
    {/if}
</article>

<style lang="scss">
    .trade-card {
        box-sizing: border-box;
        min-width: 0;
        padding: var(--clickgui-setting-group-padding, 7px);
        background: color-mix(in srgb, var(--clickgui-input-background-color) 82%, transparent);
        border: solid var(--clickgui-control-border-width, 1px) color-mix(in srgb, var(--clickgui-text-color) 16%, transparent);
        border-radius: var(--clickgui-merchant-card-radius, 8px);

        &.inactive {
            border-color: color-mix(in srgb, var(--error-color) 42%, transparent);
        }
    }

    .trade-card-header {
        display: grid;
        grid-template-columns: 18px auto minmax(0, 1fr) 20px;
        align-items: center;
        min-width: 0;
        gap: 5px;
        margin-bottom: var(--clickgui-setting-label-gap, 5px);
        color: var(--clickgui-text-color);
        font-size: 10px;
    }

    .trade-drag-handle,
    .remove-rule {
        display: grid;
        width: 18px;
        height: 18px;
        padding: 2px;
        place-items: center;
        background: transparent;
        border: none;
        border-radius: var(--clickgui-merchant-action-radius, 5px);
        cursor: pointer;

        img {
            display: block;
            max-width: 100%;
            max-height: 100%;
            user-select: none;
            -webkit-user-drag: none;
        }

        &:hover,
        &:focus-visible {
            background: var(--clickgui-selection-chip-background-color);
            outline: none;
        }
    }

    .trade-drag-handle {
        cursor: grab;
    }

    .trade-status {
        overflow: hidden;
        color: var(--clickgui-text-dimmed-color);
        font-size: 8px;
        text-align: right;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .inactive .trade-status {
        color: var(--error-color);
    }

    .vanilla-trade-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) 8px minmax(0, 1fr) 12px minmax(0, 1fr);
        align-items: center;
        min-width: 0;
        gap: 3px;
    }

    .operator {
        color: var(--clickgui-text-dimmed-color);
        font-size: 11px;
        text-align: center;

        &.arrow {
            color: var(--accent-color);
            font-size: 14px;
        }
    }

    @container (max-width: 215px) {
        .trade-card {
            padding: 5px;
        }

        .trade-card-header {
            grid-template-columns: 16px auto minmax(0, 1fr) 18px;
            gap: 3px;
        }

        .trade-status {
            font-size: 7px;
        }

        .vanilla-trade-row {
            grid-template-columns: minmax(0, 1fr) 6px minmax(0, 1fr) 10px minmax(0, 1fr);
            gap: 2px;
        }
    }
</style>
