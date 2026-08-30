<script lang="ts">
    import type {ConfigurableSetting} from "../../../../integration/types";
    import type {ClickGuiValueChangeEvent} from "../../../../integration/events";
    import {listen} from "../../../../integration/ws";
    import GenericSetting from "../../../../shared/settings/GenericSetting.svelte";
    import {createLatestValueSaveQueue} from "../../theme/latestValueSaveQueue";
    import type {ClickGuiDataSource} from "./model/clickGuiDataSource";
    import {shouldLoadModernModuleSettings} from "./model/modernInteractionState";
    import {MODERN_SETTING_STAGGER_LIMIT, motionStaggerIndex} from "./model/modernMotion";
    import {describeModernModuleError} from "./model/modernModulePresentation.ts";

    let {name, hasSettings, expanded, settingsPath, dataSource, externalError, onError, onClearError, onCollapse} = $props<{
        name: string;
        hasSettings: boolean;
        expanded: boolean;
        settingsPath: string;
        dataSource: ClickGuiDataSource;
        externalError: string | null;
        onError: (message: string) => void;
        onClearError: () => void;
        onCollapse: () => void;
    }>();

    let configurable = $state<ConfigurableSetting | null>(null);
    let loading = $state(false);
    let savePending = $state(false);
    let saveError = $state<string | null>(null);
    const saveQueue = createLatestValueSaveQueue<ConfigurableSetting>({
        save: settings => dataSource.setModuleSettings(name, settings),
        reload: () => dataSource.getModuleSettings(name),
        onConfirmed: settings => configurable = settings,
        onStateChange: state => {
            savePending = state.saving;
            saveError = state.error
                ? describeModernModuleError(state.error, "Settings could not be saved.")
                : null;
        },
    });

    listen("clickGuiValueChange", (event: ClickGuiValueChangeEvent) => {
        if (event.configurable.name !== name || saveQueue.isSaving() || saveQueue.hasPending()) return;
        configurable = structuredClone(event.configurable);
        loading = false;
    });

    $effect(() => {
        if (shouldLoadModernModuleSettings({
            expanded,
            hasSettings,
            loaded: configurable !== null,
            loading,
        })) void refresh();
    });

    async function refresh(): Promise<void> {
        loading = true;
        try {
            const nextConfigurable = await dataSource.getModuleSettings(name);
            configurable = nextConfigurable;
            if (!nextConfigurable.value.some((setting: {name: string}) => setting.name !== "Bind")) onCollapse();
            onClearError();
        } catch (error) {
            onError(describeModernModuleError(error, "Settings could not be loaded."));
        } finally {
            loading = false;
        }
    }

    function scheduleSave(): void {
        if (!configurable) return;
        onClearError();
        saveError = null;
        saveQueue.enqueue(structuredClone($state.snapshot(configurable)));
    }
</script>

{#if expanded}
    <div
            id="modern-module-settings-{name}"
            class="module-settings"
            role="region"
            aria-label="{name} settings"
            aria-busy={loading || savePending}
    >
        {#if loading && !configurable}
            <div class="settings-status" role="status"><span class="spinner" aria-hidden="true"></span>Loading settings</div>
        {:else if configurable}
            {#each configurable.value as _, index (configurable.value[index].name)}
                <div class="modern-setting-shell" style:--modern-setting-enter-index={motionStaggerIndex(index, MODERN_SETTING_STAGGER_LIMIT)}>
                    <GenericSetting path={settingsPath} bind:setting={configurable.value[index]} on:change={scheduleSave}/>
                </div>
            {/each}
        {/if}
        {#if externalError || saveError}
            <div class="settings-error" role="alert">
                <span>{saveError ?? externalError}</span>
                {#if !configurable}<button type="button" onclick={refresh}>Retry</button>
                {:else if saveError}<button type="button" onclick={() => saveQueue.retry()}>Retry save</button>{/if}
            </div>
        {/if}
    </div>
{/if}
