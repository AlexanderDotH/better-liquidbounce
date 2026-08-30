<script lang="ts">
    import type {
        BaritoneWaypoint,
        BaritoneWaypointRequest,
    } from "../../integration/baritone";
    import BaritoneField from "./BaritoneField.svelte";
    import {
        type BaritoneDashboardFields,
        waypointRequest,
    } from "./baritoneDashboardState.ts";

    let {fields, waypoints, canStartTask, busy, nativeTextInput, onAdd, onDelete, onNavigate, onError} = $props<{
        fields: BaritoneDashboardFields;
        waypoints: BaritoneWaypoint[];
        canStartTask: boolean;
        busy: boolean;
        nativeTextInput: boolean;
        onAdd: (request: BaritoneWaypointRequest) => Promise<boolean>;
        onDelete: (waypoint: BaritoneWaypoint) => Promise<void>;
        onNavigate: (waypoint: BaritoneWaypoint) => Promise<void>;
        onError: (message: string) => void;
    }>();

    async function addWaypoint(): Promise<void> {
        try {
            if (await onAdd(waypointRequest(fields))) fields.waypointName = "";
        } catch (error) {
            onError(error instanceof Error ? error.message : "Waypoint values are invalid.");
        }
    }
</script>

<section class="waypoint-layout" aria-labelledby="waypoint-heading">
    <div class="section-heading">
        <div>
            <h3 id="waypoint-heading">Saved waypoints</h3>
            <p>Upstream-compatible waypoints remain attached to their world and dimension.</p>
        </div>
        <span>{waypoints.length} saved</span>
    </div>
    <div class="waypoint-form">
        <BaritoneField id="baritone-waypoint-name" label="Name" value={fields.waypointName} onValueChange={value => fields.waypointName = value} placeholder="Base entrance" disabled={!canStartTask} {nativeTextInput}/>
        <BaritoneField id="baritone-waypoint-tag" label="Tag" value={fields.waypointTag} onValueChange={value => fields.waypointTag = value} placeholder="USER" disabled={!canStartTask} {nativeTextInput}/>
        <BaritoneField id="baritone-waypoint-x" label="X" value={fields.waypointX} onValueChange={value => fields.waypointX = value} inputMode="decimal" disabled={!canStartTask} {nativeTextInput}/>
        <BaritoneField id="baritone-waypoint-y" label="Y" value={fields.waypointY} onValueChange={value => fields.waypointY = value} inputMode="decimal" disabled={!canStartTask} {nativeTextInput}/>
        <BaritoneField id="baritone-waypoint-z" label="Z" value={fields.waypointZ} onValueChange={value => fields.waypointZ = value} inputMode="decimal" disabled={!canStartTask} {nativeTextInput}/>
        <button class="primary" type="button" disabled={!canStartTask || busy} onclick={addWaypoint}>Add waypoint</button>
    </div>
    {#if waypoints.length}
        <div class="waypoint-list">
            {#each waypoints as waypoint (waypoint.id)}
                <article>
                    <div class="waypoint-marker" aria-hidden="true"></div>
                    <div class="waypoint-copy">
                        <strong>{waypoint.name}</strong>
                        <span>{waypoint.tag ?? "USER"} · {waypoint.position.x}, {waypoint.position.y}, {waypoint.position.z}</span>
                    </div>
                    <button type="button" disabled={!canStartTask || busy} onclick={() => onNavigate(waypoint)}>Go</button>
                    <button class="danger-text" type="button" disabled={busy} onclick={() => onDelete(waypoint)}>Delete</button>
                </article>
            {/each}
        </div>
    {:else}
        <p class="empty-copy">No waypoints are saved for this world.</p>
    {/if}
</section>
