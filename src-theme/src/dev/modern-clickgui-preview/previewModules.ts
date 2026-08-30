import type {ConfigurableSetting, Module as ClickGuiModule} from "../../integration/types";
import {booleanSetting, chooseSetting, configurable, floatSetting, intRangeSetting, intSetting, togglable} from "./previewSettingFactories.ts";
import {bindSetting, choiceModes, choiceSetting, merchantReachSetting, merchantTradeFiltersSetting, multiChooseSetting, mutableListSetting, registryListSetting, textSetting} from "./previewCompositeSettings.ts";

type PreviewModuleDefinition = [
    name: string,
    category: string,
    enabled: boolean,
    aliases?: string[],
];

const PREVIEW_MODULE_DEFINITIONS: PreviewModuleDefinition[] = [
    ["KillAura", "Combat", true, ["ForceField"]],
    ["Criticals", "Combat", false],
    ["Speed", "Movement", true, ["BHop"]],
    ["Flight", "Movement", false, ["Fly"]],
    ["AutoArmor", "Player", true],
    ["AutoShop", "Player", true, ["AutoTrade"]],
    ["InventoryCleaner", "Player", false, ["InvCleaner"]],
    ["Scaffold", "World", false, ["BlockFly"]],
    ["Nuker", "World", false],
    ["ESP", "Render", true, ["WallHack"]],
    ["Fullbright", "Render", true],
    ["Disabler", "Exploit", false],
    ["Blink", "Exploit", false],
    ["Derp", "Fun", false],
    ["SkinDerp", "Fun", true],
    ["AutoChat", "Misc", false],
    ["NameProtect", "Misc", true],
];

export function createPreviewModules(): ClickGuiModule[] {
    return PREVIEW_MODULE_DEFINITIONS.map(([name, category, enabled, aliases = []]) => ({
        name,
        category,
        keyBind: {
            boundKey: "key.keyboard.unknown",
            action: "Toggle",
            modifiers: [],
        },
        enabled,
        description: previewDescription(name),
        hasSettings: true,
        hidden: false,
        aliases,
        tag: null,
    }));
}

function previewDescription(name: string): string {
    const descriptions: Record<string, string> = {
        KillAura: "Automatically attacks nearby valid targets using configurable rotations.",
        AutoShop: "Trades through server shops or nearby vanilla merchants using configured item filters.",
        Speed: "Improves movement speed with server-aware modes.",
        Scaffold: "Places blocks below the player while moving.",
        ESP: "Highlights entities through world geometry.",
    };

    return descriptions[name] ?? `${name} preview module with representative settings and state.`;
}

export function createComprehensiveModuleSettings(): ConfigurableSetting {
    return configurable("KillAura", [
        booleanSetting("AutoBlock", true, "Blocks between attacks when possible."),
        floatSetting("Range", 3.65, 1, 6, "m"),
        intSetting("SwitchDelay", 180, 0, 1000, "ms"),
        chooseSetting("Priority", "Distance", ["Distance", "Health", "Angle"]),
        choiceSetting("AttackPattern"),
        multiChooseSetting("Targets", ["Players", "Mobs"], ["Players", "Mobs", "Animals"]),
        textSetting("TargetName", "PreviewPlayer"),
        bindSetting("Bind", "key.keyboard.r"),
        togglable("Rotations", [
            booleanSetting("Enabled", true),
            floatSetting("TurnSpeed", 84, 10, 180, "°"),
            booleanSetting("Silent", true),
        ]),
        configurable("Targeting", [
            booleanSetting("ThroughWalls", false),
            intRangeSetting("HurtTime", 0, 8, 0, 10),
        ]),
        mutableListSetting("IgnoredNames", ["FriendOne", "FriendTwo"]),
        registryListSetting(
            "Blocks",
            ["minecraft:diamond_ore", "minecraft:ancient_debris"],
            "minecraft_blocks",
        ),
    ], "Representative fixture for every common ClickGUI control.");
}

export function createDefaultModuleSettings(name: string): ConfigurableSetting {
    return configurable(name, [
        booleanSetting("EnabledInInventory", name.length % 2 === 0),
        bindSetting("Bind", "key.keyboard.unknown"),
        booleanSetting("Hidden", false),
    ]);
}

export function createAutoShopSettings(): ConfigurableSetting {
    const serverShop = configurable("ServerShop", [
        chooseSetting("Config", "HypixelBedWars", ["HypixelBedWars", "CubeCraft"]),
        intRangeSetting("StartDelay", 1, 3, 0, 20),
        choiceModes("PurchaseMode", "Normal", {
            Normal: configurable("Normal", []),
            Quick: configurable("Quick", []),
        }),
        intSetting("ExtraCategorySwitchDelay", 2, 0, 20, " ticks"),
        booleanSetting("AutoClose", true),
    ]);
    const rotations = configurable("Rotations", [
        choiceModes("AngleSmooth", "Linear", {
            Linear: configurable("Linear", [floatSetting("TurnSpeed", 90, 10, 180, "°")]),
            Sigmoid: configurable("Sigmoid", [floatSetting("Steepness", 8, 1, 20, "")]),
        }),
        chooseSetting("MovementCorrection", "Silent", ["None", "Silent", "Strict"]),
        floatSetting("ResetThreshold", 2, 1, 180, "°"),
        intSetting("TicksUntilReset", 5, 1, 30, " ticks"),
    ]);
    const vanilla = configurable("Vanilla", [
        merchantTradeFiltersSetting(),
        merchantReachSetting(),
        intRangeSetting("CPS", 4, 8, 1, 20),
        rotations,
    ]);

    return configurable("AutoShop", [
        choiceModes("Mode", "Vanilla", {ServerShop: serverShop, Vanilla: vanilla}),
        bindSetting("Bind", "key.keyboard.unknown"),
        booleanSetting("Hidden", false),
    ], "Expanded vanilla merchant-trading fixture with persistent nested settings.");
}

export function createClickGuiSettings(): ConfigurableSetting {
    return configurable("ClickGUI", [
        floatSetting("Scale", 1, 0.5, 2, "x"),
        chooseSetting("Theme", "Modern", ["Classic", "Modern"]),
        booleanSetting("SearchBarAutoFocus", false),
        togglable("Snapping", [
            booleanSetting("Enabled", true),
            intSetting("GridSize", 10, 4, 32, "px"),
        ]),
    ]);
}

export function createGlobalSettings(): ConfigurableSetting {
    return configurable("Global", [
        configurable("General", [
            booleanSetting("Commands", true),
            textSetting("CommandPrefix", "."),
            chooseSetting("Language", "English", ["English", "German", "Spanish"]),
            multiChooseSetting(
                "Notifications",
                ["Modules", "Warnings"],
                ["Modules", "Warnings", "Chat"],
            ),
        ]),
        configurable("Combat", [
            booleanSetting(
                "DelegateKillAuraAttacks",
                false,
                "Allows KillAura to delegate attacks beyond normal reach to enabled SpearKill or SuperHit modules.",
            ),
        ]),
        togglable("DiscordRPC", [
            booleanSetting("Enabled", true),
            textSetting("Details", "Modern ClickGUI preview"),
            booleanSetting("ShowServer", false),
        ]),
    ]);
}
