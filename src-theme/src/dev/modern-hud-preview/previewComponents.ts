import type {ConfigurableSetting, HudComponent, Metadata, Module} from "../../integration/types";
import type {ModernHudPreviewFixture} from "./previewState";
import {createComponentSettingsByName} from "./previewComponentSettings.ts";

const SHOWCASE_COMPONENTS = new Set([
    "Watermark",
    "Coordinates",
    "KeyBinds",
    "TabGui",
    "ArrayList",
    "Notifications",
    "Hotbar",
    "Scoreboard",
    "TargetHud",
    "Effects",
]);

const INVENTORY_COMPONENTS = new Set([
    "ArmorItems",
    "InventoryStatistics",
    "Inventory",
    "CraftingInventory",
    "EnderChestInventory",
]);

const COMPONENT_NAMES = [
        "Watermark",
        "TabGui",
        "ArrayList",
        "Notifications",
        "Hotbar",
        "Scoreboard",
        "ArmorItems",
        "InventoryStatistics",
        "Inventory",
        "CraftingInventory",
        "EnderChestInventory",
        "TargetHud",
        "BlockCounter",
        "Effects",
        "Keystrokes",
        "Taco",
        "Image",
        "Text",
        "Coordinates",
        "KeyBinds",
];

export function createMetadata(): Metadata {
    return {
        id: "liquidbounce",
        name: "LiquidBounce",
        version: "preview",
        authors: ["CCBlueX"],
        colors: {
            Accent: "#7897d6",
            Tint: "#090b0f",
        },
        screens: [],
        overlays: ["hud"],
        components: [...COMPONENT_NAMES],
        fonts: [
            "Inter-Bold.ttf",
            "Inter-Medium.ttf",
            "Inter-Regular.ttf",
        ],
        backgrounds: [],
    };
}

export function createComponents(
    names: string[],
    fixture: ModernHudPreviewFixture,
): HudComponent[] {
    const settingsByName = createComponentSettingsByName();

    return names.map((name, index) => ({
        name,
        description: `${name} deterministic Modern HUD preview component.`,
        id: `preview-${index + 1}-${name.toLowerCase()}`,
        settings: {
            enabled: isComponentEnabled(name, fixture),
            ...settingsByName[name],
        },
    }));
}

function isComponentEnabled(
    name: string,
    fixture: ModernHudPreviewFixture,
): boolean {
    const enabledComponents = fixture === "inventory"
        ? INVENTORY_COMPONENTS
        : SHOWCASE_COMPONENTS;

    return enabledComponents.has(name);
}

export function previewColor(value: string): string {
    let hash = 0;
    for (const character of value) {
        hash = ((hash << 5) - hash + character.charCodeAt(0)) | 0;
    }

    return `hsl(${Math.abs(hash) % 360} 48% 48%)`;
}
