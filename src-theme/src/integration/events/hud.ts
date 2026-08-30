import type {BedState, ContextualBarData, PlayerData, StatusEffect, TextComponent, MinecraftKey} from "../types";

export interface ClientPlayerDataEvent {
    playerData: PlayerData;
}

export interface ContextualBarEvent {
    contextualBar: ContextualBarData;
}

export interface ClientPlayerEffectEvent {
    effects: StatusEffect[];
}

export interface OverlayMessageEvent {
    text: TextComponent | string;
    tinted: boolean;
}

export type NotificationSeverity = "INFO" | "SUCCESS" | "ERROR" | "ENABLED" | "DISABLED";

export interface NotificationEvent {
    title: string;
    message: string;
    severity: NotificationSeverity;
}

export interface KeyEvent {
    key: MinecraftKey;
    action: number;
}

export interface TargetChangeEvent {
    target: PlayerData | null;
}

export interface BlockCountChangeEvent {
    nextBlock?: string;
    count?: number;
}

export interface BedStateChangeEvent {
    bedStates: BedState[];
}
