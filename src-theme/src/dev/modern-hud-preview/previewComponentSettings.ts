const COMPONENT_SETTINGS: Record<string, Record<string, unknown>> = {
    Watermark: positioned("Left", 20, "Top", 20),
    TabGui: positioned("Left", 190, "Top", 20),
    ArrayList: {
        ...positioned("Right", 20, "Top", 20),
        showTags: true,
        itemAlignment: "Right",
        order: "Descending",
    },
    Notifications: {
        ...positioned("Right", 20, "Bottom", 188),
        severities: ["INFO", "SUCCESS", "ERROR", "ENABLED", "DISABLED"],
    },
    Hotbar: positioned("CenterTranslated", 0, "Bottom", 18),
    Scoreboard: {
        ...positioned("Right", 20, "Top", 270),
        show: ["Header", "Name", "Score"],
    },
    ArmorItems: positioned("CenterTranslated", 96, "Bottom", 34),
    InventoryStatistics: {
        ...positioned("CenterTranslated", -96, "Bottom", 34),
        items: [
            "minecraft:emerald",
            "minecraft:diamond",
            "minecraft:gold_ingot",
            "minecraft:iron_ingot",
        ],
        showEmpty: true,
        rowLength: 1,
    },
    Inventory: positioned("Left", 20, "Top", 90),
    CraftingInventory: positioned("CenterTranslated", 0, "Top", 90),
    EnderChestInventory: positioned("Right", 20, "Top", 90),
    TargetHud: positioned("CenterTranslated", 0, "CenterTranslated", -78),
    BlockCounter: {
        ...positioned("CenterTranslated", 0, "CenterTranslated", 26),
        iconPosition: "Left",
    },
    Effects: positioned("Right", 20, "Bottom", 20),
    Keystrokes: positioned("Left", 20, "Bottom", 20),
    Taco: positioned("Left", 202, "Bottom", 20),
    Image: {
        ...positioned("CenterTranslated", 0, "Top", 28),
        uRL: "/img/menu/icon-liquidbounce.svg",
        scale: 0.72,
    },
    Text: {
        ...positioned("Left", 20, "Top", 76),
        text: "Text",
        container: "Plain",
        color: 4294967295,
        font: "Inter",
        size: 12,
        decorations: {
            enabled: true,
            bold: false,
            italic: false,
            underline: false,
            strikethrough: false,
        },
        shadow: {
            enabled: true,
            offsetX: 0,
            offsetY: 1,
            blurRadius: 3,
            color: 4278190080,
        },
        glow: {
            enabled: false,
            radius: 0,
            color: 4294967295,
        },
    },
    Coordinates: positioned("Left", 15, "Top", 60),
    KeyBinds: positioned("Left", 20, "Top", 106),
};

export function createComponentSettingsByName(): Record<string, Record<string, unknown>> {
    return structuredClone(COMPONENT_SETTINGS);
}

function positioned(
    horizontalAlignment: string,
    horizontalOffset: number,
    verticalAlignment: string,
    verticalOffset: number,
): Record<string, unknown> {
    return {
        alignment: {
            horizontalAlignment,
            horizontalOffset,
            verticalAlignment,
            verticalOffset,
        },
    };
}
