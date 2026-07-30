<div class="modern-watermark" aria-label="LiquidBounce Modern HUD">
    <span class="brand-mark" aria-hidden="true">
        <img src="img/menu/icon-liquidbounce.svg" alt="" />
    </span>
    <span class="brand-copy">
        <strong>LiquidBounce</strong>
        <small>Modern HUD</small>
    </span>
    <span class="status-dot" aria-hidden="true"></span>
</div>

<style lang="scss">
  .modern-watermark {
    display: inline-flex;
    align-items: center;
    gap: 9px;
    min-height: 38px;
    width: max-content;
    padding: 5px 10px 5px 5px;
    color: #eef1f5;
    background: rgba(15, 18, 23, 0.9);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 999px;
    box-shadow:
      0 10px 28px rgba(0, 0, 0, 0.24),
      inset 0 1px rgba(255, 255, 255, 0.035);
  }

  .brand-mark {
    display: grid;
    place-items: center;
    width: 28px;
    height: 28px;
    background: color-mix(in srgb, var(--accent-color) 78%, #151923);
    border: 1px solid color-mix(in srgb, var(--accent-color) 55%, white 12%);
    border-radius: 50%;
  }

  .brand-mark img {
    width: 13px;
    height: 13px;
  }

  .brand-copy {
    display: grid;
    gap: 1px;
    line-height: 1;
  }

  .brand-copy strong {
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.01em;
  }

  .brand-copy small {
    color: #919aa6;
    font-size: 8px;
    font-weight: 500;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  .status-dot {
    width: 5px;
    height: 5px;
    margin-left: 1px;
    background: var(--accent-color);
    border-radius: 50%;
    box-shadow: 0 0 8px color-mix(in srgb, var(--accent-color) 70%, transparent);
  }
</style>
