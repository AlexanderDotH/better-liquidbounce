<script lang="ts">
    import type {BaritoneControlAction, BaritoneSnapshot} from "../../integration/baritone";
    import {baritoneStatusLabel} from "./baritoneDashboardPresentation.ts";

    let {snapshot, loading, busyAction, onControl, onRefresh} = $props<{
        snapshot: BaritoneSnapshot;
        loading: boolean;
        busyAction: string | null;
        onControl: (action: BaritoneControlAction) => Promise<void>;
        onRefresh: () => Promise<void>;
    }>();

    let hasTask = $derived(snapshot.task !== null);
</script>

<header class="dashboard-header">
    <div class="brand">
        <div class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M4 17.5 9.4 12l3.1 3.1L20 7.5M4 6.5h5M4 10h3"/></svg>
        </div>
        <div><p>LiquidBounce navigation</p><h1>Baritone</h1></div>
    </div>
    <div class="header-status" aria-live="polite">
        <span
                class:warning={snapshot.status === "PAUSED"}
                class:error={snapshot.status === "FAILED" || snapshot.status === "UNAVAILABLE"}
                class:success={snapshot.status === "ARRIVED"}
        ></span>
        <div><small>Current state</small><strong>{loading ? "Connecting…" : baritoneStatusLabel(snapshot.status)}</strong></div>
    </div>
    <div class="header-actions">
        <button type="button" disabled={busyAction !== null || !hasTask || snapshot.status === "PAUSED"} onclick={() => onControl("PAUSE")}>Pause</button>
        <button type="button" disabled={busyAction !== null || snapshot.status !== "PAUSED"} onclick={() => onControl("RESUME")}>Resume</button>
        <button class="danger" type="button" disabled={busyAction !== null || !hasTask} onclick={() => onControl("CANCEL")}>Cancel</button>
        <button class="icon-button" type="button" aria-label="Refresh Baritone state" disabled={busyAction !== null || loading} onclick={onRefresh}>
            <svg aria-hidden="true" viewBox="0 0 16 16"><path d="M13.2 5.7A5.5 5.5 0 1 0 13 10m.2-4.3V2.8m0 2.9h-3"/></svg>
        </button>
    </div>
</header>
