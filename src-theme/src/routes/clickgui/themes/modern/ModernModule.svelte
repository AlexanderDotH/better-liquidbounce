<script lang="ts">
    import {onDestroy, onMount} from "svelte";
    import {setItem} from "../../../../integration/persistent_storage";
    import {spaceSeperatedNames} from "../../../../theme/theme_config";
    import {
        description as descriptionStore,
        highlightModuleName,
        scaleFactor,
    } from "../../clickgui_store";
    import ModernModuleSettings from "./ModernModuleSettings.svelte";
    import type {ClickGuiDataSource} from "./model/clickGuiDataSource";
    import {productionClickGuiDataSource} from "./model/clickGuiDataSource";
    import {modernModuleExpansionKey} from "./model/modernInteractionState";
    import {
        MODERN_MODULE_STAGGER_LIMIT,
        motionStaggerIndex,
    } from "./model/modernMotion";
    import {motionAwareScrollBehavior} from "./modernShellState";
    import {
        describeModernModuleError,
        modernModuleDescription,
        modernModuleDisplayName,
        prefersReducedMotion,
        shouldToggleModernModuleExpansion,
    } from "./model/modernModulePresentation.ts";
    import "./ModernModule.styles.scss";

    interface Props {
        name: string;
        enabled: boolean;
        description: string;
        aliases: string[];
        hasSettings: boolean;
        moduleIndex: number;
        revealed: boolean;
        dataSource?: ClickGuiDataSource;
    }

    let {
        name,
        enabled,
        description,
        aliases,
        hasSettings,
        moduleIndex,
        revealed,
        dataSource = productionClickGuiDataSource,
    }: Props = $props();

    let moduleButton = $state<HTMLButtonElement>();
    let liveEnabled = $state(false);
    let expanded = $state(false);
    let togglePending = $state(false);
    let toggleMotionVersion = $state(0);
    let toggleFeedbackActive = $state(false);
    let toggleFeedbackTimeout: number | null = null;
    let interactionError = $state<string | null>(null);

    let settingsPath = $derived(modernModuleExpansionKey(name));

    $effect(() => {
        liveEnabled = enabled;
    });

    $effect(() => {
        if ($highlightModuleName !== name || !moduleButton) {
            return;
        }

        const timeout = window.setTimeout(() => {
            moduleButton?.scrollIntoView({
                behavior: motionAwareScrollBehavior(prefersReducedMotion()),
                block: "center",
            });
            moduleButton?.focus({preventScroll: true});
        }, 90);

        return () => window.clearTimeout(timeout);
    });

    onMount(() => {
        expanded = hasSettings && localStorage.getItem(settingsPath) === "true";
    });

    onDestroy(() => {
        if (toggleFeedbackTimeout !== null) {
            clearTimeout(toggleFeedbackTimeout);
        }
    });

    async function toggleModule(): Promise<void> {
        if (togglePending) {
            return;
        }

        const previousEnabled = liveEnabled;
        liveEnabled = !previousEnabled;
        toggleMotionVersion += 1;
        startToggleFeedback();
        togglePending = true;
        interactionError = null;

        try {
            await dataSource.setModuleEnabled(name, liveEnabled);
        } catch (error) {
            liveEnabled = previousEnabled;
            interactionError = describeModernModuleError(error, "Module state could not be changed.");
        } finally {
            togglePending = false;
        }
    }

    function startToggleFeedback(): void {
        if (toggleFeedbackTimeout !== null) {
            clearTimeout(toggleFeedbackTimeout);
        }

        toggleFeedbackActive = true;
        toggleFeedbackTimeout = window.setTimeout(() => {
            toggleFeedbackTimeout = null;
            toggleFeedbackActive = false;
        }, 500);
    }

    function toggleExpanded(): void {
        if (!hasSettings) {
            return;
        }

        expanded = !expanded;
        void persistExpansion();
    }

    async function persistExpansion(): Promise<void> {
        try {
            await setItem(settingsPath, expanded.toString());
        } catch (error) {
            interactionError = describeModernModuleError(error, "Expansion state could not be saved.");
        }
    }

    function handleModuleKeydown(event: KeyboardEvent): void {
        if (!shouldToggleModernModuleExpansion(event.key, expanded, hasSettings)) return;
        event.preventDefault();
        toggleExpanded();
    }

    function showDescription(): void {
        if (!moduleButton) {
            return;
        }

        descriptionStore.set(modernModuleDescription(
            moduleButton.getBoundingClientRect(),
            window.innerWidth,
            $scaleFactor,
            description,
            aliases,
            $spaceSeperatedNames,
        ));
    }

    function displayName(value: string): string {
        return modernModuleDisplayName(value, $spaceSeperatedNames);
    }
</script>

<article
        class="module"
        class:enabled={liveEnabled}
        class:expanded
        class:highlighted={$highlightModuleName === name}
        class:pending={togglePending}
        class:state-feedback={toggleFeedbackActive}
        class:revealed
        style:--modern-module-enter-index={motionStaggerIndex(moduleIndex, MODERN_MODULE_STAGGER_LIMIT)}
>
    <button
            class="module-row"
            type="button"
            aria-pressed={liveEnabled}
            aria-busy={togglePending}
            aria-expanded={hasSettings ? expanded : undefined}
            aria-controls={hasSettings ? `modern-module-settings-${name}` : undefined}
            title={hasSettings ? "Left-click to toggle · Right-click to open settings" : "Click to toggle"}
            bind:this={moduleButton}
            onclick={toggleModule}
            oncontextmenu={(event) => {
                event.preventDefault();
                toggleExpanded();
            }}
            onkeydown={handleModuleKeydown}
            onmouseenter={showDescription}
            onmouseleave={() => descriptionStore.set(null)}
            onfocus={showDescription}
            onblur={() => descriptionStore.set(null)}
    >
        {#key toggleMotionVersion}
            {#if toggleMotionVersion > 0 && toggleFeedbackActive}
                <span class="toggle-sweep" aria-hidden="true"></span>
            {/if}
        {/key}

        <span class="state-dot" aria-hidden="true"></span>
        <span class="module-name">{displayName(name)}</span>

        {#if hasSettings}
            <svg class="expand-mark" class:expanded aria-hidden="true" viewBox="0 0 16 16">
                <path d="m5.7 3.3 4.7 4.7-4.7 4.7 1.2 1.2L12.8 8 6.9 2.1 5.7 3.3Z"/>
            </svg>
        {/if}
    </button>

    <ModernModuleSettings
            {name}
            {hasSettings}
            {expanded}
            {settingsPath}
            {dataSource}
            externalError={interactionError}
            onError={message => interactionError = message}
            onClearError={() => interactionError = null}
            onCollapse={() => expanded = false}
    />
</article>
