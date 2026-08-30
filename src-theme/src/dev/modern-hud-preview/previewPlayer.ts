import type {ItemStack, PlayerData, StatusEffect, TextComponent} from "../../integration/types";
import type {PlayerInventory} from "../../integration/events";

export function createPlayer(username: string, uuid: string): PlayerData {
    const effects = createEffects();
    const armorItems = createArmorItems();

    return {
        username,
        uuid,
        position: {x: 124.75, y: 68, z: -342.5},
        blockPosition: {x: 124, y: 68, z: -343},
        velocity: {x: 0.13, y: 0, z: -0.08},
        selectedSlot: 2,
        gameMode: "survival",
        health: 16.5,
        actualHealth: 16.5,
        maxHealth: 20,
        absorption: 4,
        yaw: 127.5,
        pitch: 8.25,
        armor: 17,
        food: 18,
        air: 270,
        maxAir: 300,
        experienceLevel: 27,
        experienceProgress: 0.64,
        effects,
        mainHandStack: item("minecraft:diamond_sword", 1, 182, 1561, "Diamond Sword", {
            "minecraft:sharpness": 4,
        }),
        offHandStack: item("minecraft:golden_apple", 3, 0, 1, "Golden Apple"),
        armorItems,
        scoreboard: createScoreboard(),
    };
}

function createScoreboard() {
    return {
        header: text("BED WARS", "aqua", true),
        entries: [
            scoreEntry("Date", "07/30/26"),
            scoreEntry("Team", "Aqua"),
            scoreEntry("Kills", "7"),
            scoreEntry("Beds", "2"),
            scoreEntry("play.liquidbounce.net", ""),
        ],
    };
}

export function createTarget(): PlayerData {
    const target = createPlayer(
        "PreviewTarget",
        "00000000-0000-4000-a000-000000000002",
    );
    target.health = 11.25;
    target.actualHealth = 11.25;
    target.absorption = 2;
    target.armor = 14;
    target.effects = [];
    return target;
}

function createEffects(): StatusEffect[] {
    return [
        effect("minecraft:speed", "Speed", 2_140, 1, 0x7CAFC6),
        effect("minecraft:strength", "Strength", 920, 0, 0x932423),
        effect("minecraft:regeneration", "Regeneration", 360, 0, 0xCD5CAB),
    ];
}

function effect(
    effectId: string,
    localizedName: string,
    duration: number,
    amplifier: number,
    color: number,
): StatusEffect {
    return {
        effect: effectId,
        localizedName,
        duration,
        amplifier,
        ambient: false,
        infinite: false,
        visible: true,
        showIcon: true,
        color,
    };
}

export function createInventory(): PlayerInventory {
    const hotbar = [
        item("minecraft:diamond_sword", 1, 182, 1561, "Diamond Sword", {
            "minecraft:sharpness": 4,
        }),
        item("minecraft:bow", 1, 41, 384, "Bow"),
        item("minecraft:white_wool", 64, 0, 1, "White Wool"),
        item("minecraft:golden_apple", 3, 0, 1, "Golden Apple"),
        item("minecraft:ender_pearl", 8, 0, 1, "Ender Pearl"),
        item("minecraft:water_bucket", 1, 0, 1, "Water Bucket"),
        item("minecraft:iron_pickaxe", 1, 22, 250, "Iron Pickaxe"),
        item("minecraft:arrow", 32, 0, 1, "Arrow"),
        item("minecraft:compass", 1, 0, 1, "Compass"),
    ];
    const storage = [
        item("minecraft:emerald", 12, 0, 1, "Emerald"),
        item("minecraft:diamond", 7, 0, 1, "Diamond"),
        item("minecraft:gold_ingot", 18, 0, 1, "Gold Ingot"),
        item("minecraft:iron_ingot", 54, 0, 1, "Iron Ingot"),
        item("minecraft:oak_planks", 64, 0, 1, "Oak Planks"),
        item("minecraft:cobweb", 4, 0, 1, "Cobweb"),
        item("minecraft:fire_charge", 2, 0, 1, "Fire Charge"),
        item("minecraft:tnt", 3, 0, 1, "TNT"),
    ];

    return {
        armor: createArmorItems(),
        main: fillInventory([...hotbar, ...storage], 36),
        crafting: fillInventory([
            item("minecraft:stick", 2, 0, 1, "Stick"),
            item("minecraft:iron_ingot", 3, 0, 1, "Iron Ingot"),
        ], 4),
        enderChest: fillInventory([
            item("minecraft:diamond", 16, 0, 1, "Diamond"),
            item("minecraft:emerald", 9, 0, 1, "Emerald"),
            item("minecraft:golden_apple", 5, 0, 1, "Golden Apple"),
            item("minecraft:ender_pearl", 12, 0, 1, "Ender Pearl"),
        ], 27),
    };
}

function createArmorItems(): ItemStack[] {
    return [
        item("minecraft:diamond_boots", 1, 41, 429, "Diamond Boots", {
            "minecraft:protection": 3,
        }),
        item("minecraft:diamond_leggings", 1, 74, 495, "Diamond Leggings"),
        item("minecraft:diamond_chestplate", 1, 58, 528, "Diamond Chestplate"),
        item("minecraft:diamond_helmet", 1, 39, 363, "Diamond Helmet"),
    ];
}

function fillInventory(stacks: ItemStack[], size: number): ItemStack[] {
    return Array.from({length: size}, (_, index) =>
        stacks[index] ?? item("minecraft:air", 0, 0, 1, "Air"),
    );
}

function item(
    identifier: string,
    count: number,
    damage: number,
    maxDamage: number,
    displayName: string,
    enchantments?: Record<string, number>,
): ItemStack {
    return {
        identifier,
        count,
        damage,
        maxDamage,
        displayName,
        enchantments,
    };
}

function scoreEntry(name: string, score: string) {
    return {
        name: text(name, "white"),
        score: text(score, "yellow"),
    };
}

export function text(
    value: string,
    color = "white",
    bold = false,
): TextComponent {
    return {
        type: "text",
        color,
        bold,
        text: value,
    };
}
