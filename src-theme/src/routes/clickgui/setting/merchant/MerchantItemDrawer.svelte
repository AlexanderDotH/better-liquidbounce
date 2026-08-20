<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import {cefTextInput} from "../common/cefTextInput";
    import VirtualList from "../list/VirtualList.svelte";
    import MerchantItemIcon from "./MerchantItemIcon.svelte";
    import {
        searchMerchantRegistryItems,
        type MerchantRegistryItem,
    } from "./merchantTradeEditorModel";

    export let label: string;
    export let items: MerchantRegistryItem[];
    export let selected: string[];
    export let loading = false;
    export let error: string | undefined;

    const dispatch = createEventDispatcher<{
        toggle: {value: string};
        close: void;
    }>();

    let searchQuery = "";

    $: itemLookup = new Map(items.map(item => [item.value, item]));
    $: selectedItems = selected.map(identifier => itemLookup.get(identifier) ?? {
        value: identifier,
        name: identifier,
        icon: undefined,
    });
    $: renderedItems = searchMerchantRegistryItems(items, searchQuery);

    function updateSearchQuery(value: string) {
        searchQuery = value;
    }
</script>

<section class="item-drawer" aria-label={`${label} item picker`}>
    <header class="drawer-header">
        <strong>{label}</strong>
        <button type="button" class="close-button" aria-label="Close item picker" on:click={() => dispatch("close")}>×</button>
    </header>

    {#if selectedItems.length > 0}
        <div class="selected-chips" aria-label="Selected items">
            {#each selectedItems as item (item.value)}
                <button
                    type="button"
                    class="selected-chip"
                    title={`Remove ${item.name}`}
                    on:click={() => dispatch("toggle", {value: item.value})}
                >
                    <MerchantItemIcon identifier={item.value} icon={item.icon} size={18}/>
                    <span>{item.name}</span>
                    <span class="chip-remove" aria-hidden="true">×</span>
                </button>
            {/each}
        </div>
    {/if}

    <input
        class="search-input"
        type="text"
        placeholder="Search items"
        aria-label={`Search ${label} items`}
        value={searchQuery}
        autocomplete="off"
        spellcheck="false"
        readonly
        use:cefTextInput={{
            getValue: () => searchQuery,
            onChange: updateSearchQuery,
        }}
    />

    {#if loading}
        <div class="empty-state">Loading items…</div>
    {:else if error}
        <div class="empty-state error">{error}</div>
    {:else if renderedItems.length === 0}
        <div class="empty-state">No matching items</div>
    {:else}
        <div class="result-list">
            <VirtualList items={renderedItems} height="180px" itemHeight={38} let:item>
                <button
                    type="button"
                    class="result-item"
                    class:selected={selected.includes(item.value)}
                    aria-pressed={selected.includes(item.value)}
                    on:click={() => dispatch("toggle", {value: item.value})}
                >
                    <MerchantItemIcon identifier={item.value} icon={item.icon}/>
                    <span class="item-text">
                        <span class="item-name">{item.name}</span>
                        <span class="item-identifier">{item.value}</span>
                    </span>
                    <span class="checkmark" aria-hidden="true">{selected.includes(item.value) ? "✓" : ""}</span>
                </button>
            </VirtualList>
        </div>
    {/if}
</section>

<style lang="scss">
    .item-drawer {
        min-width: 0;
        margin-top: var(--clickgui-setting-control-gap, 5px);
        padding: var(--clickgui-setting-group-padding, 7px);
        background: color-mix(in srgb, var(--clickgui-input-background-color) 78%, transparent);
        border-left: solid var(--clickgui-setting-group-border-width, 2px) var(--clickgui-setting-group-border-color);
        border-radius: var(--clickgui-merchant-drawer-radius, 8px);
    }

    .drawer-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        min-width: 0;
        margin-bottom: var(--clickgui-setting-label-gap, 5px);
        color: var(--clickgui-text-color);
        font-size: var(--clickgui-control-font-size, 12px);
    }

    .close-button {
        display: grid;
        width: 22px;
        height: 22px;
        padding: 0;
        place-items: center;
        color: var(--clickgui-text-dimmed-color);
        background: transparent;
        border: none;
        border-radius: var(--clickgui-merchant-action-radius, 5px);
        cursor: pointer;
        font: inherit;

        &:hover,
        &:focus-visible {
            color: var(--clickgui-text-color);
            background: var(--clickgui-selection-chip-background-color);
            outline: none;
        }
    }

    .selected-chips {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
        max-height: 78px;
        margin-bottom: var(--clickgui-setting-label-gap, 5px);
        overflow-y: auto;
    }

    .selected-chip {
        display: inline-grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        align-items: center;
        max-width: 100%;
        gap: 4px;
        padding: var(--clickgui-chip-padding, 3px 6px);
        color: var(--clickgui-selection-chip-selected-color);
        background: var(--clickgui-selection-chip-selected-background-color);
        border: none;
        border-radius: var(--clickgui-chip-radius, 3px);
        cursor: pointer;
        font-family: "Inter", sans-serif;
        font-size: 9px;

        span:nth-child(2) {
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
    }

    .chip-remove {
        color: var(--clickgui-selection-chip-remove-color);
        font-size: 13px;
        line-height: 1;
    }

    .search-input {
        box-sizing: border-box;
        width: 100%;
        margin-bottom: var(--clickgui-setting-label-gap, 5px);
        padding: var(--clickgui-input-padding, 5px);
        color: var(--clickgui-text-color);
        background: var(--clickgui-input-background-color);
        border: none;
        border-bottom: solid var(--clickgui-control-border-width, 2px) var(--clickgui-input-border-color);
        border-radius: var(--clickgui-merchant-input-radius, 6px);
        font-family: "Inter", sans-serif;
        font-size: var(--clickgui-control-font-size, 12px);
        outline: none;
    }

    .result-list {
        min-width: 0;
        overflow: hidden;
    }

    .result-item {
        box-sizing: border-box;
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) 14px;
        align-items: center;
        width: 100%;
        height: 38px;
        gap: var(--clickgui-setting-control-gap, 5px);
        padding: 3px 5px;
        color: var(--clickgui-text-color);
        background: transparent;
        border: none;
        border-radius: var(--clickgui-merchant-result-radius, 6px);
        cursor: pointer;
        text-align: left;

        &:hover,
        &:focus-visible,
        &.selected {
            background: var(--clickgui-selection-chip-selected-background-color);
            outline: none;
        }
    }

    .item-text {
        display: grid;
        min-width: 0;
    }

    .item-name,
    .item-identifier {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .item-name {
        font-size: var(--clickgui-control-font-size, 12px);
    }

    .item-identifier {
        color: var(--clickgui-text-dimmed-color);
        font-size: 8px;
    }

    .checkmark {
        color: var(--accent-color);
        font-size: 12px;
        font-weight: 700;
        text-align: center;
    }

    .empty-state {
        min-height: 54px;
        padding: 16px 4px;
        color: var(--clickgui-text-dimmed-color);
        font-size: var(--clickgui-control-font-size, 12px);
        text-align: center;

        &.error {
            color: var(--error-color);
        }
    }
</style>
