<script lang="ts">
    import {onMount} from "svelte";
    import {REST_BASE, isStatic} from "../../integration/host";
    import {getRegistryItems} from "../../integration/rest";
    import {listen} from "../../integration/ws";
    import {
        BARITONE_TABS,
        createBaritoneDataSource,
        createBaritoneRestClient,
        createInitialBaritoneViewState,
        filterBaritoneSettings,
        type BaritoneControlAction,
        type BaritoneFlyOwnership,
        type BaritoneLocomotion,
        type BaritoneNavigationPhase,
        type BaritoneSetting,
        type BaritoneSettingValue,
        type BaritoneSubscribe,
        type BaritoneTabId,
        type BaritoneTaskRequest,
        type BaritoneWaypoint,
    } from "../../integration/baritone";
    import BaritoneField from "./BaritoneField.svelte";
    import BaritoneRouteMap from "./BaritoneRouteMap.svelte";
    import BaritoneSettingEditor from "./BaritoneSettingEditor.svelte";
    import BaritoneTaskComposer from "./BaritoneTaskComposer.svelte";

    const client = createBaritoneRestClient({
        baseUrl: `${REST_BASE}/api/v1/client/baritone`,
    });
    const subscribe = listen as unknown as BaritoneSubscribe;

    let activeTab = $state<BaritoneTabId>("overview");
    let viewState = $state(createInitialBaritoneViewState());
    let loading = $state(true);
    let busyAction = $state<string | null>(null);
    let error = $state<string | null>(null);
    let blockOptions = $state<string[]>([]);
    let playerOptions = $state<string[]>([]);
    let settingQuery = $state("");
    let waypointName = $state("");
    let waypointTag = $state("USER");
    let waypointX = $state("0");
    let waypointY = $state("64");
    let waypointZ = $state("0");
    let consoleInput = $state("");
    let consoleOutput = $state<string | null>(null);
    let completions = $state<string[]>([]);
    let completionRevision = 0;
    let completionTimer: number | undefined;

    const dataSource = createBaritoneDataSource({
        client,
        subscribe,
        onChange: nextState => {
            viewState = nextState;
        },
    });

    let snapshot = $derived(viewState.snapshot);
    let navigation = $derived(snapshot.navigation!);
    let canStartTask = $derived(snapshot.availability === "AVAILABLE" && snapshot.status !== "NO_WORLD");
    let hasTask = $derived(snapshot.task !== null);
    let filteredSettings = $derived(filterBaritoneSettings(snapshot.settings, settingQuery));
    let activeTabLabel = $derived(BARITONE_TABS.find(tab => tab.id === activeTab)?.label ?? "Overview");

    onMount(() => {
        void initialize();
        return () => {
            if (completionTimer !== undefined) {
                window.clearTimeout(completionTimer);
            }
            dataSource.stop();
        };
    });

    async function initialize(): Promise<void> {
        loading = true;
        error = null;
        const [stateResult, blocksResult, playersResult] = await Promise.allSettled([
            dataSource.refresh(),
            getRegistryItems("blocks"),
            getRegistryItems("world_players"),
        ]);

        if (blocksResult.status === "fulfilled") {
            blockOptions = Object.keys(blocksResult.value).sort();
        }
        if (playersResult.status === "fulfilled") {
            playerOptions = Object.keys(playersResult.value).sort((left, right) => left.localeCompare(right));
        }
        if (stateResult.status === "rejected") {
            error = describeError(stateResult.reason, "Unable to load Baritone state.");
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

    async function addWaypoint(): Promise<void> {
        const name = waypointName.trim();
        if (!name) {
            error = "Waypoint name is required.";
            return;
        }

        await perform("add-waypoint", async () => {
            await client.addWaypoint({
                name,
                tag: waypointTag.trim() || undefined,
                x: coordinate(waypointX, "X"),
                y: coordinate(waypointY, "Y"),
                z: coordinate(waypointZ, "Z"),
            });
            waypointName = "";
            await dataSource.refresh();
        });
    }

    async function deleteWaypoint(waypoint: BaritoneWaypoint): Promise<void> {
        await perform(`delete-${waypoint.id}`, async () => {
            await client.deleteWaypoint(waypoint.id);
            await dataSource.refresh();
        });
    }

    async function navigateToWaypoint(waypoint: BaritoneWaypoint): Promise<void> {
        await submitTask({
            type: "GOTO",
            x: waypoint.position.x,
            y: waypoint.position.y,
            z: waypoint.position.z,
        });
    }

    function updateConsoleInput(value: string): void {
        consoleInput = value;
        completions = [];
        completionRevision += 1;
        const requestedRevision = completionRevision;
        if (completionTimer !== undefined) {
            window.clearTimeout(completionTimer);
        }
        if (!value.trim()) {
            return;
        }

        completionTimer = window.setTimeout(async () => {
            try {
                const nextCompletions = await client.completions(value);
                if (requestedRevision === completionRevision) {
                    completions = nextCompletions.slice(0, 8);
                }
            } catch {
                if (requestedRevision === completionRevision) {
                    completions = [];
                }
            }
        }, 140);
    }

    async function runConsoleCommand(): Promise<void> {
        const command = consoleInput.trim();
        if (!command) {
            error = "Enter a Baritone command.";
            return;
        }

        await perform("command", async () => {
            const result = await client.command(command);
            consoleOutput = result.output ?? (result.accepted ? "Command accepted." : "Command rejected.");
            consoleInput = "";
            completions = [];
            await dataSource.refresh();
        });
    }

    function selectCompletion(completion: string): void {
        consoleInput = completion;
        completions = [];
    }

    async function perform(
        actionName: string,
        action: () => Promise<void>,
        clearError = true,
    ): Promise<void> {
        if (clearError) {
            error = null;
        }
        busyAction = actionName;
        try {
            await action();
        } catch (caught) {
            error = describeError(caught, "Baritone could not complete the request.");
        } finally {
            busyAction = null;
        }
    }

    function coordinate(value: string, name: string): number {
        const parsed = Number(value);
        if (Number.isFinite(parsed)) {
            return parsed;
        }
        throw new Error(`${name} must be a finite number.`);
    }

    function selectTab(tab: BaritoneTabId): void {
        activeTab = tab;
        error = null;
    }

    function handleTabKeydown(event: KeyboardEvent, index: number): void {
        const lastIndex = BARITONE_TABS.length - 1;
        let nextIndex = index;
        if (event.key === "ArrowDown" || event.key === "ArrowRight") {
            nextIndex = index === lastIndex ? 0 : index + 1;
        } else if (event.key === "ArrowUp" || event.key === "ArrowLeft") {
            nextIndex = index === 0 ? lastIndex : index - 1;
        } else if (event.key === "Home") {
            nextIndex = 0;
        } else if (event.key === "End") {
            nextIndex = lastIndex;
        } else {
            return;
        }

        event.preventDefault();
        const nextTab = BARITONE_TABS[nextIndex];
        activeTab = nextTab.id;
        requestAnimationFrame(() => document.getElementById(`baritone-tab-${nextTab.id}`)?.focus());
    }

    function describeError(caught: unknown, fallback: string): string {
        if (caught instanceof Error && caught.message.trim()) {
            return `${fallback} ${caught.message}`;
        }
        return fallback;
    }

    function statusLabel(): string {
        return ({
            UNAVAILABLE: "Unavailable",
            NO_WORLD: "No world",
            IDLE: "Idle",
            CALCULATING: "Calculating",
            PATHING: "Pathing",
            PAUSED: "Paused",
            FAILED: "Failed",
            ARRIVED: "Arrived",
        })[snapshot.status];
    }

    function etaLabel(): string {
        if (snapshot.etaSeconds === null) {
            return "—";
        }
        const minutes = Math.floor(snapshot.etaSeconds / 60);
        const seconds = Math.max(0, Math.round(snapshot.etaSeconds % 60));
        return minutes ? `${minutes}m ${seconds}s` : `${seconds}s`;
    }

    function progressPercent(): number {
        if (snapshot.progress === null || !Number.isFinite(snapshot.progress)) {
            return 0;
        }
        return Math.round(Math.max(0, Math.min(1, snapshot.progress)) * 100);
    }

    function locomotionLabel(locomotion: BaritoneLocomotion | null): string {
        if (locomotion === "FLY") return "Fly";
        if (locomotion === "WALK") return "Walk";
        return "None";
    }

    function navigationPhaseLabel(phase: BaritoneNavigationPhase): string {
        return ({
            IDLE: "Idle",
            WAITING_FOR_PATH: "Waiting for path",
            PLANNING: "Planning flight",
            ARMING: "Arming Fly",
            FLYING: "Flying",
            WALK_FALLBACK: "Walking fallback",
            WAITING_FOR_USER: "Waiting for user",
        })[phase];
    }

    function ownershipLabel(ownership: BaritoneFlyOwnership | null): string {
        if (ownership === "BARITONE") return "Baritone-owned Fly";
        if (ownership === "USER") return "User-owned Fly";
        return navigation.active === "WALK" ? "Baritone walking" : "No Fly lease";
    }

    function routeHeading(): string {
        if (navigation.active === "FLY") return "Active flight route";
        if (navigation.active === "WALK") return "Active walking route";
        return "Planned route";
    }

    function routeDescription(): string {
        if (navigation.active === "FLY") return "Collision-safe aerial route, capped at 512 points.";
        if (navigation.active === "WALK") return "Current upstream walking route, capped at 512 points.";
        return "Direction-preserving path preview, capped at 512 points.";
    }
</script>

<svelte:head><title>Baritone · LiquidBounce</title></svelte:head>

<main class="baritone-screen">
    <div class="dashboard-shell">
        <header class="dashboard-header">
            <div class="brand">
                <div class="brand-mark" aria-hidden="true">
                    <svg viewBox="0 0 24 24"><path d="M4 17.5 9.4 12l3.1 3.1L20 7.5M4 6.5h5M4 10h3"/></svg>
                </div>
                <div>
                    <p>LiquidBounce navigation</p>
                    <h1>Baritone</h1>
                </div>
            </div>

            <div class="header-status" aria-live="polite">
                <span class:warning={snapshot.status === "PAUSED"} class:error={snapshot.status === "FAILED" || snapshot.status === "UNAVAILABLE"} class:success={snapshot.status === "ARRIVED"}></span>
                <div>
                    <small>Current state</small>
                    <strong>{loading ? "Connecting…" : statusLabel()}</strong>
                </div>
            </div>

            <div class="header-actions">
                <button type="button" disabled={busyAction !== null || !hasTask || snapshot.status === "PAUSED"} onclick={() => control("PAUSE")}>
                    Pause
                </button>
                <button type="button" disabled={busyAction !== null || snapshot.status !== "PAUSED"} onclick={() => control("RESUME")}>
                    Resume
                </button>
                <button class="danger" type="button" disabled={busyAction !== null || !hasTask} onclick={() => control("CANCEL")}>
                    Cancel
                </button>
                <button class="icon-button" type="button" aria-label="Refresh Baritone state" disabled={busyAction !== null || loading} onclick={refresh}>
                    <svg aria-hidden="true" viewBox="0 0 16 16"><path d="M13.2 5.7A5.5 5.5 0 1 0 13 10m.2-4.3V2.8m0 2.9h-3"/></svg>
                </button>
            </div>
        </header>

        <div class="dashboard-layout">
            <nav class="tab-rail" aria-label="Baritone workflows">
                <div role="tablist" aria-orientation="vertical">
                    {#each BARITONE_TABS as tab, index (tab.id)}
                        <button
                                id={`baritone-tab-${tab.id}`}
                                type="button"
                                role="tab"
                                aria-selected={activeTab === tab.id}
                                aria-controls="baritone-active-panel"
                                tabindex={activeTab === tab.id ? 0 : -1}
                                class:active={activeTab === tab.id}
                                onclick={() => selectTab(tab.id)}
                                onkeydown={event => handleTabKeydown(event, index)}
                        >
                            <span class="tab-icon" aria-hidden="true">{index + 1}</span>
                            <span>{tab.label}</span>
                        </button>
                    {/each}
                </div>
                <div class="rail-footer">
                    <span>State rev. {viewState.stateRevision}</span>
                    <span>Route rev. {viewState.routeRevision}</span>
                </div>
            </nav>

            <div
                    id="baritone-active-panel"
                    class="content-panel"
                    role="tabpanel"
                    aria-labelledby={`baritone-tab-${activeTab}`}
                    tabindex="0"
            >
                <div class="content-heading">
                    <div>
                        <p class="overline">Dashboard</p>
                        <h2>{activeTabLabel}</h2>
                    </div>
                    {#if busyAction}
                        <div class="busy-state" aria-live="polite"><span></span> Applying {busyAction}</div>
                    {/if}
                </div>

                {#if error}
                    <div class="global-error" role="alert">
                        <span>{error}</span>
                        <button type="button" aria-label="Dismiss error" onclick={() => error = null}>×</button>
                    </div>
                {/if}

                {#if snapshot.status === "UNAVAILABLE" || snapshot.status === "NO_WORLD"}
                    <div class="availability-banner" role="status">
                        <strong>{snapshot.status === "UNAVAILABLE" ? "Baritone is unavailable" : "No world is loaded"}</strong>
                        <span>{snapshot.failure ?? (snapshot.status === "UNAVAILABLE" ? "This build did not expose the Baritone capability." : "Join a world before creating a navigation task.")}</span>
                    </div>
                {/if}

                {#if activeTab === "overview"}
                    <div class="overview-grid">
                        <section class="metric-card task-card" aria-labelledby="active-task-heading">
                            <p>Active task</p>
                            <h3 id="active-task-heading">{snapshot.task?.label ?? "No active task"}</h3>
                            <span>{snapshot.task?.details ?? (snapshot.task ? snapshot.task.type : "Choose a workflow to begin.")}</span>
                        </section>
                        <section class="metric-card">
                            <p>ETA</p>
                            <h3>{etaLabel()}</h3>
                            <span>{navigation.active === "FLY" && snapshot.etaSeconds === null ? "Unavailable for the active Fly mode" : snapshot.status === "CALCULATING" ? "Calculating route" : "Estimated remaining time"}</span>
                        </section>
                        <section class="metric-card">
                            <p>Progress</p>
                            <h3>{progressPercent()}%</h3>
                            <div class="progress-track" aria-label={`${progressPercent()} percent complete`} role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={progressPercent()}>
                                <span style:width={`${progressPercent()}%`}></span>
                            </div>
                        </section>
                        <section class="metric-card">
                            <p>Navigation mode</p>
                            <h3>{locomotionLabel(navigation.requested)}</h3>
                            <span>Active: {locomotionLabel(navigation.active)}{navigation.flyMode ? ` · ${navigation.flyMode}` : ""}</span>
                        </section>
                        <section class="metric-card">
                            <p>Navigation state</p>
                            <h3>{navigationPhaseLabel(navigation.phase)}</h3>
                            <span class="navigation-detail">{navigation.detail ?? snapshot.pauseReason ?? "Navigation is ready."}</span>
                        </section>
                        <section class="metric-card">
                            <p>Movement ownership</p>
                            <h3>{ownershipLabel(navigation.ownership)}</h3>
                            <span>{snapshot.pauseReason ?? (navigation.ownership === "USER" ? "The enabled Fly module remains user-controlled." : "No higher-priority movement owner detected.")}</span>
                        </section>
                        <section class="metric-card">
                            <p>Retries remaining</p>
                            <h3>{navigation.restartsRemaining}</h3>
                            <span>Shared Fly restart and walking retry budget.</span>
                        </section>
                    </div>

                    <section class="route-section" aria-labelledby="route-heading">
                        <div class="section-heading">
                            <div>
                                <h3 id="route-heading">{routeHeading()}</h3>
                                <p>{routeDescription()}</p>
                            </div>
                            <span>{viewState.route.points.length} nodes</span>
                        </div>
                        <BaritoneRouteMap points={viewState.route.points}/>
                    </section>

                    <section class="recent-log" aria-labelledby="recent-log-heading">
                        <div class="section-heading">
                            <div>
                                <h3 id="recent-log-heading">Recent activity</h3>
                                <p>Baritone status, warnings, and task results.</p>
                            </div>
                            <button type="button" onclick={() => activeTab = "console"}>Open console</button>
                        </div>
                        {#if snapshot.logs.length}
                            <ol>
                                {#each snapshot.logs.slice(-5).reverse() as entry (entry.revision)}
                                    <li class:error-log={entry.level === "ERROR"}>
                                        <time>{entry.timestamp}</time>
                                        <strong>{entry.level}</strong>
                                        <span>{entry.message}</span>
                                    </li>
                                {/each}
                            </ol>
                        {:else}
                            <p class="empty-copy">No Baritone activity has been reported.</p>
                        {/if}
                    </section>
                {:else if activeTab === "navigate" || activeTab === "mine" || activeTab === "follow" || activeTab === "farm" || activeTab === "explore" || activeTab === "build" || activeTab === "elytra"}
                    <BaritoneTaskComposer
                            kind={activeTab}
                            disabled={!canStartTask}
                            busy={busyAction !== null}
                            {blockOptions}
                            {playerOptions}
                            nativeTextInput={isStatic}
                            onSubmit={submitTask}
                    />
                {:else if activeTab === "waypoints"}
                    <section class="waypoint-layout" aria-labelledby="waypoint-heading">
                        <div class="section-heading">
                            <div>
                                <h3 id="waypoint-heading">Saved waypoints</h3>
                                <p>Upstream-compatible waypoints remain attached to their world and dimension.</p>
                            </div>
                            <span>{snapshot.waypoints.length} saved</span>
                        </div>

                        <div class="waypoint-form">
                            <BaritoneField id="baritone-waypoint-name" label="Name" value={waypointName} onValueChange={value => waypointName = value} placeholder="Base entrance" disabled={!canStartTask} nativeTextInput={isStatic}/>
                            <BaritoneField id="baritone-waypoint-tag" label="Tag" value={waypointTag} onValueChange={value => waypointTag = value} placeholder="USER" disabled={!canStartTask} nativeTextInput={isStatic}/>
                            <BaritoneField id="baritone-waypoint-x" label="X" value={waypointX} onValueChange={value => waypointX = value} inputMode="decimal" disabled={!canStartTask} nativeTextInput={isStatic}/>
                            <BaritoneField id="baritone-waypoint-y" label="Y" value={waypointY} onValueChange={value => waypointY = value} inputMode="decimal" disabled={!canStartTask} nativeTextInput={isStatic}/>
                            <BaritoneField id="baritone-waypoint-z" label="Z" value={waypointZ} onValueChange={value => waypointZ = value} inputMode="decimal" disabled={!canStartTask} nativeTextInput={isStatic}/>
                            <button class="primary" type="button" disabled={!canStartTask || busyAction !== null} onclick={addWaypoint}>Add waypoint</button>
                        </div>

                        {#if snapshot.waypoints.length}
                            <div class="waypoint-list">
                                {#each snapshot.waypoints as waypoint (waypoint.id)}
                                    <article>
                                        <div class="waypoint-marker" aria-hidden="true"></div>
                                        <div class="waypoint-copy">
                                            <strong>{waypoint.name}</strong>
                                            <span>{waypoint.tag ?? "USER"} · {waypoint.position.x}, {waypoint.position.y}, {waypoint.position.z}</span>
                                        </div>
                                        <button type="button" disabled={!canStartTask || busyAction !== null} onclick={() => navigateToWaypoint(waypoint)}>Go</button>
                                        <button class="danger-text" type="button" disabled={busyAction !== null} onclick={() => deleteWaypoint(waypoint)}>Delete</button>
                                    </article>
                                {/each}
                            </div>
                        {:else}
                            <p class="empty-copy">No waypoints are saved for this world.</p>
                        {/if}
                    </section>
                {:else if activeTab === "settings"}
                    <section class="settings-layout" aria-labelledby="settings-heading">
                        <div class="section-heading settings-heading">
                            <div>
                                <h3 id="settings-heading">Baritone settings</h3>
                                <p>Canonical typed values are persisted by LiquidBounce.</p>
                            </div>
                            <button type="button" disabled={busyAction !== null} onclick={resetAllSettings}>Reset all</button>
                        </div>
                        <BaritoneField
                                id="baritone-setting-search"
                                label="Search settings"
                                value={settingQuery}
                                onValueChange={value => settingQuery = value}
                                placeholder="Search by name or description"
                                nativeTextInput={isStatic}
                        />
                        <p class="result-count" aria-live="polite">{filteredSettings.length} of {snapshot.settings.length} settings</p>
                        <div class="setting-list">
                            {#each filteredSettings as setting (setting.name)}
                                <BaritoneSettingEditor
                                        {setting}
                                        busy={busyAction !== null}
                                        nativeTextInput={isStatic}
                                        onSave={saveSetting}
                                        onReset={resetSetting}
                                />
                            {/each}
                        </div>
                        {#if !filteredSettings.length}
                            <p class="empty-copy">No settings match “{settingQuery}”.</p>
                        {/if}
                    </section>
                {:else if activeTab === "console"}
                    <section class="console-layout" aria-labelledby="console-heading">
                        <div class="section-heading">
                            <div>
                                <h3 id="console-heading">Advanced console</h3>
                                <p>Send the full upstream command set without enabling Baritone chat control.</p>
                            </div>
                            <span>{snapshot.logs.length} messages</span>
                        </div>

                        <div class="console-window" role="log" aria-live="polite" aria-label="Baritone output">
                            {#if snapshot.logs.length}
                                {#each snapshot.logs as entry (entry.revision)}
                                    <div class:error-log={entry.level === "ERROR"} class:warning-log={entry.level === "WARNING"}>
                                        <time>{entry.timestamp}</time>
                                        <strong>[{entry.level}]</strong>
                                        <span>{entry.message}</span>
                                    </div>
                                {/each}
                            {:else}
                                <p>No output yet.</p>
                            {/if}
                            {#if consoleOutput}<div class="command-output"><strong>[RESULT]</strong><span>{consoleOutput}</span></div>{/if}
                        </div>

                        <div class="console-command">
                            <BaritoneField
                                    id="baritone-console-input"
                                    label="Command"
                                    value={consoleInput}
                                    onValueChange={updateConsoleInput}
                                    placeholder="goto 120 64 -30"
                                    disabled={!canStartTask || busyAction !== null}
                                    nativeTextInput={isStatic}
                            />
                            <button class="primary" type="button" disabled={!canStartTask || busyAction !== null || !consoleInput.trim()} onclick={runConsoleCommand}>Run</button>
                        </div>
                        {#if completions.length}
                            <div class="completion-list" aria-label="Command completions">
                                {#each completions as completion}
                                    <button type="button" onclick={() => selectCompletion(completion)}>{completion}</button>
                                {/each}
                            </div>
                        {/if}
                    </section>
                {/if}
            </div>
        </div>
    </div>
</main>

<style lang="scss">
  .baritone-screen {
    --baritone-text-primary: #eef1f5;
    --baritone-text-secondary: #aeb5bf;
    --baritone-text-muted: #89929f;
    --baritone-surface: color-mix(in srgb, var(--surface-color, #11151a) 88%, rgba(7, 9, 13, 0.94));
    --baritone-surface-panel: rgba(16, 20, 26, 0.96);
    --baritone-surface-raised: rgba(255, 255, 255, 0.045);
    --baritone-surface-raised-hover: rgba(255, 255, 255, 0.075);
    --baritone-border: rgba(255, 255, 255, 0.1);
    --baritone-divider: rgba(255, 255, 255, 0.075);
    --baritone-motion-duration: 140ms;
    position: absolute;
    inset: 0;
    box-sizing: border-box;
    padding: clamp(10px, 2vw, 22px);
    overflow: hidden;
    color: var(--baritone-text-primary);
    background: radial-gradient(circle at 50% 4%, color-mix(in srgb, var(--accent-color) 9%, transparent), transparent 43%);
    font-family: "Inter", "Roboto", sans-serif;
  }

  .dashboard-shell {
    width: min(1480px, 100%);
    height: 100%;
    margin: 0 auto;
    overflow: hidden;
    background: var(--baritone-surface);
    border: 1px solid var(--baritone-border);
    border-radius: 16px;
    box-shadow: 0 24px 70px rgba(0, 0, 0, 0.38);
    backdrop-filter: blur(22px);
    animation: baritone-dashboard-enter 260ms cubic-bezier(0.16, 1, 0.3, 1) backwards;
  }

  .dashboard-header {
    display: grid;
    grid-template-columns: minmax(220px, 1fr) auto minmax(320px, 1fr);
    align-items: center;
    gap: 20px;
    height: 72px;
    box-sizing: border-box;
    padding: 0 18px;
    background: rgba(255, 255, 255, 0.025);
    border-bottom: 1px solid var(--baritone-divider);
  }

  .brand,
  .header-status,
  .header-actions {
    display: flex;
    align-items: center;
  }

  .brand {
    gap: 11px;
  }

  .brand-mark {
    display: grid;
    width: 34px;
    height: 34px;
    place-items: center;
    color: color-mix(in srgb, var(--accent-color) 76%, white);
    background: color-mix(in srgb, var(--accent-color) 13%, rgba(255, 255, 255, 0.03));
    border: 1px solid color-mix(in srgb, var(--accent-color) 30%, var(--baritone-border));
    border-radius: 9px;
  }

  .brand-mark svg {
    width: 21px;
    fill: none;
    stroke: currentColor;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.7;
  }

  .brand p,
  .brand h1 {
    margin: 0;
  }

  .brand p {
    color: var(--baritone-text-muted);
    font-size: 9px;
    font-weight: 700;
    letter-spacing: 0.11em;
    text-transform: uppercase;
  }

  .brand h1 {
    margin-top: 3px;
    font-size: 18px;
    letter-spacing: -0.02em;
  }

  .header-status {
    gap: 8px;
    min-width: 118px;
    padding: 8px 12px;
    background: rgba(255, 255, 255, 0.035);
    border: 1px solid var(--baritone-border);
    border-radius: 999px;
  }

  .header-status > span {
    width: 8px;
    height: 8px;
    background: color-mix(in srgb, var(--accent-color) 76%, white);
    border-radius: 50%;
    box-shadow: 0 0 12px color-mix(in srgb, var(--accent-color) 55%, transparent);
  }

  .header-status > span.warning { background: #e9b85d; box-shadow: 0 0 12px rgba(233, 184, 93, 0.38); }
  .header-status > span.error { background: #ef7378; box-shadow: 0 0 12px rgba(239, 115, 120, 0.38); }
  .header-status > span.success { background: #71d99a; box-shadow: 0 0 12px rgba(113, 217, 154, 0.38); }

  .header-status small,
  .header-status strong {
    display: block;
  }

  .header-status small {
    color: var(--baritone-text-muted);
    font-size: 8px;
    text-transform: uppercase;
  }

  .header-status strong {
    margin-top: 2px;
    font-size: 11px;
  }

  .header-actions {
    justify-content: flex-end;
    gap: 7px;
  }

  button {
    color: var(--baritone-text-secondary);
    background: var(--baritone-surface-raised);
    border: 1px solid var(--baritone-border);
    border-radius: 7px;
    font-family: inherit;
    font-size: 10px;
    font-weight: 650;
    cursor: pointer;
    transition:
      color var(--baritone-motion-duration) ease,
      background-color var(--baritone-motion-duration) ease,
      border-color var(--baritone-motion-duration) ease,
      transform var(--baritone-motion-duration) ease;
  }

  button:hover:not(:disabled) {
    color: var(--baritone-text-primary);
    background: var(--baritone-surface-raised-hover);
    border-color: color-mix(in srgb, var(--accent-color) 35%, var(--baritone-border));
  }

  button:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--accent-color) 74%, white);
    outline-offset: 2px;
  }

  button:disabled {
    cursor: not-allowed;
    opacity: 0.4;
  }

  .header-actions button {
    height: 31px;
    padding: 0 11px;
  }

  .header-actions .danger,
  .danger-text {
    color: #f0a0a4;
  }

  .header-actions .icon-button {
    display: grid;
    width: 31px;
    padding: 0;
    place-items: center;
  }

  .icon-button svg {
    width: 14px;
    fill: none;
    stroke: currentColor;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.4;
  }

  .dashboard-layout {
    display: grid;
    grid-template-columns: 210px minmax(0, 1fr);
    height: calc(100% - 72px);
  }

  .tab-rail {
    display: flex;
    min-height: 0;
    flex-direction: column;
    justify-content: space-between;
    padding: 13px 10px;
    background: rgba(7, 10, 14, 0.26);
    border-right: 1px solid var(--baritone-divider);
  }

  .tab-rail [role="tablist"] {
    display: grid;
    gap: 3px;
  }

  .tab-rail [role="tab"] {
    display: grid;
    grid-template-columns: 25px minmax(0, 1fr);
    align-items: center;
    gap: 8px;
    min-height: 35px;
    padding: 0 9px;
    text-align: left;
    background: transparent;
    border-color: transparent;
  }

  .tab-rail [role="tab"].active {
    color: var(--baritone-text-primary);
    background: color-mix(in srgb, var(--accent-color) 12%, rgba(255, 255, 255, 0.035));
    border-color: color-mix(in srgb, var(--accent-color) 27%, transparent);
  }

  .tab-icon {
    display: grid;
    width: 19px;
    height: 19px;
    place-items: center;
    color: var(--baritone-text-muted);
    background: rgba(255, 255, 255, 0.04);
    border-radius: 5px;
    font: 700 8px/1 "JetBrains Mono", monospace;
  }

  .tab-rail [role="tab"].active .tab-icon {
    color: #0a0d10;
    background: color-mix(in srgb, var(--accent-color) 78%, white);
  }

  .rail-footer {
    display: flex;
    justify-content: space-between;
    padding: 9px 8px 0;
    color: var(--baritone-text-muted);
    border-top: 1px solid var(--baritone-divider);
    font: 8px/1.3 "JetBrains Mono", monospace;
  }

  .content-panel {
    min-width: 0;
    overflow: auto;
    padding: clamp(18px, 2.5vw, 32px);
    outline: none;
    scrollbar-color: rgba(255, 255, 255, 0.16) transparent;
  }

  .content-heading,
  .section-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
  }

  .content-heading {
    margin-bottom: 22px;
  }

  .overline,
  .content-heading h2 {
    margin: 0;
  }

  .overline {
    color: color-mix(in srgb, var(--accent-color) 70%, #c7ccd4);
    font-size: 9px;
    font-weight: 750;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .content-heading h2 {
    margin-top: 4px;
    font-size: 23px;
    letter-spacing: -0.03em;
  }

  .busy-state {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    color: var(--baritone-text-muted);
    font-size: 10px;
  }

  .busy-state span {
    width: 10px;
    height: 10px;
    border: 2px solid rgba(255, 255, 255, 0.16);
    border-top-color: var(--accent-color);
    border-radius: 50%;
    animation: baritone-spin 700ms linear infinite;
  }

  .global-error,
  .availability-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    margin-bottom: 18px;
    padding: 11px 13px;
    border-radius: 9px;
    font-size: 11px;
  }

  .global-error {
    color: #ffc2c4;
    background: rgba(144, 38, 45, 0.17);
    border: 1px solid rgba(255, 103, 111, 0.25);
  }

  .global-error button {
    width: 25px;
    height: 25px;
    padding: 0;
  }

  .availability-banner {
    align-items: flex-start;
    justify-content: flex-start;
    flex-direction: column;
    color: #f3d6aa;
    background: rgba(137, 91, 25, 0.14);
    border: 1px solid rgba(232, 176, 86, 0.22);
  }

  .availability-banner span {
    color: #bea982;
  }

  .overview-grid {
    display: grid;
    grid-template-columns: minmax(260px, 1.45fr) repeat(3, minmax(150px, 0.7fr));
    gap: 10px;
  }

  .metric-card,
  .route-section,
  .recent-log,
  .waypoint-layout,
  .settings-layout,
  .console-layout,
  :global(.task-composer) {
    background: var(--baritone-surface-panel);
    border: 1px solid var(--baritone-border);
    border-radius: 12px;
  }

  .metric-card {
    min-width: 0;
    padding: 14px;
  }

  .metric-card p,
  .metric-card h3,
  .metric-card > span {
    display: block;
    margin: 0;
  }

  .metric-card p {
    color: var(--baritone-text-muted);
    font-size: 9px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .metric-card h3 {
    margin-top: 8px;
    overflow: hidden;
    font-size: 17px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .metric-card > .navigation-detail {
    overflow: visible;
    white-space: normal;
  }

  .metric-card > span {
    margin-top: 6px;
    overflow: hidden;
    color: var(--baritone-text-muted);
    font-size: 10px;
    line-height: 1.4;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .task-card h3 {
    color: color-mix(in srgb, var(--accent-color) 70%, white);
  }

  .progress-track {
    height: 5px;
    margin-top: 10px;
    overflow: hidden;
    background: rgba(255, 255, 255, 0.07);
    border-radius: 999px;
  }

  .progress-track span {
    display: block;
    height: 100%;
    background: color-mix(in srgb, var(--accent-color) 77%, white);
    border-radius: inherit;
    transition: width 220ms ease;
  }

  .route-section,
  .recent-log,
  .waypoint-layout,
  .settings-layout,
  .console-layout,
  :global(.task-composer) {
    margin-top: 12px;
    padding: 16px;
  }

  .section-heading {
    align-items: center;
    margin-bottom: 13px;
  }

  .section-heading h3,
  .section-heading p {
    margin: 0;
  }

  .section-heading h3 {
    font-size: 14px;
  }

  .section-heading p {
    margin-top: 4px;
    color: var(--baritone-text-muted);
    font-size: 10px;
  }

  .section-heading > span,
  .result-count {
    color: var(--baritone-text-muted);
    font: 9px/1.3 "JetBrains Mono", monospace;
  }

  .section-heading button {
    height: 29px;
    padding: 0 10px;
  }

  .recent-log ol {
    display: grid;
    gap: 2px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .recent-log li,
  .console-window > div {
    display: grid;
    grid-template-columns: 60px 58px minmax(0, 1fr);
    gap: 8px;
    align-items: baseline;
    padding: 7px 8px;
    color: var(--baritone-text-secondary);
    border-radius: 6px;
    font: 10px/1.4 "JetBrains Mono", monospace;
  }

  .recent-log li:nth-child(odd),
  .console-window > div:nth-child(odd) {
    background: rgba(255, 255, 255, 0.025);
  }

  .recent-log time,
  .console-window time {
    color: var(--baritone-text-muted);
  }

  .recent-log strong,
  .console-window strong {
    color: color-mix(in srgb, var(--accent-color) 68%, white);
    font-size: 9px;
  }

  .recent-log .error-log strong,
  .console-window .error-log strong {
    color: #f19398;
  }

  .console-window .warning-log strong {
    color: #e9b85d;
  }

  .waypoint-form {
    display: grid;
    grid-template-columns: 1.3fr 0.65fr repeat(3, 0.55fr) auto;
    gap: 9px;
    align-items: end;
  }

  button.primary {
    height: 36px;
    padding: 0 14px;
    color: #0b0d10;
    background: color-mix(in srgb, var(--accent-color) 78%, white);
    border-color: transparent;
    font-weight: 750;
  }

  .waypoint-list,
  .setting-list {
    display: grid;
    gap: 7px;
    margin-top: 14px;
  }

  .waypoint-list article {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto auto;
    gap: 10px;
    align-items: center;
    padding: 10px;
    background: var(--baritone-surface-raised);
    border: 1px solid var(--baritone-border);
    border-radius: 9px;
  }

  .waypoint-marker {
    width: 9px;
    height: 9px;
    background: color-mix(in srgb, var(--accent-color) 80%, white);
    border: 3px solid color-mix(in srgb, var(--accent-color) 20%, rgba(255, 255, 255, 0.08));
    border-radius: 50%;
  }

  .waypoint-copy strong,
  .waypoint-copy span {
    display: block;
  }

  .waypoint-copy strong {
    font-size: 11px;
  }

  .waypoint-copy span {
    margin-top: 3px;
    color: var(--baritone-text-muted);
    font: 9px/1.3 "JetBrains Mono", monospace;
  }

  .waypoint-list button {
    height: 29px;
    padding: 0 9px;
  }

  .settings-heading {
    margin-bottom: 16px;
  }

  .result-count {
    margin: 9px 0 0;
    text-align: right;
  }

  .console-window {
    height: min(420px, 48vh);
    overflow: auto;
    padding: 8px;
    color: var(--baritone-text-secondary);
    background: rgba(3, 5, 8, 0.52);
    border: 1px solid var(--baritone-border);
    border-radius: 9px;
  }

  .console-window p {
    margin: 0;
    padding: 10px;
    color: var(--baritone-text-muted);
    font: 10px/1.5 "JetBrains Mono", monospace;
  }

  .console-window .command-output {
    grid-template-columns: 68px minmax(0, 1fr);
    color: #cdebd7;
  }

  .console-command {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 9px;
    align-items: end;
    margin-top: 11px;
  }

  .completion-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;
  }

  .completion-list button {
    min-height: 26px;
    padding: 0 8px;
    font: 9px/1 "JetBrains Mono", monospace;
  }

  .empty-copy {
    margin: 12px 0 0;
    padding: 24px;
    color: var(--baritone-text-muted);
    background: rgba(255, 255, 255, 0.02);
    border: 1px dashed var(--baritone-border);
    border-radius: 9px;
    font-size: 11px;
    text-align: center;
  }

  @keyframes baritone-dashboard-enter {
    from { opacity: 0; transform: translateY(6px); }
  }

  @keyframes baritone-spin {
    to { transform: rotate(360deg); }
  }

  @media (max-width: 1080px) {
    .dashboard-header {
      grid-template-columns: 1fr auto;
    }

    .header-status {
      display: none;
    }

    .overview-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .waypoint-form {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  @media (max-width: 760px) {
    .baritone-screen {
      padding: 0;
    }

    .dashboard-shell {
      border: 0;
      border-radius: 0;
    }

    .dashboard-header {
      grid-template-columns: 1fr;
      gap: 7px;
      height: auto;
      padding: 10px 12px;
    }

    .header-actions {
      justify-content: flex-start;
      overflow-x: auto;
    }

    .dashboard-layout {
      grid-template-columns: 1fr;
      grid-template-rows: auto minmax(0, 1fr);
      height: calc(100% - 104px);
    }

    .tab-rail {
      display: block;
      overflow-x: auto;
      padding: 7px;
      border-right: 0;
      border-bottom: 1px solid var(--baritone-divider);
    }

    .tab-rail [role="tablist"] {
      display: flex;
      width: max-content;
    }

    .tab-rail [role="tab"] {
      grid-template-columns: auto;
      min-width: max-content;
      padding: 0 10px;
    }

    .tab-icon,
    .rail-footer {
      display: none;
    }

    .content-panel {
      padding: 16px 12px 24px;
    }

    .overview-grid,
    .waypoint-form {
      grid-template-columns: 1fr;
    }

    .recent-log li,
    .console-window > div {
      grid-template-columns: 52px minmax(0, 1fr);
    }

    .recent-log li span,
    .console-window > div span {
      grid-column: 1 / -1;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .dashboard-shell,
    .busy-state span {
      animation: none;
    }

    button,
    .progress-track span {
      transition: none;
    }

    button:hover:not(:disabled) {
      transform: none;
    }
  }
</style>
