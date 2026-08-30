<script lang="ts">
    import {onMount} from "svelte";
    import {REST_BASE, isStatic} from "../../integration/host";
    import {getRegistryItems} from "../../integration/rest";
    import {listen} from "../../integration/ws";
    import {
        createBaritoneDataSource,
        createBaritoneRestClient,
        createInitialBaritoneViewState,
        type BaritoneCommandResult,
        type BaritoneControlAction,
        type BaritoneSetting,
        type BaritoneSettingValue,
        type BaritoneSubscribe,
        type BaritoneTabId,
        type BaritoneTaskRequest,
        type BaritoneWaypoint,
        type BaritoneWaypointRequest,
    } from "../../integration/baritone";
    import BaritoneDashboardContent from "./BaritoneDashboardContent.svelte";
    import BaritoneDashboardHeader from "./BaritoneDashboardHeader.svelte";
    import BaritoneTabRail from "./BaritoneTabRail.svelte";
    import {describeBaritoneError} from "./baritoneDashboardPresentation.ts";
    import {
        createBaritoneDashboardFields,
        createCompletionController,
    } from "./baritoneDashboardState.ts";
    import "./BaritoneDashboard.styles.scss";

    const client = createBaritoneRestClient({baseUrl: `${REST_BASE}/api/v1/client/baritone`});
    const subscribe = listen as unknown as BaritoneSubscribe;
    const fields = $state(createBaritoneDashboardFields());
    let activeTab = $state<BaritoneTabId>("overview");
    let viewState = $state(createInitialBaritoneViewState());
    let loading = $state(true);
    let busyAction = $state<string | null>(null);
    let error = $state<string | null>(null);
    let blockOptions = $state<string[]>([]);
    let playerOptions = $state<string[]>([]);

    const dataSource = createBaritoneDataSource({client, subscribe, onChange: next => viewState = next});
    const completionController = createCompletionController(client, next => fields.completions = next);

    onMount(() => {
        void initialize();
        return () => {
            completionController.stop();
            dataSource.stop();
        };
    });

    async function initialize(): Promise<void> {
        loading = true;
        error = null;
        const [stateResult, blocksResult, playersResult] = await Promise.allSettled([
            dataSource.refresh(), getRegistryItems("blocks"), getRegistryItems("world_players"),
        ]);
        if (blocksResult.status === "fulfilled") blockOptions = Object.keys(blocksResult.value).sort();
        if (playersResult.status === "fulfilled") {
            playerOptions = Object.keys(playersResult.value).sort((left, right) => left.localeCompare(right));
        }
        if (stateResult.status === "rejected") {
            error = describeBaritoneError(stateResult.reason, "Unable to load Baritone state.");
        }
        loading = false;
    }

    async function refresh(): Promise<void> {
        await perform("refresh", () => dataSource.refresh(), false);
    }

    async function submitTask(request: BaritoneTaskRequest): Promise<void> {
        await perform("task", async () => {
            await client.putTask(request);
            await dataSource.refresh();
            activeTab = "overview";
        });
    }

    async function control(action: BaritoneControlAction): Promise<void> {
        await perform(action.toLocaleLowerCase(), async () => {
            await client.control(action);
            await dataSource.refresh();
        });
    }

    async function saveSetting(setting: BaritoneSetting, value: BaritoneSettingValue): Promise<void> {
        await client.updateSetting(setting.name, value);
        await dataSource.refresh();
    }

    async function resetSetting(setting: BaritoneSetting): Promise<void> {
        await client.resetSetting(setting.name);
        await dataSource.refresh();
    }

    async function resetAllSettings(): Promise<void> {
        await perform("reset-settings", async () => {
            await client.resetSettings();
            await dataSource.refresh();
        });
    }

    async function addWaypoint(request: BaritoneWaypointRequest): Promise<boolean> {
        return (await perform("add-waypoint", async () => {
            await client.addWaypoint(request);
            await dataSource.refresh();
            return true;
        })) ?? false;
    }

    async function deleteWaypoint(waypoint: BaritoneWaypoint): Promise<void> {
        await perform(`delete-${waypoint.id}`, async () => {
            await client.deleteWaypoint(waypoint.id);
            await dataSource.refresh();
        });
    }

    async function navigateToWaypoint(waypoint: BaritoneWaypoint): Promise<void> {
        await submitTask({type: "GOTO", ...waypoint.position});
    }

    function updateConsoleInput(value: string): void {
        fields.consoleInput = value;
        completionController.update(value);
    }

    function selectCompletion(completion: string): void {
        fields.consoleInput = completion;
        fields.completions = [];
    }

    async function runConsoleCommand(command: string): Promise<BaritoneCommandResult | undefined> {
        return perform("command", async () => {
            const result = await client.command(command);
            await dataSource.refresh();
            return result;
        });
    }

    async function perform<Result>(
        actionName: string,
        action: () => Promise<Result>,
        clearError = true,
    ): Promise<Result | undefined> {
        if (clearError) error = null;
        busyAction = actionName;
        try {
            return await action();
        } catch (caught) {
            error = describeBaritoneError(caught, "Baritone could not complete the request.");
            return undefined;
        } finally {
            busyAction = null;
        }
    }

    function selectTab(tab: BaritoneTabId): void {
        activeTab = tab;
        error = null;
    }
</script>

<svelte:head><title>Baritone · LiquidBounce</title></svelte:head>

<main class="baritone-screen">
    <div class="dashboard-shell">
        <BaritoneDashboardHeader snapshot={viewState.snapshot} {loading} {busyAction} onControl={control} onRefresh={refresh}/>
        <div class="dashboard-layout">
            <BaritoneTabRail {activeTab} stateRevision={viewState.stateRevision} routeRevision={viewState.routeRevision} onSelect={selectTab}/>
            <BaritoneDashboardContent
                    {activeTab}
                    {viewState}
                    {fields}
                    {busyAction}
                    {error}
                    {blockOptions}
                    {playerOptions}
                    nativeTextInput={isStatic}
                    onDismissError={() => error = null}
                    onError={message => error = message}
                    onOpenConsole={() => activeTab = "console"}
                    onSubmitTask={submitTask}
                    onSaveSetting={saveSetting}
                    onResetSetting={resetSetting}
                    onResetAllSettings={resetAllSettings}
                    onAddWaypoint={addWaypoint}
                    onDeleteWaypoint={deleteWaypoint}
                    onNavigateToWaypoint={navigateToWaypoint}
                    onConsoleInput={updateConsoleInput}
                    onRunCommand={runConsoleCommand}
                    onSelectCompletion={selectCompletion}
            />
        </div>
    </div>
</main>
