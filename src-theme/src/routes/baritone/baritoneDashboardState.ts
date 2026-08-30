import type {BaritoneRestClient, BaritoneWaypointRequest} from "../../integration/baritone";
import {coordinateValue} from "./baritoneDashboardPresentation.ts";

export interface BaritoneDashboardFields {
    settingQuery: string;
    waypointName: string;
    waypointTag: string;
    waypointX: string;
    waypointY: string;
    waypointZ: string;
    consoleInput: string;
    consoleOutput: string | null;
    completions: string[];
}

export function createBaritoneDashboardFields(): BaritoneDashboardFields {
    return {
        settingQuery: "",
        waypointName: "",
        waypointTag: "USER",
        waypointX: "0",
        waypointY: "64",
        waypointZ: "0",
        consoleInput: "",
        consoleOutput: null,
        completions: [],
    };
}

export function waypointRequest(fields: BaritoneDashboardFields): BaritoneWaypointRequest {
    const name = fields.waypointName.trim();
    if (!name) {
        throw new Error("Waypoint name is required.");
    }
    return {
        name,
        tag: fields.waypointTag.trim() || undefined,
        x: coordinateValue(fields.waypointX, "X"),
        y: coordinateValue(fields.waypointY, "Y"),
        z: coordinateValue(fields.waypointZ, "Z"),
    };
}

export function createCompletionController(
    client: Pick<BaritoneRestClient, "completions">,
    onChange: (completions: string[]) => void,
) {
    let revision = 0;
    let timer: number | undefined;

    const update = (value: string) => {
        onChange([]);
        const requestedRevision = ++revision;
        if (timer !== undefined) window.clearTimeout(timer);
        if (!value.trim()) return;
        timer = window.setTimeout(async () => {
            try {
                const completions = await client.completions(value);
                if (requestedRevision === revision) onChange(completions.slice(0, 8));
            } catch {
                if (requestedRevision === revision) onChange([]);
            }
        }, 140);
    };

    return {
        update,
        stop: () => {
            revision += 1;
            if (timer !== undefined) window.clearTimeout(timer);
        },
    };
}
