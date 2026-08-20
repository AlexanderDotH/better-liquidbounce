<script lang="ts">
    import {createEventDispatcher, onMount} from "svelte";
    import {SortableList} from "@jhubbardsf/svelte-sortablejs";
    import type {
        MerchantTradeFiltersSetting as MerchantTradeFiltersSettingType,
        ModuleSetting,
    } from "../../../../integration/types";
    import {getRegistryItems} from "../../../../integration/rest";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../../theme/theme_config";
    import SettingButton from "../common/SettingButton.svelte";
    import MerchantTradeCard from "./MerchantTradeCard.svelte";
    import {
        addMerchantTradeFilter,
        moveMerchantTradeFilter,
        normalizeMerchantTradeFilters,
        removeMerchantTradeFilter,
        toggleMerchantTradeFilterItem,
        type MerchantRegistryItem,
        type MerchantTradeFilter,
        type MerchantTradeFilterSlot,
    } from "./merchantTradeEditorModel";

    export let setting: ModuleSetting;
    export let path: string;

    type ActiveDrawer = {
        ruleIndex: number;
        slot: MerchantTradeFilterSlot;
    };

    type SortEvent = {
        oldIndex?: number | null;
        newIndex?: number | null;
        oldDraggableIndex?: number | null;
        newDraggableIndex?: number | null;
    };

    const dispatch = createEventDispatcher<{change: void}>();

    let registryItems: MerchantRegistryItem[] = [];
    let registryLoading = true;
    let registryError: string | undefined;
    let activeDrawer: ActiveDrawer | undefined;
    let sortableRenderKey = 0;

    $: cSetting = setting as MerchantTradeFiltersSettingType;
    $: rules = normalizeMerchantTradeFilters(cSetting.value);
    $: itemLookup = new Map(registryItems.map(item => [item.value, item]));
    $: settingLabel = $spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name;

    onMount(async () => {
        try {
            const registry = await getRegistryItems(cSetting.registry ?? "item");
            registryItems = Object.entries(registry)
                .map(([value, item]) => ({value, name: item.name, icon: item.icon}))
                .sort((left, right) => left.name.localeCompare(right.name));
        } catch {
            registryError = "Unable to load the item registry";
        } finally {
            registryLoading = false;
        }
    });

    function commitRules(nextRules: readonly MerchantTradeFilter[]) {
        setting = {
            ...cSetting,
            value: normalizeMerchantTradeFilters(nextRules),
        };
        dispatch("change");
    }

    function addRule() {
        const newRuleIndex = rules.length;
        commitRules(addMerchantTradeFilter(rules));
        activeDrawer = {ruleIndex: newRuleIndex, slot: "inputA"};
    }

    function removeRule(index: number) {
        activeDrawer = undefined;
        commitRules(removeMerchantTradeFilter(rules, index));
    }

    function openDrawer(ruleIndex: number, slot: MerchantTradeFilterSlot) {
        const nextDrawer = {ruleIndex, slot};

        if (activeDrawer?.ruleIndex === ruleIndex && activeDrawer.slot === slot) {
            activeDrawer = undefined;
            return;
        }

        activeDrawer = nextDrawer;
    }

    function toggleItem(ruleIndex: number, slot: MerchantTradeFilterSlot, itemIdentifier: string) {
        commitRules(toggleMerchantTradeFilterItem(rules, ruleIndex, slot, itemIdentifier));
    }

    function handleSort(event: SortEvent) {
        const oldIndex = event.oldDraggableIndex ?? event.oldIndex;
        const newIndex = event.newDraggableIndex ?? event.newIndex;

        if (oldIndex == null || newIndex == null || oldIndex === newIndex) {
            return;
        }

        activeDrawer = undefined;
        commitRules(moveMerchantTradeFilter(rules, oldIndex, newIndex));
        sortableRenderKey++;
    }

</script>

<div class="merchant-trade-setting" data-setting-path={`${path}.${cSetting.name}`}>
    <div class="setting-header">
        <span class="setting-name">{settingLabel}</span>
        <span class="rule-count">{rules.length}</span>
    </div>

    {#if rules.length === 0}
        <div class="no-rules">Add a trade to choose accepted villager offers.</div>
    {:else}
        <div class="trade-rules">
            {#key sortableRenderKey}
                <SortableList
                    class="trade-sortable-list"
                    handle=".trade-drag-handle"
                    forceFallback={true}
                    fallbackOnBody={true}
                    animation={150}
                    onEnd={handleSort}
                >
                    {#each rules as rule, index}
                        <MerchantTradeCard
                            {rule}
                            {index}
                            activeSlot={activeDrawer?.ruleIndex === index ? activeDrawer.slot : undefined}
                            {registryItems}
                            {itemLookup}
                            {registryLoading}
                            {registryError}
                            on:remove={() => removeRule(index)}
                            on:open={(event) => openDrawer(index, event.detail.slot)}
                            on:toggle={(event) => toggleItem(index, event.detail.slot, event.detail.value)}
                            on:close={() => activeDrawer = undefined}
                        />
                    {/each}
                </SortableList>
            {/key}
        </div>
    {/if}

    <SettingButton value="Add trade" on:click={addRule}/>
</div>

<style lang="scss">
    .merchant-trade-setting {
        box-sizing: border-box;
        min-width: 0;
        padding: var(--clickgui-setting-padding, 7px 0);
        container-type: inline-size;
    }

    .setting-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--clickgui-setting-control-gap, 5px);
        margin-bottom: var(--clickgui-setting-label-gap, 5px);
    }

    .setting-name {
        overflow: hidden;
        color: var(--clickgui-text-color);
        font-size: var(--clickgui-control-font-size, 12px);
        font-weight: 600;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .rule-count {
        min-width: 18px;
        padding: 1px 5px;
        color: var(--clickgui-text-dimmed-color);
        background: var(--clickgui-selection-chip-background-color);
        border-radius: 999px;
        font-size: 9px;
        text-align: center;
    }

    .no-rules {
        margin-bottom: var(--clickgui-setting-expanded-gap, 10px);
        padding: 8px;
        color: var(--clickgui-text-dimmed-color);
        background: var(--clickgui-input-background-color);
        border-radius: var(--clickgui-control-radius, 3px);
        font-size: var(--clickgui-control-font-size, 12px);
        line-height: 1.35;
        text-align: center;
    }

    .trade-rules {
        min-width: 0;
        max-height: 520px;
        margin-bottom: var(--clickgui-setting-expanded-gap, 10px);
        overflow-x: hidden;
        overflow-y: auto;
    }

    :global(.trade-sortable-list) {
        display: grid;
        min-width: 0;
        gap: var(--clickgui-setting-expanded-gap, 10px);
    }

</style>
