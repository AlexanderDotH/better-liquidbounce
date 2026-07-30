<script lang="ts">
    import {onMount} from "svelte";
    import type {GroupedModules, Module as TModule,} from "../../../../integration/types";
    import {getModules, setModuleEnabled} from "../../../../integration/rest";
    import {groupByCategory} from "../../../../integration/util";
    import Category from "./Category.svelte";
    import {getTextWidth} from "../../../../integration/text_measurement";
    import {listen} from "../../../../integration/ws";
    import {fly} from "svelte/transition";
    import Module from "./Module.svelte";
    import type {KeyEvent, ModuleToggleEvent} from "../../../../integration/events";
    import {hudMotionDuration, prefersReducedMotion} from "../../motion/hudMotion";

    export let variant: "classic" | "modern" = "classic";

    let modules: TModule[] = [];
    let groupedModules: GroupedModules = {};
    let categories: string[] = [];
    let selectedCategoryIndex = 0;
    let selectedModuleIndex = 0;
    let renderedModules: TModule[] = [];
    let categoriesElement: HTMLElement;
    let motionDuration = 200;
    let motionOffset = -10;

    $: motionDuration = hudMotionDuration(variant, $prefersReducedMotion);
    $: motionOffset = variant === "modern" ? -8 : -10;

    onMount(async () => {
        modules = (await getModules()).filter((m) => m.category !== "Client");
        groupedModules = groupByCategory(modules);
        categories = Object.keys(groupedModules).sort(
            (a, b) =>
                getTextWidth(b, "Inter 14px") - getTextWidth(a, "Inter 14px"),
        );
    });

    function wrapIndex(index: number, itemCount: number): number {
        return (index + itemCount) % itemCount;
    }

    function moveSelection(direction: -1 | 1) {
        const itemCount = renderedModules.length || categories.length;
        if (itemCount === 0) {
            return;
        }

        if (renderedModules.length === 0) {
            selectedCategoryIndex = wrapIndex(selectedCategoryIndex + direction, itemCount);
            return;
        }

        selectedModuleIndex = wrapIndex(selectedModuleIndex + direction, itemCount);
    }

    function openSelectedCategory() {
        if (renderedModules.length > 0) {
            return;
        }

        const selectedCategory = categories[selectedCategoryIndex];
        if (!selectedCategory) {
            return;
        }

        renderedModules = groupedModules[selectedCategory] ?? [];
        selectedModuleIndex = 0;
    }

    async function handleKeyDown(e: KeyEvent) {
        if (e.action !== 1) {
            return;
        }

        switch (e.key) {
            case "key.keyboard.down":
                moveSelection(1);
                break;
            case "key.keyboard.up":
                moveSelection(-1);
                break;
            case "key.keyboard.left":
                renderedModules = [];
                selectedModuleIndex = 0;
                break;
            case "key.keyboard.right":
                openSelectedCategory();
                break;
            case "key.keyboard.enter":
                const selectedModule = renderedModules[selectedModuleIndex];
                if (!selectedModule) {
                    return;
                }
                await setModuleEnabled(selectedModule.name, !selectedModule.enabled);
                break;
        }
    }

    listen("key", handleKeyDown);

    listen("moduleToggle", (e: ModuleToggleEvent) => {
        const moduleName = e.moduleName;
        const moduleEnabled = e.enabled;

        const mod = modules.find((m) => m.name === moduleName);
        if (!mod) return;

        mod.enabled = moduleEnabled;
        groupedModules = groupByCategory(modules);
        if (renderedModules.length > 0) {
            const selectedCategory = categories[selectedCategoryIndex];
            renderedModules = selectedCategory ? groupedModules[selectedCategory] ?? [] : [];
            selectedModuleIndex = Math.min(
                selectedModuleIndex,
                Math.max(0, renderedModules.length - 1),
            );
        }
    });
</script>

<div class="tabgui" class:modern={variant === "modern"}>
    <div class="categories" bind:this={categoriesElement}>
        {#each categories as name, index}
            <Category {name} selected={index === selectedCategoryIndex} {variant} />
        {/each}
    </div>

    {#if renderedModules.length > 0}
        <div
            class="modules"
            transition:fly={{ x: motionOffset, duration: motionDuration }}
            style={variant === "modern"
                ? `max-height: ${categoriesElement.offsetHeight}px`
                : `height: ${categoriesElement.offsetHeight}px`}
        >
            {#each renderedModules as { name, enabled }, index}
                <Module {name} {enabled} selected={selectedModuleIndex === index} {variant} />
            {/each}
        </div>
    {/if}
</div>

<style lang="scss">

    .tabgui {
        display: flex;
    }

    .tabgui.modern {
        align-items: flex-start;
        gap: 8px;
    }

    .categories {
        background-clip: content-box;
        display: flex;
        flex-direction: column;
        border-radius: 5px;
        overflow: hidden;
    }

    .tabgui.modern .categories {
        gap: 3px;
        min-width: 116px;
        padding: 5px;
        background: var(--modern-hud-surface);
        background-clip: border-box;
        border-radius: 13px;
        box-shadow: var(--modern-hud-shadow);
    }

    .modules {
      background-clip: content-box;
      background-color: var(--tabgui-modules-background-color);
      margin-left: 6px;
      border-radius: 5px;
      min-width: 100px;
      display: flex;
      flex-direction: column;
      overflow: auto;

      &::-webkit-scrollbar {
        width: 0;
      }
    }

    .tabgui.modern .modules {
      gap: 2px;
      min-width: 126px;
      height: auto;
      padding: 5px;
      margin-left: 0;
      background: var(--modern-hud-surface);
      background-clip: border-box;
      border-radius: 13px;
      box-shadow: var(--modern-hud-shadow);
    }
</style>
