export type TheAlteningGenerationResult =
    | { status: "SUCCESS"; username: string; message?: undefined }
    | { status: "CREDENTIALS_REQUIRED"; message: string; username?: undefined }
    | { status: "ACCESS_DENIED"; message: string; username?: undefined }
    | { status: "ERROR"; message: string; username?: undefined };

export interface Session {
    username: string;
    type: string;
    service: string;
    avatar: string;
    online: boolean;
    uuid: string;
}

export interface Server {
    id: number;
    address: string;
    icon: string;
    label: TextComponent | string;
    players: {
        max: number;
        online: number;
    };
    name: string;
    online: boolean;
    playerCountLabel: string;
    protocolVersion: number;
    version: string;
    ping: number;
    resourcePackPolicy: string;
    lan?: boolean;
}

export interface TextComponent {
    type?: string;
    extra?: (TextComponent | string)[];
    color: string;
    bold?: boolean;
    italic?: boolean;
    underlined?: boolean;
    strikethrough?: boolean;
    obfuscated?: boolean;
    font?: string;
    text: string;
}

export interface Protocol {
    name: string;
    version: number;
}

export interface FritzBoxReconnectResult {
    oldIp: string | null;
    newIp: string | null;
}

export interface FritzBoxReconnectRequest {
    password?: string;
}

export interface Account {
    avatar: string;
    bans: Record<string, AccountBan>;
    workingServers: string[];
    favorite: boolean;
    id: number;
    type: string;
    username: string;
    uuid: string;
}

export interface AccountBan {
    serverName: string;
    reason: string;
    bannedUntil: number;
}

export interface World {
    id: number;
    name: string;
    displayName: string;
    lastPlayed: number;
    gameMode: string;
    difficulty: string;
    icon: string | undefined;
    hardcore: boolean;
    commandsAllowed: boolean;
    version: string;
}

export interface Proxy {
    id: number;
    host: string;
    port: number;
    type: 'HTTP' | 'SOCKS5';
    forwardAuthentication: boolean;
    favorite: boolean;
    credentials: {
        username: string;
        password: string;
    } | undefined;
    ipInfo: {
        city?: string;
        country?: string;
        ip: string;
        loc?: string;
        org?: string;
        postal?: string;
        region?: string;
        timezone?: string;
    } | undefined;
}
