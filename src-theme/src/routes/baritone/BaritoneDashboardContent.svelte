<script lang="ts">
    import {
        BARITONE_TABS,
        type BaritoneCommandResult,
        type BaritoneSetting,
        type BaritoneSettingValue,
        type BaritoneTabId,
        type BaritoneTaskRequest,
        type BaritoneViewState,
        type BaritoneWaypoint,
        type BaritoneWaypointRequest,
    } from "../../integration/baritone";
    import BaritoneConsole from "./BaritoneConsole.svelte";
    import BaritoneOverview from "./BaritoneOverview.svelte";
    import BaritoneSettings from "./BaritoneSettings.svelte";
    import BaritoneTaskComposer from "./BaritoneTaskComposer.svelte";
    import BaritoneWaypoints from "./BaritoneWaypoints.svelte";
    import type {BaritoneDashboardFields} from "./baritoneDashboardState.ts";

    let {
        activeTab, viewState, fields, busyAction, error, blockOptions, playerOptions, nativeTextInput,
        onDismissError, onError, onOpenConsole, onSubmitTask, onSaveSetting, onResetSetting, onResetAllSettings,
        onAddWaypoint, onDeleteWaypoint, onNavigateToWaypoint, onConsoleInput, onRunCommand,
        onSelectCompletion,
    } = $props<{
        activeTab: BaritoneTabId;
        viewState: BaritoneViewState;
        fields: BaritoneDashboardFields;
        busyAction: string | null;
        error: string | null;
        blockOptions: string[];
        playerOptions: string[];
        nativeTextInput: boolean;
        onDismissError: () => void;
        onError: (message: string) => void;
        onOpenConsole: () => void;
        onSubmitTask: (request: BaritoneTaskRequest) => Promise<void>;
        onSaveSetting: (setting: BaritoneSetting, value: BaritoneSettingValue) => Promise<void>;
        onResetSetting: (setting: BaritoneSetting) => Promise<void>;
        onResetAllSettings: () => Promise<void>;
        onAddWaypoint: (request: BaritoneWaypointRequest) => Promise<boolean>;
        onDeleteWaypoint: (waypoint: BaritoneWaypoint) => Promise<void>;
        onNavigateToWaypoint: (waypoint: BaritoneWaypoint) => Promise<void>;
        onConsoleInput: (value: string) => void;
        onRunCommand: (command: string) => Promise<BaritoneCommandResult | undefined>;
        onSelectCompletion: (completion: string) => void;
    }>();

    let snapshot = $derived(viewState.snapshot);
    let canStartTask = $derived(snapshot.availability === "AVAILABLE" && snapshot.status !== "NO_WORLD");
    let activeTabLabel = $derived(BARITONE_TABS.find(tab => tab.id === activeTab)?.label ?? "Overview");
    let busy = $derived(busyAction !== null);
</script>

<div
        id="baritone-active-panel"
        class="content-panel"
        role="tabpanel"
        aria-labelledby={`baritone-tab-${activeTab}`}
        tabindex="0"
>
    <div class="content-heading">
        <div><p class="overline">Dashboard</p><h2>{activeTabLabel}</h2></div>
        {#if busyAction}<div class="busy-state" aria-live="polite"><span></span> Applying {busyAction}</div>{/if}
    </div>
    {#if error}
        <div class="global-error" role="alert"><span>{error}</span><button type="button" aria-label="Dismiss error" onclick={onDismissError}>×</button></div>
    {/if}
    {#if snapshot.status === "UNAVAILABLE" || snapshot.status === "NO_WORLD"}
        <div class="availability-banner" role="status">
            <strong>{snapshot.status === "UNAVAILABLE" ? "Baritone is unavailable" : "No world is loaded"}</strong>
            <span>{snapshot.failure ?? (snapshot.status === "UNAVAILABLE" ? "This build did not expose the Baritone capability." : "Join a world before creating a navigation task.")}</span>
        </div>
    {/if}

    {#if activeTab === "overview"}
        <BaritoneOverview {snapshot} route={viewState.route} {onOpenConsole}/>
    {:else if activeTab === "navigate" || activeTab === "mine" || activeTab === "follow" || activeTab === "farm" || activeTab === "explore" || activeTab === "build" || activeTab === "elytra"}
        <BaritoneTaskComposer kind={activeTab} disabled={!canStartTask} {busy} {blockOptions} {playerOptions} {nativeTextInput} onSubmit={onSubmitTask}/>
    {:else if activeTab === "waypoints"}
        <BaritoneWaypoints
                {fields}
                waypoints={snapshot.waypoints}
                {canStartTask}
                {busy}
                {nativeTextInput}
                onAdd={onAddWaypoint}
                onDelete={onDeleteWaypoint}
                onNavigate={onNavigateToWaypoint}
                {onError}
        />
    {:else if activeTab === "settings"}
        <BaritoneSettings
                {fields}
                settings={snapshot.settings}
                {busy}
                {nativeTextInput}
                onSave={onSaveSetting}
                onReset={onResetSetting}
                onResetAll={onResetAllSettings}
        />
    {:else if activeTab === "console"}
        <BaritoneConsole
                {fields}
                logs={snapshot.logs}
                {canStartTask}
                {busy}
                {nativeTextInput}
                onInput={onConsoleInput}
                onRun={onRunCommand}
                {onSelectCompletion}
        />
    {/if}
</div>
