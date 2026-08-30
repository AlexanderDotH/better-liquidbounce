import type {Session, TextComponent} from "../types";

export interface ClickGuiScaleChangeEvent {
    value: number;
}

export interface ModuleActivationEvent {
    moduleName: string;
}

export interface GameModeChangeEvent {
    gameMode: "survival" | "creative" | "adventure" | "spectator";
}

export interface ClientChatStateChangeEvent {
    state: "connecting" | "connected" | "logon" | "loggedIn" | "disconnected" | "authenticationFailed";
}

export interface ClientChatMessageEvent {
    user: {
        name: string;
        uuid: string;
    };
    message: string;
    chatGroup: "PublicChat" | "PrivateChat";
    // Not "public"/"private" because the EnumChoiceSerializer in Kotlin ignores @SerializedName annotations, bug?
}

export interface ClientChatErrorEvent {
    error: string;
}

export interface SessionEvent {
    session: Session;
}

export interface ChatSendEvent {
    message: string;
}

export interface ChatReceiveEvent {
    message: string;
    textData: TextComponent;
    type: "ChatMessage" | "DisguisedChatMessage" | "GameMessage";
}

export interface FpsChangeEvent {
    fps: number;
}

export interface TitleEventTitle {
    text: TextComponent | string | null;
}

export interface TitleEventSubtitle {
    text: TextComponent | string | null;
}

export interface TitleEventFade {
    fadeInTicks: number;
    stayTicks: number;
    fadeOutTicks: number;
}

export interface TitleEventClear {
    reset: boolean;
}

export interface ClosedCaptionsEvent {
    entries: ClosedCaptionEntry[];
}

export interface ClosedCaptionEntry {
    text: TextComponent | string;
    direction: "NONE" | "LEFT" | "RIGHT";
    textColor: number;
    backgroundColor: number;
}

export interface VirtualScreenEvent {
    type: string;
    action: "open" | "close";
}
