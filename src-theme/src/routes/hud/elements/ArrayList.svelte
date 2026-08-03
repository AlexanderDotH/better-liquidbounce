<script lang="ts">
    import {onMount, tick} from "svelte";
    import type {Module} from "../../../integration/types";
    import {getModules} from "../../../integration/rest";
    import {listen} from "../../../integration/ws";
    import {getTextWidth} from "../../../integration/text_measurement";
    import {flip} from "svelte/animate";
    import {fly} from "svelte/transition";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import {hudMotionDuration, prefersReducedMotion} from "../motion/hudMotion";

    export let settings: { [name: string]: any };
    export let variant: "classic" | "modern" = "classic";

    let cSettings = settings as HudArrayListSettings;
    const CLASSIC_FONT = "500 14px Inter";
    const MODERN_NAME_FONT = "550 12px Inter";
    const MODERN_TAG_FONT = "600 10px Inter";
    const MODERN_TAG_GAP_PX = 6;
    const MODERN_TAG_HORIZONTAL_PADDING_PX = 12;

    let enabledModules: Module[] = [];
    let motionDuration = 200;
    let motionOffset = 50;
    let previousVariant = variant;

    $: motionDuration = hudMotionDuration(variant, $prefersReducedMotion);
    $: motionOffset = variant === "modern" ? 18 : 50;

    function measureModuleWidth(module: Module, formattedName: string): number {
        const visibleTag = cSettings.showTags ? module.tag : null;

        if (variant !== "modern") {
            const fullName = visibleTag
                ? `${formattedName} ${visibleTag}`
                : formattedName;
            return getTextWidth(fullName, CLASSIC_FONT);
        }

        const nameWidth = getTextWidth(formattedName, MODERN_NAME_FONT);
        if (!visibleTag) {
            return nameWidth;
        }

        return nameWidth
            + MODERN_TAG_GAP_PX + MODERN_TAG_HORIZONTAL_PADDING_PX
            + getTextWidth(visibleTag, MODERN_TAG_FONT);
    }

    async function updateEnabledModules() {
        const modules = await getModules();
        const visibleModules = modules.filter(m => m.enabled && !m.hidden);

        const modulesWithWidths = visibleModules.map(module => {
            const formattedName = $spaceSeperatedNames ? convertToSpacedString(module.name) : module.name;

            return {
                ...module,
                width: measureModuleWidth(module, formattedName)
            };
        });

        modulesWithWidths.sort((a, b) => cSettings.order === "Ascending" ? a.width - b.width : b.width - a.width);

        enabledModules = modulesWithWidths;
        await tick();
    }

    $: if (cSettings !== settings) {
        cSettings = settings as HudArrayListSettings;
        void updateEnabledModules();
    }

    onMount(() => {
        const unsubscribe = spaceSeperatedNames.subscribe(() => {
            void updateEnabledModules();
        });

        return unsubscribe;
    });

    listen("moduleToggle", async () => {
        await updateEnabledModules();
    });

    listen("refreshArrayList", async () => {
        await updateEnabledModules();
    });

    $: if (variant !== previousVariant) {
        previousVariant = variant;
        void updateEnabledModules();
    }
</script>

<div class="arraylist">
    {#each enabledModules as {name, tag} (name)}
        <div
                class="module"
                style={cSettings.itemAlignment === "Left" ? "margin-right: auto;" : "margin-left: auto;"}
                animate:flip={{ duration: motionDuration }}
                transition:fly={{ x: motionOffset, duration: motionDuration }}
        >
            <span class="module-name">
                {$spaceSeperatedNames ? convertToSpacedString(name) : name}
            </span>
            {#if tag && cSettings.showTags}
                <span class="tag">{tag}</span>
            {/if}
        </div>
    {/each}
</div>

<style lang="scss">

  .module {
    background-color: var(--arraylist-background-color);
    color: var(--arraylist-text-color);
    font-size: 14px;
    border-radius: 4px 0 0 4px;
    padding: 5px 8px;
    border-left: solid 4px var(--arraylist-border-color);
    width: max-content;
    font-weight: 500;
  }

  .tag {
    color: var(--arraylist-tag-color);
  }
</style>
