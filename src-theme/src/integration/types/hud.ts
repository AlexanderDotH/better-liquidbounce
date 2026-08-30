import type {File} from "./settings";
import type {Vec3} from "./gameplay";

export interface GameWindow {
    width: number;
    height: number;
    scaledWidth: number;
    scaledHeight: number;
    scaleFactor: number;
    guiScale: number;
}

export interface Theme {
    name: string;
    id: string;
    colors: {
        accent: number;
        tint: number;
    };
    settings: { [name: string]: any };
}

export interface HudComponent {
    name: string;
    description: string;
    id: string;
    settings: { [name: string]: any };
    width?: number;
    height?: number;
}

export interface HudComponentCatalogEntry {
    name: string;
    description: string;
    id: string;
    singleton: boolean;
    canAdd: boolean;
}

export interface Alignment {
    horizontalAlignment: HorizontalAlignment;
    verticalAlignment: VerticalAlignment;
    horizontalOffset: number;
    verticalOffset: number;
}

export enum HorizontalAlignment {
    LEFT = "Left",
    RIGHT = "Right",
    CENTER = "Center",
    CENTER_TRANSLATED = "CenterTranslated",
}

export enum VerticalAlignment {
    TOP = "Top",
    BOTTOM = "Bottom",
    CENTER = "Center",
    CENTER_TRANSLATED = "CenterTranslated",
}

export type OS = "linux" | "solaris" | "windows" | "mac" | "unknown";

export interface ClientInfo {
    os: OS;
    gameVersion: string;
    clientVersion: string;
    clientName: string;
    development: boolean;
    fps: number;
    gameDir: File;
    clientDir: File;
    inGame: boolean;
    viaFabricPlus: boolean;
    hasProtocolHack: boolean;
}

export interface ClientUpdate {
    development: boolean;
    commit: string;
    update: {
        buildId: number | undefined;
        commitId: string | undefined;
        branch: string | undefined;
        clientVersion: string | undefined;
        minecraftVersion: string | undefined;
        release: boolean;
        date: string;
        message: string;
        url: string;
    } | undefined;
}

export interface Browser {
    url: string
}

export interface HitResult {
    type: "block" | "entity" | "miss";
    pos: Vec3;
}

export interface BlockHitResult extends HitResult {
    blockPos: Vec3;
    side: string;
    isInsideBlock: boolean;
}

export interface EntityHitResult extends HitResult {
    entityName: string;
    entityType: string;
    entityPos: Vec3;
}

export interface GeneratorResult {
    name: string;
}

export interface Screen {
    class: string,
    title: string,
}

export interface ClientUser {
    userId: string;
    email: string;
    name: string | null;
    nickname: string | null;
    groups: string[];
    premium: boolean;
    admin: boolean;
}

export interface RegistryItem {
    name: string;
    icon: string | undefined;
}

export interface Range {
    from: number;
    to: number;
}
