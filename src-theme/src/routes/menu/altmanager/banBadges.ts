import type {AccountBan, Server} from "../../../integration/types.js";

const HOUR_IN_MILLISECONDS = 60 * 60 * 1000;
const HOURS_PER_DAY = 24;

export function activeBans(bans: Record<string, AccountBan>, now = Date.now()): AccountBan[] {
    return Object.values(bans).filter(ban => ban.bannedUntil === -1 || ban.bannedUntil > now);
}

export function availableServers(
    workingServers: string[],
    bans: Record<string, AccountBan>,
    now = Date.now(),
): string[] {
    const bannedServers = activeBans(bans, now).map(ban => normalizeServerIdentifier(ban.serverName));
    return workingServers.filter(serverName => {
        const normalizedServerName = normalizeServerIdentifier(serverName);
        return !bannedServers.some(bannedServer => domainsMatch(normalizedServerName, bannedServer));
    });
}

export function formatRemainingBanTime(bannedUntil: number, now = Date.now()): string {
    if (bannedUntil === -1) {
        return "Permanent";
    }

    const remainingMilliseconds = bannedUntil - now;
    if (remainingMilliseconds <= 0) {
        return "Expired";
    }

    const totalHours = Math.ceil(remainingMilliseconds / HOUR_IN_MILLISECONDS);
    const days = Math.floor(totalHours / HOURS_PER_DAY);
    const hours = totalHours % HOURS_PER_DAY;
    const dayText = `${days} ${days === 1 ? "day" : "days"}`;
    const hourText = `${hours} ${hours === 1 ? "hour" : "hours"}`;

    if (days === 0) {
        return hourText;
    }
    if (hours === 0) {
        return dayText;
    }
    return `${dayText} and ${hourText}`;
}

export function findServerIcon(
    serverName: string,
    servers: Pick<Server, "address" | "name" | "icon">[],
): string | null {
    const normalizedServerName = normalizeServerIdentifier(serverName);
    const server = servers.find(candidate => {
        const normalizedAddress = normalizeServerIdentifier(candidate.address);
        const normalizedName = normalizeServerIdentifier(candidate.name);

        return normalizedName === normalizedServerName
            || domainsMatch(normalizedAddress, normalizedServerName);
    });

    return server?.icon || null;
}

function normalizeServerIdentifier(value: string): string {
    return value.trim().toLowerCase().replace(/:\d+$/, "").replace(/\.$/, "");
}

function domainsMatch(first: string, second: string): boolean {
    return first === second || first.endsWith(`.${second}`) || second.endsWith(`.${first}`);
}
