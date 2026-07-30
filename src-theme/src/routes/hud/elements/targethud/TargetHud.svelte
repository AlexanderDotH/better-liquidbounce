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

    .targethud {
        background-color: var(--targethud-background-color);
        border-radius: 5px;
        overflow: hidden;
    }

    .main-wrapper {
        display: grid;
        grid-template-areas:
            "a b d"
            "a c d";
        column-gap: 10px;
        padding: 10px 15px;
    }

    .name {
        grid-area: b;
        color: var(--targethud-text-color);
        font-weight: 500;
        align-self: flex-end;
    }

    .health-stats {
        grid-area: c;
        display: flex;
        column-gap: 10px;

        .stat {
            .value {
                color: var(--targethud-text-dimmed-color);
                font-size: 14px;
                min-width: 18px;
                display: inline-block;
            }
        }
    }

    .armor-stats {
        grid-area: d;
        display: flex;
        align-items: center;
        column-gap: 10px;
        padding-left: 5px;
    }

    .avatar {
        grid-area: a;
        height: 50px;
        width: 50px;
        position: relative;
        image-rendering: pixelated;
        background-image: url("/img/steve.png");
        background-repeat: no-repeat;
        background-size: cover;
        border-radius: 5px;
        overflow: hidden;

        img {
            position: absolute;
            scale: 6.25;
            left: 118px;
            top: 118px;
        }
    }

    .targethud.targethud--modern[data-presentation="essential"] {
        width: 184px;
        padding: 7px 8px;
        color: var(--modern-hud-text, #eef1f5);
        background: rgba(15, 18, 23, 0.78);
        border: 0;
        border-radius: 10px;
        box-shadow: 0 6px 16px rgba(0, 0, 0, 0.14);
    }

    .targethud.targethud--modern .modern-main {
        display: grid;
        grid-template-columns: 34px minmax(0, 1fr);
        gap: 8px;
        align-items: center;
    }

    .targethud.targethud--modern .avatar.avatar--modern {
        grid-area: auto;
        width: 34px;
        height: 34px;
        border: 0;
        border-radius: 8px;
        box-shadow: none;

        img {
            scale: 4.25;
            left: 70px;
            top: 70px;
        }
    }

    .modern-summary {
        min-width: 0;
    }

    .modern-heading {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 8px;
        margin-bottom: 4px;
    }

    .targethud.targethud--modern .name.name--modern {
        min-width: 0;
        overflow: hidden;
        color: var(--modern-hud-text, #eef1f5);
        font-size: 12px;
        font-weight: 650;
        line-height: 1.2;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .health-value {
        flex: 0 0 auto;
        color: var(--modern-hud-text-muted, #919aa6);
        font-size: 10px;
        font-variant-numeric: tabular-nums;
        font-weight: 650;
    }

    .compact-stats {
        display: flex;
        gap: 4px;
        margin-top: 4px;
    }

    .compact-stat {
        display: inline-flex;
        align-items: center;
        gap: 3px;
        min-height: 14px;
        padding: 1px 5px;
        color: var(--modern-hud-text-muted, #919aa6);
        font-size: 9px;
        font-variant-numeric: tabular-nums;
        font-weight: 600;
        line-height: 1;
        background: rgba(255, 255, 255, 0.055);
        border-radius: 999px;

        img {
            width: 9px;
            height: 9px;
        }
    }

    .compact-stat--absorption {
        color: #f4cf76;
    }

    .compact-stat--armor {
        color: #9db7ef;
    }
</style>
