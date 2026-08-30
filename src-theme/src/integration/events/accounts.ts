import type {ItemStack, Proxy, Server, Setting} from "../types";

export interface AccountManagerAdditionEvent {
    username: string | null;
    error: string | null;
}

export interface AccountManagerRemovalEvent {
    username: string | null;
}

export interface AccountManagerMessageEvent {
    message: string;
}

export interface AccountManagerLoginEvent {
    username: string | null;
    error: string | null;
}

export interface ServerPingedEvent {
    server: Server;
}

export interface ClientPlayerInventoryEvent {
    inventory: PlayerInventory;
}

export interface PlayerInventory {
    armor: ItemStack[];
    main: ItemStack[];
    crafting: ItemStack[];
    enderChest: ItemStack[];
}

export interface ProxyCheckResultEvent {
    proxy: Proxy | null;
    error: string | null;
}

export interface SpaceSeperatedNamesChangeEvent {
    value: boolean;
}

export interface BrowserUrlChangeEvent {
    index: number;
    url: string;
}

export interface ValueChangedEvent {
    value: Setting<any>;
}
