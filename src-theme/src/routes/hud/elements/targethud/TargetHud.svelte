<script lang="ts">
    import ArmorStatus from "./ArmorStatus.svelte";
    import {listen} from "../../../../integration/ws.js";
    import type {PlayerData} from "../../../../integration/types";
    import {REST_BASE} from "../../../../integration/host";
    import {fly} from "svelte/transition";
    import HealthProgress from "./HealthProgress.svelte";
    import type {TargetChangeEvent} from "../../../../integration/events";
    import {onDestroy} from "svelte";
    import {hudMotionDuration, prefersReducedMotion} from "../../motion/hudMotion";

    export let presentation: "classic" | "modern" = "classic";

    let target: PlayerData | null = null;
    let visible = true;
    let transitionDuration = 200;
    let motionOffset = -10;

    let hideTimeout: number;

    $: transitionDuration = hudMotionDuration(presentation, $prefersReducedMotion, 180);
    $: motionOffset = presentation === "modern" ? -6 : -10;

    function startHideTimeout() {
        hideTimeout = setTimeout(() => {
            visible = false;
        }, 1000);
    }

    listen("targetChange", (data: TargetChangeEvent) => {
        target = data.target;
        visible = true;
        clearTimeout(hideTimeout);
        startHideTimeout();
    });

    startHideTimeout();

    onDestroy(() => {
        clearTimeout(hideTimeout);
    });
</script>

{#if visible && target != null}
    <div
            class="targethud"
            class:targethud--modern={presentation === "modern"}
            data-presentation={presentation === "modern" ? "essential" : undefined}
            transition:fly={{ y: motionOffset, duration: transitionDuration }}
    >
        {#if presentation === "modern"}
            <div class="modern-main">
                <div class="avatar avatar--modern">
                    <img
                            src="{REST_BASE}/api/v1/client/resource/skin?uuid={target.uuid}"
                            alt="{target.username}"
                    />
                </div>

                <div class="modern-summary">
                    <div class="modern-heading">
                        <div class="name name--modern">{target.username}</div>
                        <div class="health-value" aria-label="Health">
                            {Math.floor(target.actualHealth)}
                        </div>
                    </div>

                    <HealthProgress
                            maxHealth={target.maxHealth}
                            health={target.actualHealth}
                            compact={true}
                    />

                    {#if target.absorption > 0 || target.armor > 0}
                        <div class="compact-stats">
                            {#if target.absorption > 0}
                                <div class="compact-stat compact-stat--absorption">
                                    <img
                                            src="img/hud/targethud/icon-absorption.svg"
                                            alt=""
                                            aria-hidden="true"
                                    />
                                    <span>{Math.floor(target.absorption)}</span>
                                </div>
                            {/if}

                            {#if target.armor > 0}
                                <div class="compact-stat compact-stat--armor">
                                    <img
                                            src="img/hud/targethud/icon-armor.svg"
                                            alt=""
                                            aria-hidden="true"
                                    />
                                    <span>{Math.floor(target.armor)}</span>
                                </div>
                            {/if}
                        </div>
                    {/if}
                </div>
            </div>
        {:else}
            <div class="main-wrapper">
                <div class="avatar">
                    <img src="{REST_BASE}/api/v1/client/resource/skin?uuid={target.uuid}" alt="avatar" />
                </div>

                <div class="name">{target.username}</div>
                <div class="health-stats">
                    <div class="stat">
                        <div class="value">{Math.floor(target.actualHealth)}</div>
                        <img
                                class="icon"
                                src="img/hud/targethud/icon-health.svg"
                                alt="health"
                        />
                    </div>
                    {#if target.absorption > 0}
                        <div class="stat">
                            <div class="value">{Math.floor(target.absorption)}</div>
                            <img
                                    class="icon"
                                    src="img/hud/targethud/icon-absorption.svg"
                                    alt="absorption"
                            />
                        </div>
                    {/if}
                    <div class="stat">
                        <div class="value">{Math.floor(target.armor)}</div>
                        <img
                                class="icon"
                                src="img/hud/targethud/icon-armor.svg"
                                alt="armor"
                        />
                    </div>
                </div>
                <div class="armor-stats">
                    {#if target.armorItems[3].count > 0}
                        <ArmorStatus itemStack={target.armorItems[3]} />
                    {/if}
                    {#if target.armorItems[2].count > 0}
                        <ArmorStatus itemStack={target.armorItems[2]} />
                    {/if}
                    {#if target.armorItems[1].count > 0}
                        <ArmorStatus itemStack={target.armorItems[1]} />
                    {/if}
                    {#if target.armorItems[0].count > 0}
                        <ArmorStatus itemStack={target.armorItems[0]} />
                    {/if}
                </div>
            </div>

            <HealthProgress maxHealth={target.maxHealth + target.absorption} health={target.actualHealth + target.absorption} />
        {/if}
    </div>
{/if}

<style lang="scss">
  @use "./TargetHud.styles";
</style>
