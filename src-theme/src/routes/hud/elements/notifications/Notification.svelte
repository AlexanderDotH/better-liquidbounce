<script lang="ts">
    export let title: string;
    export let message: string;
    export let severity: string;
    export let variant: "classic" | "modern" = "classic";

    $: isModuleToggle = severity === "ENABLED" || severity === "DISABLED";
</script>

{#if variant === "modern" && isModuleToggle}
    <div
        class="notification module-toggle-notification {severity.toString().toLowerCase()}"
        role="status"
    >
        <span class="status-dot" aria-hidden="true"></span>
        <strong class="module-name">{message}</strong>
        <span class="state">{title}</span>
    </div>
{:else}
    <div class="notification">
        <div class="icon {severity.toString().toLowerCase()}"></div>
        <div class="title">{title}</div>
        <div class="message">{message}</div>
    </div>
{/if}

<style lang="scss">
  .notification {
    display: grid;
    grid-template-areas:
            "a b"
            "a c";
    grid-template-columns: max-content 1fr;
    column-gap: 10px;
    background: var(--notification-background-color);
    border-radius: 5px;
    width: 300px;
    overflow: hidden;
    padding: 10px;
    margin-bottom: 10px;
  }

  .icon {
    height: 40px;
    width: 40px;
    background-position: center;
    background-repeat: no-repeat;
    border-radius: 4px;
    grid-area: a;
    transition: background-color 0.2s;
    position: relative;
    background-image: url("/img/hud/notification/icon-toggle.svg");

    &.success {
      background-color: var(--notification-success-color);
      background-image: url("/img/hud/notification/icon-success.svg");
    }

    &.error {
      background-color: var(--notification-error-color);
      background-image: url("/img/hud/notification/icon-error.svg");
    }

    &.info {
      background-color: var(--notification-info-color);
      background-image: url("/img/hud/notification/icon-info.svg");
    }

    &.disabled,
    &.enabled {
      &::after {
        content: "";
        position: absolute;
        height: 10px;
        width: 10px;
        border-radius: 5px;
        top: 50%;
        transform: translate(-50%, -50%);
        background: var(--notification-toggle-knob-color);
        transition: all 0.2s ease-out;
      }
    }

    &.enabled {
      background-color: var(--notification-success-color);

      &::after {
        left: 62%;
      }
    }

    &.disabled {
      background-color: var(--notification-error-color);

      &::after {
        left: 38%;
      }
    }
  }

  .title {
    grid-area: b;
    font-size: 14px;
    color: var(--notification-title-color);
    font-weight: 600;
  }

  .message {
    grid-area: c;
    font-size: 12px;
    color: var(--notification-message-color);
  }

  .module-toggle-notification {
    display: inline-flex;
    grid-template-areas: none;
    grid-template-columns: none;
    align-items: center;
    gap: 7px;
    width: max-content;
    max-width: 240px;
    min-height: 30px;
    padding: 0 10px;
    margin-bottom: 8px;
    color: var(--notification-title-color);
    background: var(--notification-background-color);
    border: 0;
    border-radius: 999px;
    box-shadow: 0 8px 22px rgba(0, 0, 0, 0.22);
  }

  .status-dot {
    flex: 0 0 auto;
    width: 6px;
    height: 6px;
    background: #7d8795;
    border-radius: 50%;
  }

  .module-toggle-notification.enabled .status-dot {
    background: #4677ff;
    box-shadow: 0 0 8px rgba(70, 119, 255, 0.52);
  }

  .module-name {
    overflow: hidden;
    color: var(--notification-title-color);
    font-size: 12px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .state {
    flex: 0 0 auto;
    color: var(--notification-message-color);
    font-size: 10px;
    font-weight: 550;
  }
</style>
