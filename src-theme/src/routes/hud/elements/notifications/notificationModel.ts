import type {NotificationSeverity} from "../../../../integration/events";

export interface NotificationFilterSettings {
    severities?: NotificationSeverity[];
}

export function notificationSeverityEnabled(
    settings: NotificationFilterSettings | undefined,
    severity: NotificationSeverity,
): boolean {
    return settings?.severities?.includes(severity) ?? true;
}
