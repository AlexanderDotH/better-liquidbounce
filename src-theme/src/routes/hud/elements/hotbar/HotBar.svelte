<script lang="ts">
    import Status from "./Status.svelte";
    import ModernContextualBar from "./ModernContextualBar.svelte";
    import {listen} from "../../../../integration/ws";
    import type {
        ContextualBarData,
        PlayerData,
        TextComponent as TTExtComponent,
    } from "../../../../integration/types";
    import {onMount} from "svelte";
    import {getContextualBar, getPlayerData} from "../../../../integration/rest";
    import {fade} from "svelte/transition";
    import TextComponent from "../../../../components/text/TextComponent.svelte";
    import type {
        ClientPlayerDataEvent,
        ContextualBarEvent,
        OverlayMessageEvent,
    } from "../../../../integration/events";
    import {EMPTY_CONTEXTUAL_BAR} from "./contextualBarModel";

    export let presentation: "classic" | "modern" = "classic";

    let lastSlot = 0;
    let currentSlot = 0;
    let playerData: PlayerData | null = null;
    let contextualBar: ContextualBarData = EMPTY_CONTEXTUAL_BAR;
    let maxAbsorption = 0;
    let slotsElement: HTMLElement | undefined;

    let showItemStackName = false;
    let showItemStackNameTimeout: number | null = null;
    let itemStackName: TTExtComponent | string | null = null;
    let overlayMessage: OverlayMessageEvent | null = null;
    let overlayMessageTimeout: number | null = null;

    function updatePlayerData(s: PlayerData) {
        playerData = s;
        if (playerData.absorption <= 0) {
            maxAbsorption = 0;
        }
        if (playerData.absorption > maxAbsorption) {
            maxAbsorption = playerData.absorption;
        }
        currentSlot = Math.min(8, Math.max(0, playerData.selectedSlot));
        if (currentSlot !== lastSlot) {
            lastSlot = currentSlot;
            if (playerData.mainHandStack.identifier !== "minecraft:air") {
                itemStackName = playerData.mainHandStack.displayName;
                if (showItemStackNameTimeout !== null) {
                    clearTimeout(showItemStackNameTimeout);
                }
                showItemStackName = true;
                showItemStackNameTimeout = setTimeout(() => {
                    showItemStackName = false;
                }, 2000);
            }
        }
    }

    listen("clientPlayerData", (event: ClientPlayerDataEvent) => {
        updatePlayerData(event.playerData);
    });

    listen("contextualBar", (event: ContextualBarEvent) => {
        contextualBar = event.contextualBar;
    });

    listen("overlayMessage", (event: OverlayMessageEvent) => {
        overlayMessage = event;
        if (overlayMessageTimeout !== null) {
            clearTimeout(overlayMessageTimeout);
        }
        overlayMessageTimeout = setTimeout(() => {
            overlayMessage = null;
        }, 3000)
    });

    listen("disconnect", () => {
        playerData = null;
        contextualBar = EMPTY_CONTEXTUAL_BAR;
    });

    listen("socketReady", () => {
        void refreshSnapshot();
    });

    async function refreshSnapshot() {
        try {
            const [nextPlayerData, nextContextualBar] = await Promise.all([
                getPlayerData(),
                getContextualBar(),
            ]);
            updatePlayerData(nextPlayerData);
            contextualBar = nextContextualBar;
        } catch {
            playerData = null;
            contextualBar = EMPTY_CONTEXTUAL_BAR;
        }
    }

    onMount(refreshSnapshot);
</script>

{#if playerData}
    <div class="hotbar" class:hotbar--spectator={playerData.gameMode === "spectator"}>
        {#if playerData.gameMode !== "spectator"}
            {#if overlayMessage !== null}
                <div class="overlay-message" out:fade={{duration: 200}}
                     style="max-width: {slotsElement?.offsetWidth ?? 0}px">
                    <TextComponent fontSize={14} textComponent={overlayMessage.text} allowPreformatting={true} />
                </div>
            {/if}
            {#if showItemStackName && itemStackName !== null}
                <div class="item-name" out:fade={{duration: 200}}>
                    <TextComponent fontSize={14} textComponent={itemStackName}/>
                </div>
            {/if}
            <div class="status">

                <div class="pair">
                    {#if playerData.armor > 0}
                        <Status
                                max={20}
                                value={playerData.armor}
                                color="var(--hotbar-armor-color)"
                                alignRight={false}
                                icon="shield"
                        />
                    {:else}
                        <div></div>
                    {/if}

                    {#if playerData.air < playerData.maxAir}
                        <Status
                                max={playerData.maxAir}
                                value={playerData.air}
                                color="var(--hotbar-air-color)"
                                alignRight={true}
                        />
                    {:else}
                        <div></div>
                    {/if}
                </div>

                {#if playerData.gameMode !== "creative"}
                    {#if playerData.absorption > 0}
                        <div class="pair">
                            <Status
                                    max={maxAbsorption}
                                    value={playerData.absorption}
                                    color="var(--hotbar-absorption-color)"
                                    alignRight={false}
                            />

                            <div></div>
                        </div>
                    {/if}
                    <div class="pair">
                        <Status
                                max={playerData.maxHealth}
                                value={playerData.health}
                                color="var(--hotbar-health-color)"
                                alignRight={false}
                                icon="heart"
                        />
                        <Status
                                max={20}
                                value={playerData.food}
                                color="var(--hotbar-hunger-color)"
                                alignRight={true}
                                icon="food"
                        />
                    </div>
                {/if}
                {#if presentation === "classic" && playerData.experienceLevel > 0}
                    <Status
                            max={100} value={playerData.experienceProgress * 100}
                            color="var(--hotbar-experience-color)"
                            alignRight={false}
                            label={playerData.experienceLevel.toString()}
                    />
                {/if}
            </div>
        {/if}

        {#if presentation === "modern" && contextualBar.mode !== "empty"}
            <ModernContextualBar data={contextualBar}/>
        {/if}

        {#if playerData.gameMode !== "spectator"}
            <div class="hotbar-elements">
                <div class="slider" style="transform: translateX({currentSlot * 45}px)"></div>
                <div class="slots" bind:this={slotsElement}>
                    <div class="slot"></div>
                    <div class="slot"></div>
                    <div class="slot"></div>
                    <div class="slot"></div>
                    <div class="slot"></div>
                    <div class="slot"></div>
                    <div class="slot"></div>
                    <div class="slot"></div>
                    <div class="slot"></div>
                </div>
            </div>

            {#if playerData?.offHandStack.identifier !== "minecraft:air"}
                <div class="offhand-slot"></div>
            {/if}
        {/if}
    </div>
{/if}

<style lang="scss">
  @use "./HotBar.styles";
</style>
