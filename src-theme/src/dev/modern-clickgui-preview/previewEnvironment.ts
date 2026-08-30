import type {PersistentStorageItem} from "../../integration/types";

const INITIAL_PERSISTENT_ITEMS: PersistentStorageItem[] = [
        {
            key: "clickgui.panel.Player",
            value: JSON.stringify({
                left: 20,
                top: 84,
                expanded: true,
                scrollTop: 0,
                zIndex: 4,
            }),
        },
        {key: "clickgui.AutoShop", value: "true"},
        {key: "clickgui.AutoShop.Mode", value: "true"},
        {key: "clickgui.AutoShop.Mode.Rotations", value: "true"},
        {
            key: "clickgui.modern.panel.v1.Combat",
            value: JSON.stringify({
                left: 20,
                top: 84,
                expanded: true,
                scrollTop: 0,
                zIndex: 3,
            }),
        },
        {
            key: "clickgui.modern.panel.v1.Player",
            value: JSON.stringify({
                left: 322,
                top: 84,
                expanded: true,
                scrollTop: 0,
                zIndex: 4,
            }),
        },
        {key: "clickgui.modern.module.v1.KillAura", value: "true"},
        {key: "clickgui.modern.module.v1.KillAura.AttackPattern", value: "true"},
        {key: "clickgui.modern.module.v1.KillAura.Targets", value: "true"},
        {key: "clickgui.modern.module.v1.KillAura.Rotations", value: "true"},
        {key: "clickgui.modern.module.v1.KillAura.Targeting", value: "true"},
        {key: "clickgui.modern.module.v1.KillAura.Blocks", value: "true"},
        {key: "clickgui.modern.module.v1.AutoShop", value: "true"},
        {key: "clickgui.modern.module.v1.AutoShop.Mode", value: "true"},
        {key: "clickgui.modern.module.v1.AutoShop.Mode.Rotations", value: "true"},
];

export function createInitialPersistentItems(): PersistentStorageItem[] {
    return structuredClone(INITIAL_PERSISTENT_ITEMS);
}

export function createClientInfo() {
    return {
        os: "linux",
        gameVersion: "1.21.1",
        clientVersion: "preview",
        clientName: "LiquidBounce",
        development: true,
        fps: 144,
        gameDir: "/home/alex/.minecraft",
        clientDir: "/home/alex/.liquidbounce",
        inGame: true,
        viaFabricPlus: false,
        hasProtocolHack: false,
    };
}

export function createGameWindow() {
    return {
        width: 1440,
        height: 900,
        scaledWidth: 720,
        scaledHeight: 450,
        scaleFactor: 2,
        guiScale: 2,
    };
}

export function createRegistryItems() {
    return {
        "minecraft:emerald": previewRegistryItem("Emerald", "#35c96f", "#c9ffe0"),
        "minecraft:paper": previewRegistryItem("Paper", "#e8eadf", "#9ca49b"),
        "minecraft:wheat": previewRegistryItem("Wheat", "#e0b84f", "#fff0a3"),
        "minecraft:coal": previewRegistryItem("Coal", "#30343a", "#777e87"),
        "minecraft:iron_ingot": previewRegistryItem("Iron Ingot", "#d8d8d2", "#ffffff"),
        "minecraft:bread": previewRegistryItem("Bread", "#bd792f", "#f2c268"),
        "minecraft:book": previewRegistryItem("Book", "#765036", "#ece3c1"),
        "minecraft:bookshelf": previewRegistryItem("Bookshelf", "#9b6638", "#dfc386"),
        "minecraft:enchanted_book": previewRegistryItem("Enchanted Book", "#8d4fb8", "#f5c4ff"),
        "minecraft:diamond_ore": previewRegistryItem("Diamond Ore", "#68757b", "#50d6cf"),
        "minecraft:ancient_debris": previewRegistryItem("Ancient Debris", "#68473f", "#a86c55"),
        "minecraft:chest": previewRegistryItem("Chest", "#a86e31", "#e6b15c"),
        "minecraft:obsidian": previewRegistryItem("Obsidian", "#241c35", "#765a92"),
    };
}

function previewRegistryItem(name: string, primary: string, highlight: string) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" `
        + `viewBox="0 0 16 16" shape-rendering="crispEdges">`
        + `<rect x="2" y="2" width="12" height="12" fill="${primary}"/>`
        + `<rect x="4" y="3" width="7" height="2" fill="${highlight}"/>`
        + `<rect x="3" y="6" width="2" height="6" fill="${highlight}" opacity=".72"/>`
        + `<rect x="6" y="11" width="7" height="2" fill="#000" opacity=".28"/>`
        + `</svg>`;

    return {
        name,
        icon: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`,
    };
}
