<script lang="ts">
    import {onMount} from "svelte";
    import {fly} from "svelte/transition";
    import type {Module} from "../../../integration/types";
    import type {ModuleToggleEvent} from "../../../integration/events";
    import {getModules} from "../../../integration/rest";
    import {listen} from "../../../integration/ws";
    import {getTextWidth} from "../../../integration/text_measurement";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import {hudMotionDuration, prefersReducedMotion} from "../motion/hudMotion";
    import {
        areArrayListEntriesRenderEquivalent,
        buildArrayListEntries,
        getArrayListMotionOffset,
        LatestArrayListModuleLoader,
        type ArrayListEntry,
        type ArrayListVariant,
    } from "./arrayListModel";

    export let settings: { [name: string]: any };
    export let variant: ArrayListVariant = "classic";

    let cSettings = settings as HudArrayListSettings;
    const moduleLoader = new LatestArrayListModuleLoader(getModules);

    let moduleSnapshot: Module[] = [];
    let enabledModules: ArrayListEntry[] = [];
    let useSpacedNames = false;
    let motionDuration = 200;
    let motionOffset = 50;
    let previousVariant = variant;

    $: motionDuration = hudMotionDuration(variant, $prefersReducedMotion);
    $: motionOffset = getArrayListMotionOffset(variant, cSettings.itemAlignment);

    function renderModuleSnapshot() {
        const formatName = useSpacedNames
            ? convertToSpacedString
            : (name: string) => name;

        const nextEntries = buildArrayListEntries(
            moduleSnapshot,
            cSettings,
            variant,
            formatName,
            getTextWidth,
        );

        if (areArrayListEntriesRenderEquivalent(enabledModules, nextEntries)) {
            return;
        }

        enabledModules = nextEntries;
    }

    async function refreshModuleSnapshot() {
        try {
            const modules = await moduleLoader.loadLatest();
            if (modules === null) {
                return;
            }

            moduleSnapshot = modules;
            renderModuleSnapshot();
        } catch (error) {
            console.error("[ArrayList] Failed to refresh modules", error);
        }
    }

    function handleModuleToggle(event: ModuleToggleEvent) {
        let moduleFound = false;
        const updatedModules = moduleSnapshot.map(module => {
            if (module.name !== event.moduleName) {
                return module;
            }

            moduleFound = true;
            return {
                ...module,
                enabled: event.enabled,
                hidden: event.hidden,
            };
        });

        if (!moduleFound) {
            void refreshModuleSnapshot();
            return;
        }

        moduleLoader.invalidate();
        moduleSnapshot = updatedModules;
        renderModuleSnapshot();
    }

    $: if (cSettings !== settings) {
        cSettings = settings as HudArrayListSettings;
        renderModuleSnapshot();
    }

    onMount(() => {
        let fontCallbackActive = true;
        const unsubscribe = spaceSeperatedNames.subscribe((enabled) => {
            useSpacedNames = enabled;
            renderModuleSnapshot();
        });

        void document.fonts.ready.then(() => {
            if (!fontCallbackActive) {
                return;
            }
            renderModuleSnapshot();
        });

        void refreshModuleSnapshot();

        return () => {
            unsubscribe();
            fontCallbackActive = false;
            moduleLoader.invalidate();
        };
    });

    listen("moduleToggle", handleModuleToggle);

    listen("refreshArrayList", () => {
        void refreshModuleSnapshot();
    });

    listen("socketReady", () => {
        void refreshModuleSnapshot();
    });

    $: if (variant !== previousVariant) {
        previousVariant = variant;
        renderModuleSnapshot();
    }
</script>

<div class="arraylist">
    {#each enabledModules as {name, displayName, visibleTag} (name)}
        <div
                class="module"
                class:has-visible-tag={visibleTag !== null}
                style={cSettings.itemAlignment === "Left" ? "margin-right: auto;" : "margin-left: auto;"}
                transition:fly={{ x: motionOffset, duration: motionDuration }}
        >
            <span class="module-name">{displayName}</span>
            {#if visibleTag !== null}
                <span class="tag">{visibleTag}</span>
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
