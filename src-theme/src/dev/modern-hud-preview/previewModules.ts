import type {ConfigurableSetting, Module} from "../../integration/types";

export function createModules(): Module[] {
    const definitions: Array<[
        name: string,
        category: string,
        enabled: boolean,
        tag: string | null,
        key: string,
    ]> = [
        ["KillAura", "Combat", true, "Switch", "key.keyboard.r"],
        ["Criticals", "Combat", true, null, "key.keyboard.unknown"],
        ["Speed", "Movement", true, "Watchdog", "key.keyboard.v"],
        ["Flight", "Movement", false, "Vanilla", "key.keyboard.f"],
        ["AutoArmor", "Player", true, null, "key.keyboard.g"],
        ["InventoryCleaner", "Player", false, null, "key.keyboard.unknown"],
        ["Scaffold", "World", true, "Normal", "key.keyboard.b"],
        ["Nuker", "World", false, null, "key.keyboard.unknown"],
        ["ESP", "Render", true, "Glow", "key.keyboard.x"],
        ["Fullbright", "Render", true, null, "key.keyboard.unknown"],
        ["Disabler", "Exploit", false, "Verus", "key.keyboard.unknown"],
        ["Derp", "Fun", false, null, "key.keyboard.unknown"],
        ["AutoChat", "Misc", false, null, "key.keyboard.unknown"],
    ];

    return definitions.map(([name, category, enabled, tag, boundKey]) => ({
        name,
        category,
        keyBind: {
            boundKey,
            action: "Toggle",
            modifiers: [],
        },
        enabled,
        description: `${name} deterministic Modern HUD preview module.`,
        hasSettings: true,
        hidden: false,
        aliases: [],
        tag,
    }));
}

export function createHudSettings(): ConfigurableSetting {
    return {
        name: "HUD",
        valueType: "CONFIGURABLE",
        value: [
            {
                name: "Theme",
                valueType: "CHOOSE",
                value: "Modern",
                choices: ["Classic", "Modern"],
                description: "Selects the in-game HUD presentation.",
                key: "preview.hud.theme",
            },
        ],
        description: "Deterministic Modern HUD preview settings.",
        key: "preview.hud",
    };
}
