<script lang="ts">
    export let text: string;
    export let icon: string | null = null;
    export let tooltip: string | null = null;
    export let success = false;
</script>

<div class="tag" class:success title={tooltip ?? undefined}>
    {#if icon}
        <img src={icon} alt="">
    {/if}
    <span>{text}</span>
</div>

<style lang="scss">

  .tag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    background-color: var(--menu-list-tag-background-color);
    color: var(--menu-text-color);
    font-size: 12px;
    padding: 3px 10px;
    border-radius: 20px;
    margin-left: 10px;
    transition: ease color .2s, ease background-color .2s;
    transform: translateY(-3px);

    &.success {
      background-color: color-mix(in srgb, var(--success-color) 20%, transparent);
      color: var(--success-color);
    }

    img {
      width: 16px;
      height: 16px;
      border-radius: 3px;
      image-rendering: pixelated;
    }
  }
</style>
