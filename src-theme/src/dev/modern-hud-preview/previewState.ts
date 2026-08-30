import type {ClientInfo, ConfigurableSetting, ContextualBarData, GameWindow, HudComponent, Metadata, MinecraftKeybind, Module, PlayerData} from "../../integration/types";
import type {PlayerInventory} from "../../integration/events";
import {createComponents, createMetadata} from "./previewComponents.ts";
import {createHudSettings, createModules} from "./previewModules.ts";
import {createClientInfo, createGameWindow, createKeybinds} from "./previewEnvironment.ts";
import {createContextualBar} from "./previewEvents.ts";
import {clone} from "./previewHttp.ts";
import {createInventory, createPlayer, createTarget} from "./previewPlayer.ts";

export interface ModernHudPreviewRequestRecord {
    method: string;
    path: string;
}

export interface ModernHudPreviewServerEvent {
    name: string;
    event: unknown;
}

export type ModernHudPreviewFixture = "showcase" | "inventory";

export interface ModernHudPreviewState {
    fixture: ModernHudPreviewFixture;
    metadata: Metadata;
    components: HudComponent[];
    contextualBar: ContextualBarData;
    modules: Module[];
    hudSettings: ConfigurableSetting;
    player: PlayerData;
    target: PlayerData;
    inventory: PlayerInventory;
    keybinds: MinecraftKeybind[];
    clientInfo: ClientInfo;
    gameWindow: GameWindow;
    requests: ModernHudPreviewRequestRecord[];
}
export function resolveModernHudPreviewFixture(
    searchParams: URLSearchParams,
): ModernHudPreviewFixture {
    return searchParams.get("fixture") === "inventory"
        ? "inventory"
        : "showcase";
}

export function createModernHudPreviewState(
    fixture: ModernHudPreviewFixture = "showcase",
): ModernHudPreviewState {
    const metadata = createMetadata();
    const inventory = createInventory();
    const player = createPlayer("PreviewPlayer", "00000000-0000-4000-a000-000000000001");

    player.mainHandStack = clone(inventory.main[0]);
    player.armorItems = clone(inventory.armor);

    return {
        fixture,
        metadata,
        components: createComponents(metadata.components, fixture),
        contextualBar: createContextualBar(),
        modules: createModules(),
        hudSettings: createHudSettings(),
        player,
        target: createTarget(),
        inventory,
        keybinds: createKeybinds(),
        clientInfo: createClientInfo(),
        gameWindow: createGameWindow(),
        requests: [],
    };
}
