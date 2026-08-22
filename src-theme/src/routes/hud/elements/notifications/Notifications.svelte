<script lang="ts">
    import {flip} from "svelte/animate";
    import {onDestroy} from "svelte";
    import {listen} from "../../../../integration/ws";
    import {fly} from "svelte/transition";
    import Notification from "./Notification.svelte";
    import type {NotificationEvent, NotificationSeverity} from "../../../../integration/events";
    import {hudMotionDuration, prefersReducedMotion} from "../../motion/hudMotion";
    import {notificationSeverityEnabled} from "./notificationModel";

    interface TNotification {
        id: number;
        title: string;
        severity: NotificationSeverity;
        message: string;
    }

    export let variant: "classic" | "modern" = "classic";
    export let settings: Partial<HudNotificationsSettings> = {};

    let notifications: TNotification[] = [];
    let nextNotificationId = 1;
    let motionDuration = 200;
    let motionOffset = 30;

    const expiryTimers = new Map<number, number>();

    $: motionDuration = hudMotionDuration(variant, $prefersReducedMotion);
    $: motionOffset = variant === "modern" ? 18 : 30;

    function isModuleToggle(severity: string) {
        return severity === "ENABLED" || severity === "DISABLED";
    }

    function clearExpiry(id: number) {
        const existingTimer = expiryTimers.get(id);
        if (existingTimer === undefined) {
            return;
        }

        clearTimeout(existingTimer);
        expiryTimers.delete(id);
    }

    function removeNotification(id: number) {
        notifications = notifications.filter((notification) => notification.id !== id);
        expiryTimers.delete(id);
    }

    function scheduleExpiry(id: number) {
        clearExpiry(id);
        expiryTimers.set(id, window.setTimeout(() => removeNotification(id), 3000));
    }

    function addNotification(title: string, message: string, severity: NotificationSeverity) {
        if (!notificationSeverityEnabled(settings, severity)) {
            return;
        }

        const existingNotification = isModuleToggle(severity)
            ? notifications.find((notification) =>
                isModuleToggle(notification.severity) && notification.message === message)
            : undefined;

        if (existingNotification) {
            const updatedNotification = {
                ...existingNotification,
                title,
                message,
                severity,
            };

            notifications = [
                updatedNotification,
                ...notifications.filter((notification) => notification.id !== existingNotification.id),
            ];
            scheduleExpiry(existingNotification.id);
            return;
        }

        const id = nextNotificationId++;
        notifications = [
            {id, title, message, severity},
            ...notifications,
        ];
        scheduleExpiry(id);
    }

    listen("notification", (e: NotificationEvent) => {
        addNotification(e.title, e.message, e.severity);
    });

    onDestroy(() => {
        for (const timeout of expiryTimers.values()) {
            clearTimeout(timeout);
        }
        expiryTimers.clear();
    });
</script>

<div class="notifications">
    {#each notifications as notification (notification.id)}
        <div
                animate:flip={{ duration: motionDuration }}
                in:fly={{ x: motionOffset, duration: motionDuration }}
                out:fly={{ x: motionOffset, duration: motionDuration }}
        >
            <Notification
                title={notification.title}
                message={notification.message}
                severity={notification.severity}
                {variant}
            />
        </div>
    {/each}
</div>
