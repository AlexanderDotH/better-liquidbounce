import type {ClientInfo, GameWindow, MinecraftKeybind} from "../../integration/types";

export function createKeybinds(): MinecraftKeybind[] {
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

export function createClientInfo(): ClientInfo {
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

export function createGameWindow(): GameWindow {
    return {
        width: 1440,
        height: 900,
        scaledWidth: 720,
        scaledHeight: 450,
        scaleFactor: 2,
        guiScale: 2,
    };
}
