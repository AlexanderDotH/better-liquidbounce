import type {
    ConfigurableSetting,
    Module as ClickGuiModule,
    ModuleSetting,
    PersistentStorageItem,
} from "../../integration/types";

export interface PreviewRequestRecord {
    method: string;
    path: string;
}

export interface ModernClickGuiPreviewState {
    modules: ClickGuiModule[];
    moduleSettings: Record<string, ConfigurableSetting>;
    globalSettings: ConfigurableSetting;
    persistentItems: PersistentStorageItem[];
    typing: boolean;
    clipboardText: string;
    requests: PreviewRequestRecord[];
}

const API_PREFIX = "/api/v1/client";

export function createModernClickGuiPreviewState(): ModernClickGuiPreviewState {
    const modules = createPreviewModules();
    const moduleSettings = Object.fromEntries(
        modules.map(module => [module.name, createDefaultModuleSettings(module.name)]),
    );
    moduleSettings.KillAura = createComprehensiveModuleSettings();
    moduleSettings.AutoShop = createAutoShopSettings();
    moduleSettings.ClickGUI = createClickGuiSettings();

    return {
        modules,
        moduleSettings,
        globalSettings: createGlobalSettings(),
        persistentItems: createInitialPersistentItems(),
        typing: false,
        clipboardText: "preview-player",
        requests: [],
    };
}

export async function routeModernClickGuiPreviewRequest(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();
    const route = `${method} ${url.pathname}`;
    state.requests.push({method, path: `${url.pathname}${url.search}`});

    if (route === `GET ${API_PREFIX}/modules`) {
        return jsonResponse(state.modules);
    }

    if (url.pathname === `${API_PREFIX}/modules/settings`) {
        return routeModuleSettings(state, request, url);
    }

    if (route === `POST ${API_PREFIX}/modules/toggle`) {
        return routeModuleToggle(state, request);
    }

    if (url.pathname === `${API_PREFIX}/global`) {
        return routeGlobalSettings(state, request);
    }

    if (url.pathname === `${API_PREFIX}/localStorage/all`) {
        return routePersistentStorage(state, request);
    }

    if (route === `POST ${API_PREFIX}/typing`) {
        return routeTyping(state, request);
    }

    if (route === `GET ${API_PREFIX}/clipboard`) {
        return jsonResponse({text: state.clipboardText});
    }

    if (route === `PUT ${API_PREFIX}/clipboard`) {
        return routeClipboard(state, request);
    }

    if (route === `GET ${API_PREFIX}/info`) {
        return jsonResponse(createClientInfo());
    }

    if (route === `GET ${API_PREFIX}/window`) {
        return jsonResponse(createGameWindow());
    }

    if (route === `GET ${API_PREFIX}/input`) {
        return routePrintableKey(url);
    }

    if (method === "GET" && url.pathname.startsWith(`${API_PREFIX}/registry/`)) {
        return jsonResponse(createRegistryItems());
    }

    if (route === `POST ${API_PREFIX}/fileDialog`) {
        return jsonResponse({file: "/home/alex/LiquidBounce/preview-config.json"});
    }

    if (route === `GET ${API_PREFIX}/virtualScreen`) {
        return jsonResponse({name: "clickGui"});
    }

    if (route === `POST ${API_PREFIX}/virtualScreen`) {
        return emptyResponse();
    }

    return jsonResponse(
        {error: `Unsupported preview API: ${method} ${url.pathname}`},
        404,
    );
}

async function routeModuleSettings(
    state: ModernClickGuiPreviewState,
    request: Request,
    url: URL,
): Promise<Response> {
    const name = url.searchParams.get("name");
    if (!name || !Object.hasOwn(state.moduleSettings, name)) {
        return jsonResponse({error: `Unknown preview module: ${name ?? ""}`}, 404);
    }

    if (request.method === "GET") {
        return jsonResponse(state.moduleSettings[name]);
    }

    if (request.method !== "PUT") {
        return methodNotAllowed(["GET", "PUT"]);
    }

    const settings = await readJson<ConfigurableSetting>(request);
    if (!isConfigurableSetting(settings)) {
        return jsonResponse({error: "Invalid module settings payload"}, 400);
    }

    state.moduleSettings[name] = clone(settings);
    return emptyResponse();
}

async function routeModuleToggle(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    const body = await readJson<unknown>(request);
    if (!isModuleToggle(body)) {
        return jsonResponse({error: "Invalid module toggle payload"}, 400);
    }

    const module = state.modules.find(candidate => candidate.name === body.name);
    if (!module) {
        return jsonResponse({error: `Unknown preview module: ${body.name}`}, 404);
    }

    module.enabled = body.enabled;
    return emptyResponse();
}

async function routeGlobalSettings(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    if (request.method === "GET") {
        return jsonResponse(state.globalSettings);
    }

    if (request.method !== "PUT") {
        return methodNotAllowed(["GET", "PUT"]);
    }

    const settings = await readJson<ConfigurableSetting>(request);
    if (!isConfigurableSetting(settings)) {
        return jsonResponse({error: "Invalid global settings payload"}, 400);
    }

    state.globalSettings = clone(settings);
    return emptyResponse();
}

async function routePersistentStorage(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    if (request.method === "GET") {
        return jsonResponse({items: state.persistentItems});
    }

    if (request.method !== "PUT") {
        return methodNotAllowed(["GET", "PUT"]);
    }

    const body = await readJson<unknown>(request);
    if (!isPersistentStoragePayload(body)) {
        return jsonResponse({error: "Invalid persistent storage payload"}, 400);
    }

    state.persistentItems = clone(body.items);
    return emptyResponse();
}

async function routeTyping(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    const body = await readJson<unknown>(request);
    if (!isTypingPayload(body)) {
        return jsonResponse({error: "Invalid typing payload"}, 400);
    }

    state.typing = body.typing;
    return emptyResponse();
}

async function routeClipboard(
    state: ModernClickGuiPreviewState,
    request: Request,
): Promise<Response> {
    const body = await readJson<unknown>(request);
    if (!isClipboardPayload(body)) {
        return jsonResponse({error: "Invalid clipboard payload"}, 400);
    }

    state.clipboardText = body.text;
    return emptyResponse();
}

function routePrintableKey(url: URL): Response {
    const key = url.searchParams.get("key") ?? "key.keyboard.unknown";
    const name = key.split(".").at(-1) ?? "unknown";
    const localized = name === "unknown"
        ? "None"
        : name.length === 1
            ? name.toUpperCase()
            : `${name.charAt(0).toUpperCase()}${name.slice(1)}`;

    return jsonResponse({
        translationKey: key,
        localized,
    });
}

function createPreviewModules(): ClickGuiModule[] {
    const definitions: Array<[
        name: string,
        category: string,
        enabled: boolean,
        aliases?: string[],
    ]> = [
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

    return definitions.map(([name, category, enabled, aliases = []]) => ({
        name,
        category,
        keyBind: {
            boundKey: "key.keyboard.unknown",
            action: "Toggle",
            modifiers: [],
        },
        enabled,
        description: previewDescription(name),
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

function createComprehensiveModuleSettings(): ConfigurableSetting {
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

function createDefaultModuleSettings(name: string): ConfigurableSetting {
    return configurable(name, [
        booleanSetting("EnabledInInventory", name.length % 2 === 0),
        bindSetting("Bind", "key.keyboard.unknown"),
        booleanSetting("Hidden", false),
    ]);
}

function createAutoShopSettings(): ConfigurableSetting {
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

function createClickGuiSettings(): ConfigurableSetting {
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

function createGlobalSettings(): ConfigurableSetting {
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

function configurable(
    name: string,
    value: ModuleSetting[],
    description?: string,
): ConfigurableSetting {
    return {
        name,
        valueType: "CONFIGURABLE",
        value,
        description,
        key: `preview.${name}`,
    };
}

function togglable(name: string, value: ModuleSetting[]): ModuleSetting {
    return {
        name,
        valueType: "TOGGLEABLE",
        value,
        description: undefined,
        key: `preview.${name}`,
    };
}

function booleanSetting(
    name: string,
    value: boolean,
    description?: string,
): ModuleSetting {
    return {
        name,
        valueType: "BOOLEAN",
        value,
        description,
        key: `preview.${name}`,
    };
}

function floatSetting(
    name: string,
    value: number,
    from: number,
    to: number,
    suffix: string,
): ModuleSetting {
    return {
        name,
        valueType: "FLOAT",
        value,
        range: {from, to},
        suffix,
        description: undefined,
        key: `preview.${name}`,
    };
}

function intSetting(
    name: string,
    value: number,
    from: number,
    to: number,
    suffix: string,
): ModuleSetting {
    return {
        name,
        valueType: "INT",
        value,
        range: {from, to},
        suffix,
        description: undefined,
        key: `preview.${name}`,
    };
}

function intRangeSetting(
    name: string,
    fromValue: number,
    toValue: number,
    from: number,
    to: number,
): ModuleSetting {
    return {
        name,
        valueType: "INT_RANGE",
        value: {from: fromValue, to: toValue},
        range: {from, to},
        suffix: "",
        description: undefined,
        key: `preview.${name}`,
    };
}

function chooseSetting(
    name: string,
    value: string,
    choices: string[],
): ModuleSetting {
    return {
        name,
        valueType: "CHOOSE",
        value,
        choices,
        description: undefined,
        key: `preview.${name}`,
    };
}

function choiceSetting(name: string): ModuleSetting {
    const adaptive = configurable("Adaptive", [
        floatSetting("BurstDelay", 90, 0, 500, "ms"),
    ]);
    const steady = configurable("Steady", [
        intSetting("ClicksPerSecond", 11, 1, 20, " cps"),
    ]);

    return {
        name,
        valueType: "CHOICE",
        value: [],
        active: "Adaptive",
        choices: {Adaptive: adaptive, Steady: steady},
        categories: {Dynamic: ["Adaptive"], Simple: ["Steady"]},
        description: undefined,
        key: `preview.${name}`,
    };
}

function choiceModes(
    name: string,
    active: string,
    choices: Record<string, ConfigurableSetting>,
): ModuleSetting {
    return {
        name,
        valueType: "CHOICE",
        value: [],
        active,
        choices,
        description: undefined,
        key: `preview.${name}`,
    };
}

function multiChooseSetting(
    name: string,
    value: string[],
    choices: string[],
): ModuleSetting {
    return {
        name,
        valueType: "MULTI_CHOOSE",
        value,
        choices,
        canBeNone: false,
        isOrderSensitive: false,
        description: undefined,
        key: `preview.${name}`,
    };
}

function textSetting(name: string, value: string): ModuleSetting {
    return {
        name,
        valueType: "TEXT",
        value,
        description: undefined,
        key: `preview.${name}`,
    };
}

function bindSetting(name: string, boundKey: string): ModuleSetting {
    return {
        name,
        valueType: "BIND",
        value: {
            boundKey,
            action: "Toggle",
            modifiers: [],
        },
        defaultValue: {
            boundKey: "key.keyboard.unknown",
            action: "Toggle",
            modifiers: [],
        },
        description: undefined,
        key: `preview.${name}`,
    };
}

function mutableListSetting(name: string, value: string[]): ModuleSetting {
    return {
        name,
        valueType: "MUTABLE_LIST",
        value,
        innerValueType: "TEXT",
        description: undefined,
        key: `preview.${name}`,
    };
}

function registryListSetting(
    name: string,
    value: string[],
    registry: string,
): ModuleSetting {
    return {
        name,
        valueType: "REGISTRY_LIST",
        value,
        innerValueType: "TEXT",
        registry,
        description: undefined,
        key: `preview.${name}`,
    };
}

function merchantTradeFiltersSetting(): ModuleSetting {
    return {
        name: "Trades",
        valueType: "MERCHANT_TRADE_FILTERS",
        value: [
            {
                inputA: ["minecraft:emerald"],
                inputB: [],
                outputs: ["minecraft:bread"],
            },
            {
                inputA: ["minecraft:paper"],
                inputB: ["minecraft:book"],
                outputs: ["minecraft:enchanted_book"],
            },
        ],
        registry: "item",
        description: "Ordered item-only rules processed round-robin.",
        key: "preview.AutoShop.Trades",
    };
}

function merchantReachSetting(): ModuleSetting {
    return {
        name: "Reach",
        valueType: "MERCHANT_REACH",
        value: {range: 4.5, wallRange: 3},
        rangeBounds: {from: 1, to: 6},
        wallRangeBounds: {from: 0, to: 6},
        suffix: "blocks",
        description: "Uses the shorter wall range for merchants without line of sight.",
        key: "preview.AutoShop.Reach",
    };
}

function createInitialPersistentItems(): PersistentStorageItem[] {
    return [
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
}

function createClientInfo() {
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

function createGameWindow() {
    return {
        width: 1440,
        height: 900,
        scaledWidth: 720,
        scaledHeight: 450,
        scaleFactor: 2,
        guiScale: 2,
    };
}

function createRegistryItems() {
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

function isConfigurableSetting(value: unknown): value is ConfigurableSetting {
    if (!isRecord(value)) {
        return false;
    }

    return value.valueType === "CONFIGURABLE"
        && typeof value.name === "string"
        && Array.isArray(value.value);
}

function isModuleToggle(value: unknown): value is {name: string; enabled: boolean} {
    return isRecord(value)
        && typeof value.name === "string"
        && typeof value.enabled === "boolean";
}

function isTypingPayload(value: unknown): value is {typing: boolean} {
    return isRecord(value) && typeof value.typing === "boolean";
}

function isClipboardPayload(value: unknown): value is {text: string} {
    return isRecord(value)
        && Object.hasOwn(value, "text")
        && typeof value.text === "string";
}

function isPersistentStoragePayload(
    value: unknown,
): value is {items: PersistentStorageItem[]} {
    return isRecord(value)
        && Array.isArray(value.items)
        && value.items.every(item =>
            isRecord(item)
            && typeof item.key === "string"
            && typeof item.value === "string"
        );
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
