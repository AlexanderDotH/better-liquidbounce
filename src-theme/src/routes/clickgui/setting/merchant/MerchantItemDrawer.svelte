<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import {cefTextInput} from "../../../../integration/input/cefTextInput";
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
  @use "./MerchantItemDrawer.styles";
</style>
