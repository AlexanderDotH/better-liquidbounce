import type {
    ClientInfo,
    ConfigurableSetting,
    ContextualBarData,
    GameWindow,
    HudComponent,
    ItemStack,
    Metadata,
    MinecraftKeybind,
    Module,
    PlayerData,
    StatusEffect,
    TextComponent,
} from "../../integration/types";
import type {PlayerInventory} from "../../integration/events";

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

const API_PREFIX = "/api/v1/client";

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

export async function routeModernHudPreviewRequest(
    state: ModernHudPreviewState,
    request: Request,
): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();
    const route = `${method} ${url.pathname}`;
    state.requests.push({method, path: `${url.pathname}${url.search}`});

    if (route === "GET /metadata.json") {
        return jsonResponse(state.metadata);
    }

    if (route === `GET ${API_PREFIX}/info`) {
        return jsonResponse(state.clientInfo);
    }

    if (route === `GET ${API_PREFIX}/window`) {
        return jsonResponse(state.gameWindow);
    }

    if (route === `GET ${API_PREFIX}/modules`) {
        return jsonResponse(state.modules);
    }

    if (route === `GET ${API_PREFIX}/player`) {
        return jsonResponse(state.player);
    }

    if (route === `GET ${API_PREFIX}/player/contextualBar`) {
        return jsonResponse(state.contextualBar);
    }

    if (route === `GET ${API_PREFIX}/player/inventory`) {
        return jsonResponse(state.inventory);
    }

    if (route === `GET ${API_PREFIX}/keybinds`) {
        return jsonResponse(state.keybinds);
    }

    if (route === `GET ${API_PREFIX}/input`) {
        return printableKeyResponse(url);
    }

    if (url.pathname === `${API_PREFIX}/modules/settings`) {
        return routeHudSettings(state, request, url);
    }

    if (route === `POST ${API_PREFIX}/modules/toggle`) {
        return routeModuleToggle(state, request);
    }

    if (
        method === "GET"
        && url.pathname === `${API_PREFIX}/components/${state.metadata.id}`
    ) {
        return jsonResponse(state.components);
    }

    if (
        method === "GET"
        && url.pathname.startsWith(`${API_PREFIX}/resource/`)
    ) {
        return previewResourceResponse(url);
    }

    return jsonResponse(
        {error: `Unsupported Modern HUD preview API: ${method} ${url.pathname}`},
        404,
    );
}

export function createModernHudPreviewSnapshotEvents(
    state: ModernHudPreviewState,
): ModernHudPreviewServerEvent[] {
    return clone([
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
    ]);
}

function createContextualBar(): ContextualBarData {
    return {
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
}

async function routeHudSettings(
    state: ModernHudPreviewState,
    request: Request,
    url: URL,
): Promise<Response> {
    if (url.searchParams.get("name") !== "HUD") {
        return jsonResponse({error: "Only HUD settings exist in this preview."}, 404);
    }

    if (request.method === "GET") {
        return jsonResponse(state.hudSettings);
    }

    if (request.method !== "PUT") {
        return methodNotAllowed(["GET", "PUT"]);
    }

    const settings = await readJson<unknown>(request);
    if (!isHudSettings(settings)) {
        return jsonResponse({error: "Invalid HUD settings payload."}, 400);
    }

    state.hudSettings = clone(settings);
    return emptyResponse();
}

async function routeModuleToggle(
    state: ModernHudPreviewState,
    request: Request,
): Promise<Response> {
    const body = await readJson<unknown>(request);
    if (!isModuleToggle(body)) {
        return jsonResponse({error: "Invalid module toggle payload."}, 400);
    }

    const module = state.modules.find(candidate => candidate.name === body.name);
    if (!module) {
        return jsonResponse({error: `Unknown preview module: ${body.name}`}, 404);
    }

    module.enabled = body.enabled;
    return emptyResponse();
}

function createMetadata(): Metadata {
    const components = [
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
        components,
        fonts: [
            "Inter-Bold.ttf",
            "Inter-Medium.ttf",
            "Inter-Regular.ttf",
        ],
        backgrounds: [],
    };
}

function createComponents(
    names: string[],
    fixture: ModernHudPreviewFixture,
): HudComponent[] {
    const settingsByName: Record<string, Record<string, unknown>> = {
        Watermark: positioned("Left", 20, "Top", 20),
        TabGui: positioned("Left", 190, "Top", 20),
        ArrayList: {
            ...positioned("Right", 20, "Top", 20),
            showTags: true,
            itemAlignment: "Right",
            order: "Descending",
        },
        Notifications: positioned("Right", 20, "Bottom", 188),
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

function createModules(): Module[] {
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
        hidden: false,
        aliases: [],
        tag,
    }));
}

function createHudSettings(): ConfigurableSetting {
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

function createPlayer(username: string, uuid: string): PlayerData {
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
        scoreboard: {
            header: text("BED WARS", "aqua", true),
            entries: [
                scoreEntry("Date", "07/30/26"),
                scoreEntry("Team", "Aqua"),
                scoreEntry("Kills", "7"),
                scoreEntry("Beds", "2"),
                scoreEntry("play.liquidbounce.net", ""),
            ],
        },
    };
}

function createTarget(): PlayerData {
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

function createInventory(): PlayerInventory {
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

function text(
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

function createKeybinds(): MinecraftKeybind[] {
    return [
        keybind("key.forward", "key.keyboard.w", "W"),
        keybind("key.back", "key.keyboard.s", "S"),
        keybind("key.left", "key.keyboard.a", "A"),
        keybind("key.right", "key.keyboard.d", "D"),
        keybind("key.jump", "key.keyboard.space", "Space"),
    ];
}

function keybind(
    bindName: string,
    translationKey: string,
    localized: string,
): MinecraftKeybind {
    return {
        bindName,
        key: {translationKey, localized},
    };
}

function createClientInfo(): ClientInfo {
    return {
        os: "linux",
        gameVersion: "1.21.1",
        clientVersion: "preview",
        clientName: "LiquidBounce",
        development: true,
        fps: 144,
        gameDir: "/preview/.minecraft",
        clientDir: "/preview/.liquidbounce",
        inGame: true,
        viaFabricPlus: false,
        hasProtocolHack: false,
    };
}

function createGameWindow(): GameWindow {
    return {
        width: 1440,
        height: 900,
        scaledWidth: 720,
        scaledHeight: 450,
        scaleFactor: 2,
        guiScale: 2,
    };
}

function printableKeyResponse(url: URL): Response {
    const translationKey = url.searchParams.get("key") ?? "key.keyboard.unknown";
    const keyName = translationKey.split(".").at(-1) ?? "unknown";
    const localized = keyName.length === 1
        ? keyName.toUpperCase()
        : keyName.charAt(0).toUpperCase() + keyName.slice(1);

    return jsonResponse({translationKey, localized});
}

function previewResourceResponse(url: URL): Response {
    const kind = url.pathname.split("/").at(-1) ?? "resource";
    const label = url.searchParams.get("id") ?? url.searchParams.get("uuid") ?? kind;
    const color = previewColor(label);
    const svg = [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">',
        `<rect width="32" height="32" rx="7" fill="${color}"/>`,
        '<path d="M8 21 16 7l8 14-8 5z" fill="rgba(255,255,255,.72)"/>',
        "</svg>",
    ].join("");

    return new Response(svg, {
        status: 200,
        headers: {"Content-Type": "image/svg+xml"},
    });
}

function previewColor(value: string): string {
    let hash = 0;
    for (const character of value) {
        hash = ((hash << 5) - hash + character.charCodeAt(0)) | 0;
    }

    return `hsl(${Math.abs(hash) % 360} 48% 48%)`;
}

function isHudSettings(value: unknown): value is ConfigurableSetting {
    if (!isRecord(value)) {
        return false;
    }

    return value.name === "HUD"
        && value.valueType === "CONFIGURABLE"
        && Array.isArray(value.value);
}

function isModuleToggle(value: unknown): value is {name: string; enabled: boolean} {
    return isRecord(value)
        && typeof value.name === "string"
        && typeof value.enabled === "boolean";
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

async function readJson<T>(request: Request): Promise<T> {
    return await request.json() as T;
}

function jsonResponse(value: unknown, status = 200): Response {
    return new Response(JSON.stringify(clone(value)), {
        status,
        headers: {"Content-Type": "application/json"},
    });
}

function emptyResponse(): Response {
    return new Response(null, {status: 204});
}

function methodNotAllowed(allowed: string[]): Response {
    return new Response(null, {
        status: 405,
        headers: {Allow: allowed.join(", ")},
    });
}

function clone<T>(value: T): T {
    return structuredClone(value);
}
