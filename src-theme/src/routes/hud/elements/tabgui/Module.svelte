<script lang="ts">
    import {afterUpdate} from "svelte";
    import {setModuleEnabled} from "../../../../integration/rest";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../../theme/theme_config";

    export let name: string;
    export let enabled: boolean;
    export let selected: boolean;
    export let variant: "classic" | "modern" = "classic";

    let moduleElement: HTMLElement;

    afterUpdate(() => {
        if (moduleElement && selected) {
            moduleElement.scrollIntoView({
                behavior: variant === "modern" ? "auto" : "smooth",
                block: "nearest",
            });
        }
    });

    async function handleKeyDown(e: KeyboardEvent) {
        if (selected && e.key === "Enter") {
            await setModuleEnabled(name, !enabled);
        }
    }
</script>

<svelte:window on:keydown={handleKeyDown} />

<div
    class="module"
    class:enabled
    class:selected
    class:modern={variant === "modern"}
    bind:this={moduleElement}
>
    {#if variant === "modern"}
        <span class="status-dot" aria-hidden="true"></span>
    {/if}
    <div class="name">{$spaceSeperatedNames ? convertToSpacedString(name) : name}</div>
</div>

<style lang="scss">

    .module {
        font-weight: 500;
        color: var(--tabgui-text-dimmed-color);
        font-size: 12px;
        padding: 6px 15px 6px 10px;
        transition: ease color 0.2s;

        .name {
            transition: ease transform 0.2s;
        }

        &.selected {
            background-color: var(--tabgui-module-selected-background-color);

            .name {
                transform: translateX(5px);
            }
        }

        &.enabled {
            color: var(--tabgui-text-color);
        }
    }

    .module.modern {
        display: flex;
        align-items: center;
        gap: 7px;
        min-height: 26px;
        padding: 0 8px;
        border-radius: 7px;
        transition:
            color var(--modern-hud-motion) var(--modern-hud-easing),
            background-color var(--modern-hud-motion) var(--modern-hud-easing);

        .name {
            transform: none;
        }
    }

    .module.modern.selected {
        color: var(--modern-hud-text);
        background: rgba(70, 119, 255, 0.14);

        .name {
            transform: none;
        }
    }

    .status-dot {
        flex: 0 0 auto;
        width: 5px;
        height: 5px;
        background: rgba(145, 154, 166, 0.42);
        border-radius: 50%;
    }

    .module.modern.enabled .status-dot {
        background: #4677ff;
        box-shadow: 0 0 7px rgba(70, 119, 255, 0.4);
    }
</style>
