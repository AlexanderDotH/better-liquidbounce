import type {BaritoneLogEvent, BaritoneRouteEvent, BaritoneStateEvent} from "./baritone";
import type {
    ThemeColorChangeEvent,
    ClickGuiValueChangeEvent,
    HudValueChangeEvent,
    ModuleToggleEvent,
    KeyboardKeyEvent,
    MouseButtonEvent,
    KeyboardCharEvent,
    ScaleFactorChangeEvent,
    ComponentsUpdateEvent,
} from "./events/client";
import type {
    ClientPlayerDataEvent,
    ContextualBarEvent,
    ClientPlayerEffectEvent,
    OverlayMessageEvent,
    NotificationEvent,
    KeyEvent,
    TargetChangeEvent,
    BlockCountChangeEvent,
    BedStateChangeEvent,
} from "./events/hud";
import type {
    AccountManagerAdditionEvent,
    AccountManagerRemovalEvent,
    AccountManagerMessageEvent,
    AccountManagerLoginEvent,
    ServerPingedEvent,
    ClientPlayerInventoryEvent,
    ProxyCheckResultEvent,
    SpaceSeperatedNamesChangeEvent,
    BrowserUrlChangeEvent,
    ValueChangedEvent,
} from "./events/accounts";
import type {
    ClickGuiScaleChangeEvent,
    ModuleActivationEvent,
    GameModeChangeEvent,
    ClientChatStateChangeEvent,
    ClientChatMessageEvent,
    ClientChatErrorEvent,
    SessionEvent,
    ChatSendEvent,
    ChatReceiveEvent,
    FpsChangeEvent,
    TitleEventTitle,
    TitleEventSubtitle,
    TitleEventFade,
    TitleEventClear,
    ClosedCaptionsEvent,
    VirtualScreenEvent,
} from "./events/game";

export interface EventMap {
    socketReady: void;

    baritoneState: BaritoneStateEvent;
    baritoneRoute: BaritoneRouteEvent;
    baritoneLog: BaritoneLogEvent;

    themeColorChange: ThemeColorChangeEvent;
    clickGuiScaleChange: ClickGuiScaleChangeEvent;
    clickGuiValueChange: ClickGuiValueChangeEvent;
    hudValueChange: HudValueChangeEvent;
    spaceSeperatedNamesChange: SpaceSeperatedNamesChangeEvent;
    clientLanguageChanged: void;
    valueChanged: ValueChangedEvent;
    moduleActivation: ModuleActivationEvent;
    moduleToggle: ModuleToggleEvent;
    refreshArrayList: void;
    notification: NotificationEvent;
    gameModeChange: GameModeChangeEvent;
    targetChange: TargetChangeEvent;
    blockCountChange: BlockCountChangeEvent;
    bedStateChange: BedStateChangeEvent;
    clientChatStateChange: ClientChatStateChangeEvent;
    clientChatMessage: ClientChatMessageEvent;
    clientChatError: ClientChatErrorEvent;
    accountManagerMessage: AccountManagerMessageEvent;
    accountManagerLogin: AccountManagerLoginEvent;
    accountManagerAddition: AccountManagerAdditionEvent;
    accountManagerRemoval: AccountManagerRemovalEvent;
    proxyCheckResult: ProxyCheckResultEvent;
    virtualScreen: VirtualScreenEvent;
    serverPinged: ServerPingedEvent;
    componentsUpdate: ComponentsUpdateEvent;
    scaleFactorChange: ScaleFactorChangeEvent;
    browserUrlChange: BrowserUrlChangeEvent;
    userLoggedIn: void;
    userLoggedOut: void;

    //WindowEvents.kt
    mouseButton: MouseButtonEvent;
    keyboardKey: KeyboardKeyEvent;
    keyboardChar: KeyboardCharEvent;

    //UserInterfaceEvents.kt
    fps: FpsChangeEvent;
    clientPlayerData: ClientPlayerDataEvent;
    contextualBar: ContextualBarEvent;
    clientPlayerEffect: ClientPlayerEffectEvent;
    clientPlayerInventory: ClientPlayerInventoryEvent;
    title: TitleEventTitle;
    subtitle: TitleEventSubtitle;
    titleFade: TitleEventFade;
    clearTitle: TitleEventClear;
    closedCaptions: ClosedCaptionsEvent;

    //GameEvents.kt
    key: KeyEvent;
    keybindChange: void;
    session: SessionEvent;
    chatSend: ChatSendEvent;
    chatReceive: ChatReceiveEvent;
    disconnect: void;
    overlayMessage: OverlayMessageEvent;

    //PlayerEvents.kt
    death: void;
}

export * from "./events/accounts";
export * from "./events/client";
export * from "./events/game";
export * from "./events/hud";
