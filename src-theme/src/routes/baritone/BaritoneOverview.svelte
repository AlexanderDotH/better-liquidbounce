<script lang="ts">
    import type {BaritoneRoute, BaritoneSnapshot} from "../../integration/baritone";
    import {
        baritoneEtaLabel,
        baritoneProgressPercent,
        locomotionLabel,
        navigationPhaseLabel,
        ownershipLabel,
        routeDescription,
        routeHeading,
    } from "./baritoneDashboardPresentation.ts";
    import BaritoneRouteMap from "./BaritoneRouteMap.svelte";

    let {snapshot, route, onOpenConsole} = $props<{
        snapshot: BaritoneSnapshot;
        route: BaritoneRoute;
        onOpenConsole: () => void;
    }>();
    let navigation = $derived(snapshot.navigation!);
    let progress = $derived(baritoneProgressPercent(snapshot.progress));
</script>

<div class="overview-grid">
    <section class="metric-card task-card" aria-labelledby="active-task-heading">
        <p>Active task</p><h3 id="active-task-heading">{snapshot.task?.label ?? "No active task"}</h3>
        <span>{snapshot.task?.details ?? (snapshot.task ? snapshot.task.type : "Choose a workflow to begin.")}</span>
    </section>
    <section class="metric-card">
        <p>ETA</p><h3>{baritoneEtaLabel(snapshot.etaSeconds)}</h3>
        <span>{navigation.active === "FLY" && snapshot.etaSeconds === null ? "Unavailable for the active Fly mode" : snapshot.status === "CALCULATING" ? "Calculating route" : "Estimated remaining time"}</span>
    </section>
    <section class="metric-card">
        <p>Progress</p><h3>{progress}%</h3>
        <div class="progress-track" aria-label={`${progress} percent complete`} role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={progress}>
            <span style:width={`${progress}%`}></span>
        </div>
    </section>
    <section class="metric-card">
        <p>Navigation mode</p><h3>{locomotionLabel(navigation.requested)}</h3>
        <span>Active: {locomotionLabel(navigation.active)}{navigation.flyMode ? ` · ${navigation.flyMode}` : ""}</span>
    </section>
    <section class="metric-card">
        <p>Navigation state</p><h3>{navigationPhaseLabel(navigation.phase)}</h3>
        <span class="navigation-detail">{navigation.detail ?? snapshot.pauseReason ?? "Navigation is ready."}</span>
    </section>
    <section class="metric-card">
        <p>Movement ownership</p><h3>{ownershipLabel(navigation.ownership, navigation.active)}</h3>
        <span>{snapshot.pauseReason ?? (navigation.ownership === "USER" ? "The enabled Fly module remains user-controlled." : "No higher-priority movement owner detected.")}</span>
    </section>
    <section class="metric-card">
        <p>Retries remaining</p><h3>{navigation.restartsRemaining}</h3><span>Shared Fly restart and walking retry budget.</span>
    </section>
</div>

<section class="route-section" aria-labelledby="route-heading">
    <div class="section-heading">
        <div><h3 id="route-heading">{routeHeading(navigation.active)}</h3><p>{routeDescription(navigation.active)}</p></div>
        <span>{route.points.length} nodes</span>
    </div>
    <BaritoneRouteMap points={route.points}/>
</section>

<section class="recent-log" aria-labelledby="recent-log-heading">
    <div class="section-heading">
        <div><h3 id="recent-log-heading">Recent activity</h3><p>Baritone status, warnings, and task results.</p></div>
        <button type="button" onclick={onOpenConsole}>Open console</button>
    </div>
    {#if snapshot.logs.length}
        <ol>
            {#each snapshot.logs.slice(-5).reverse() as entry (entry.revision)}
                <li class:error-log={entry.level === "ERROR"}><time>{entry.timestamp}</time><strong>{entry.level}</strong><span>{entry.message}</span></li>
            {/each}
        </ol>
    {:else}
        <p class="empty-copy">No Baritone activity has been reported.</p>
    {/if}
</section>
