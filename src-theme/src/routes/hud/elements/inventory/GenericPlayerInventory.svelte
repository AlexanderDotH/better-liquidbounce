<script lang="ts">
    import type {ItemStack} from "../../../../integration/types";
    import {listen} from "../../../../integration/ws";
    import type {ClientPlayerInventoryEvent, PlayerInventory} from "../../../../integration/events";
    import ItemStackView from "./ItemStackView.svelte";
    import {onMount} from "svelte";
    import {getPlayerInventory} from "../../../../integration/rest";

    export let rowLength: number;
    export let backgroundColor: string = "var(--inventory-background-color)";
    export let gap: string = "0.5rem";
    export let getRenderedStacks: (inventory: PlayerInventory) => ItemStack[];
    export let variant: "classic" | "modern" = "classic";
    export let label: string | undefined = undefined;

    let inventory: PlayerInventory | undefined;
    let stacks: ItemStack[] = [];

    $: panelBackgroundColor = variant === "modern" ? "rgba(15, 18, 23, 0.76)" : backgroundColor;
    $: panelGap = variant === "modern" ? "2px" : gap;

    listen("clientPlayerInventory", (data: ClientPlayerInventoryEvent) => {
        inventory = data.inventory;
    });

    onMount(async () => {
        inventory = await getPlayerInventory();
    });

    $: stacks = inventory ? getRenderedStacks(inventory) : [];
</script>

<div
    class="inventory"
    class:inventory--modern={variant === "modern"}
    style="
    background-color: {panelBackgroundColor};
    gap: {panelGap};
    --row-length: {rowLength};
">
    {#if variant === "modern" && label}
        <div class="inventory-label">{label}</div>
    {/if}

    {#each stacks as stack (stack)}
        <ItemStackView {stack} {variant}/>
    {/each}
</div>

<style lang="scss">
  .inventory {
    padding: 4px;
    border-radius: 5px;
    display: grid;
    grid-template-columns: repeat(var(--row-length), 1fr);
  }

  .inventory--modern {
    gap: 2px;
    padding: 6px;
    background: rgba(15, 18, 23, 0.76);
    border: 0;
    border-radius: 10px;
    box-shadow: 0 6px 18px rgba(0, 0, 0, 0.16);
  }

  .inventory-label {
    grid-column: 1 / -1;
    margin: 0 2px 2px;
    color: rgba(226, 232, 240, 0.58);
    font-size: 9px;
    font-weight: 650;
    line-height: 12px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
</style>
