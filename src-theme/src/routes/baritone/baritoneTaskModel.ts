import type {BaritoneTaskRequest, BaritoneTaskType} from "../../integration/baritone";

export type BaritoneComposerKind = "navigate" | "mine" | "follow" | "farm" | "explore" | "build" | "elytra";

export interface BaritoneComposerForm {
    x: string;
    y: string;
    z: string;
    block: string;
    player: string;
    count: string;
    radius: string;
    file: string;
    navigateTarget: "coordinates" | "block";
}

export function buildBaritoneTaskRequest(
    kind: BaritoneComposerKind,
    form: BaritoneComposerForm,
): BaritoneTaskRequest {
    switch (kind) {
        case "navigate":
            return form.navigateTarget === "block"
                ? {type: "GET_TO_BLOCK", block: required(form.block, "Block")}
                : coordinateRequest("GOTO", form);
        case "mine":
            return {type: "MINE", block: required(form.block, "Block"), count: positiveInteger(form.count, "Count")};
        case "follow":
            return {type: "FOLLOW", player: required(form.player, "Player")};
        case "farm":
            return {type: "FARM", radius: positiveNumber(form.radius, "Radius")};
        case "explore":
            return {type: "EXPLORE", x: finiteNumber(form.x, "X"), z: finiteNumber(form.z, "Z"), radius: positiveNumber(form.radius, "Radius")};
        case "build":
            return {
                type: "BUILD",
                file: required(form.file, "Schematic"),
                x: finiteNumber(form.x, "X"),
                y: finiteNumber(form.y, "Y"),
                z: finiteNumber(form.z, "Z"),
            };
        case "elytra":
            return coordinateRequest("ELYTRA", form);
    }
}

function coordinateRequest(type: BaritoneTaskType, form: BaritoneComposerForm): BaritoneTaskRequest {
    return {
        type,
        x: finiteNumber(form.x, "X"),
        y: finiteNumber(form.y, "Y"),
        z: finiteNumber(form.z, "Z"),
    };
}

function finiteNumber(value: string, field: string): number {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
    throw new Error(`${field} must be a finite number.`);
}

function positiveNumber(value: string, field: string): number {
    const parsed = finiteNumber(value, field);
    if (parsed > 0) return parsed;
    throw new Error(`${field} must be greater than zero.`);
}

function positiveInteger(value: string, field: string): number {
    const parsed = positiveNumber(value, field);
    if (Number.isInteger(parsed)) return parsed;
    throw new Error(`${field} must be a whole number.`);
}

function required(value: string, field: string): string {
    const trimmed = value.trim();
    if (trimmed) return trimmed;
    throw new Error(`${field} is required.`);
}

export function composerHeading(value: BaritoneComposerKind): string {
    return ({
        navigate: "Choose a destination",
        mine: "Mine a block",
        follow: "Follow a player",
        farm: "Maintain an area",
        explore: "Explore from a center",
        build: "Build a schematic",
        elytra: "Elytra pathing",
    })[value];
}

export function composerDescription(value: BaritoneComposerKind): string {
    return ({
        navigate: "Path to coordinates or the nearest matching block.",
        mine: "Find and collect a target block up to the requested count.",
        follow: "Keep a safe route to a named player as they move.",
        farm: "Harvest and replant Baritone-supported crops inside the radius.",
        explore: "Generate terrain outward from the selected center.",
        build: "Load a supported schematic from Baritone's schematics directory.",
        elytra: "Calculate a long-distance Elytra route to exact coordinates.",
    })[value];
}

export function describeComposerError(error: unknown): string {
    return error instanceof Error && error.message ? error.message : "Unable to create the task.";
}
