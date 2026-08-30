import type {TextComponent} from "./menu";

export interface Scoreboard {
    header: TextComponent;
    entries: {
        name: TextComponent;
        score: TextComponent;
    }[];
}

export interface PlayerData {
    username: string;
    uuid: string;
    position: Vec3;
    blockPosition: Vec3;
    velocity: Vec3;
    selectedSlot: number;
    gameMode: string;
    health: number,
    actualHealth: number;
    maxHealth: number;
    absorption: number;
    yaw: number;
    pitch: number;
    armor: number;
    food: number;
    air: number;
    maxAir: number;
    experienceLevel: number;
    experienceProgress: number;
    effects: StatusEffect[];
    mainHandStack: ItemStack;
    offHandStack: ItemStack;
    armorItems: ItemStack[];
    scoreboard: Scoreboard;
}

export type ContextualBarMode = "empty" | "experience" | "locator" | "jumpableVehicle";

export interface ContextualBarData {
    mode: ContextualBarMode;
    progress: number;
    level: number;
    cooldown: boolean;
    markers: readonly LocatorMarker[];
}

export interface LocatorMarker {
    id: string;
    label: string;
    offset: number;
    elevation: "above" | "level" | "below";
    distance: number;
    color: number;
    kind: "player" | "waypoint";
    playerUuid: string | null;
    style: string;
}

export interface StatusEffect {
    effect: string;
    localizedName: string;
    duration: number;
    amplifier: number;
    ambient: boolean;
    infinite: boolean;
    visible: boolean;
    showIcon: boolean;
    color: number;
}

export interface Vec2 extends Vec<"x" | "y"> {
}

export interface Vec3 extends Vec<"x" | "y" | "z"> {
}

export type VecAxis = "x" | "y" | "z" | "w";

export type Vec<D extends VecAxis> = Record<D, number>;

export interface ItemStack {
    identifier: string;
    count: number;
    damage: number;
    maxDamage: number;
    displayName: TextComponent | string;
    enchantments?: Record<string, number>;
}

export interface PrintableKey {
    translationKey: string;
    localized: string;
}

export interface MinecraftKeybind {
    bindName: string;
    key: PrintableKey;
}
