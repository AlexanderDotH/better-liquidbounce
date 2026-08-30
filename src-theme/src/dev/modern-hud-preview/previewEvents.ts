import type {ContextualBarData} from "../../integration/types";
import type {ModernHudPreviewServerEvent, ModernHudPreviewState} from "./previewState";
import {clone} from "./previewHttp.ts";
import {text} from "./previewPlayer.ts";

export function createModernHudPreviewSnapshotEvents(
    state: ModernHudPreviewState,
): ModernHudPreviewServerEvent[] {
    return clone([...playerSnapshotEvents(state), ...staticSnapshotEvents()]);
}

function playerSnapshotEvents(state: ModernHudPreviewState): ModernHudPreviewServerEvent[] {
    return [
        {
            name: "clientPlayerData",
            event: {playerData: state.player},
        },
        {
            name: "contextualBar",
            event: {contextualBar: state.contextualBar},
        },
        {
            name: "clientPlayerEffect",
            event: {effects: state.player.effects},
        },
        {
            name: "clientPlayerInventory",
            event: {inventory: state.inventory},
        },
        {
            name: "targetChange",
            event: {target: state.target},
        },
    ];
}

function staticSnapshotEvents(): ModernHudPreviewServerEvent[] {
    return [
        {
            name: "blockCountChange",
            event: {nextBlock: "minecraft:wool", count: 128},
        },
        {
            name: "notification",
            event: {
                title: "Module enabled",
                message: "Speed",
                severity: "ENABLED",
            },
        },
        {
            name: "key",
            event: {key: "key.keyboard.w", action: 1},
        },
        {
            name: "overlayMessage",
            event: {
                text: text("Modern HUD preview", "aqua"),
                tinted: false,
            },
        },
    ];
}

const CONTEXTUAL_BAR: ContextualBarData = {
    mode: "locator",
    progress: 0,
    level: 0,
    cooldown: false,
    markers: [
        {
            id: "00000000-0000-4000-a000-000000000002",
            label: "PreviewTarget",
            offset: -0.34,
            elevation: "above",
            distance: 42,
            color: 0x7897D6,
            kind: "player",
            playerUuid: "00000000-0000-4000-a000-000000000002",
            style: "minecraft:default",
        },
        {
            id: "preview:home",
            label: "Home",
            offset: 0.26,
            elevation: "level",
            distance: 118,
            color: 0x64C6B0,
            kind: "waypoint",
            playerUuid: null,
            style: "minecraft:default",
        },
        {
            id: "preview:party",
            label: "Party",
            offset: 0.72,
            elevation: "below",
            distance: 306,
            color: 0xD88FC2,
            kind: "waypoint",
            playerUuid: null,
            style: "minecraft:bowtie",
        },
    ],
};

export function createContextualBar(): ContextualBarData {
    return clone(CONTEXTUAL_BAR);
}
