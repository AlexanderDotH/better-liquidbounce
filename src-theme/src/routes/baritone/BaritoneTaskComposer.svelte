<script lang="ts">
    import type {BaritoneTaskRequest, BaritoneTaskType} from "../../integration/baritone";
    import BaritoneField from "./BaritoneField.svelte";

    type ComposerKind = "navigate" | "mine" | "follow" | "farm" | "explore" | "build" | "elytra";

    let {
        kind,
        disabled = false,
        busy = false,
        blockOptions = [],
        playerOptions = [],
        nativeTextInput = false,
        onSubmit,
    } = $props<{
        kind: ComposerKind;
        disabled?: boolean;
        busy?: boolean;
        blockOptions?: string[];
        playerOptions?: string[];
        nativeTextInput?: boolean;
        onSubmit: (request: BaritoneTaskRequest) => Promise<void>;
    }>();

    let x = $state("0");
    let y = $state("64");
    let z = $state("0");
    let block = $state("minecraft:stone");
    let player = $state("");
    let count = $state("16");
    let radius = $state("64");
    let file = $state("");
    let navigateTarget = $state<"coordinates" | "block">("coordinates");
    let localError = $state<string | null>(null);

    let heading = $derived(composerHeading(kind));
    let description = $derived(composerDescription(kind));

    async function submit(event: SubmitEvent): Promise<void> {
        event.preventDefault();
        localError = null;
        try {
            await onSubmit(buildRequest());
        } catch (error) {
            localError = describeError(error);
        }
    }

    function buildRequest(): BaritoneTaskRequest {
        switch (kind) {
            case "navigate":
                return navigateRequest();
            case "mine":
                return {type: "MINE", block: required(block, "Block"), count: positiveInteger(count, "Count")};
            case "follow":
                return {type: "FOLLOW", player: required(player, "Player")};
            case "farm":
                return {type: "FARM", radius: positiveNumber(radius, "Radius")};
            case "explore":
                return {type: "EXPLORE", x: finiteNumber(x, "X"), z: finiteNumber(z, "Z"), radius: positiveNumber(radius, "Radius")};
            case "build":
                return {
                    type: "BUILD",
                    file: required(file, "Schematic"),
                    x: finiteNumber(x, "X"),
                    y: finiteNumber(y, "Y"),
                    z: finiteNumber(z, "Z"),
                };
            case "elytra":
                return coordinateRequest("ELYTRA");
        }

        throw new Error(`Unsupported Baritone task composer: ${kind}`);
    }

    function navigateRequest(): BaritoneTaskRequest {
        if (navigateTarget === "block") {
            return {type: "GET_TO_BLOCK", block: required(block, "Block")};
        }
        return coordinateRequest("GOTO");
    }

    function coordinateRequest(type: BaritoneTaskType): BaritoneTaskRequest {
        return {
            type,
            x: finiteNumber(x, "X"),
            y: finiteNumber(y, "Y"),
            z: finiteNumber(z, "Z"),
        };
    }

    function finiteNumber(value: string, field: string): number {
        const parsed = Number(value);
        if (Number.isFinite(parsed)) {
            return parsed;
        }
        throw new Error(`${field} must be a finite number.`);
    }

    function positiveNumber(value: string, field: string): number {
        const parsed = finiteNumber(value, field);
        if (parsed > 0) {
            return parsed;
        }
        throw new Error(`${field} must be greater than zero.`);
    }

    function positiveInteger(value: string, field: string): number {
        const parsed = positiveNumber(value, field);
        if (Number.isInteger(parsed)) {
            return parsed;
        }
        throw new Error(`${field} must be a whole number.`);
    }

    function required(value: string, field: string): string {
        const trimmed = value.trim();
        if (trimmed) {
            return trimmed;
        }
        throw new Error(`${field} is required.`);
    }

    function describeError(error: unknown): string {
        return error instanceof Error && error.message ? error.message : "Unable to create the task.";
    }

    function composerHeading(value: ComposerKind): string {
        return ({
            navigate: "Choose a destination",
            mine: "Mine a block",
            follow: "Follow a player",
            farm: "Maintain an area",
            explore: "Explore from a center",
            build: "Build a schematic",
            elytra: "Elytra pathing",
        })[value];
    }

    function composerDescription(value: ComposerKind): string {
        return ({
            navigate: "Path to coordinates or the nearest matching block.",
            mine: "Find and collect a target block up to the requested count.",
            follow: "Keep a safe route to a named player as they move.",
            farm: "Harvest and replant Baritone-supported crops inside the radius.",
            explore: "Generate terrain outward from the selected center.",
            build: "Load a supported schematic from Baritone's schematics directory.",
            elytra: "Calculate a long-distance Elytra route to exact coordinates.",
        })[value];
    }
</script>

<section class="task-composer" aria-labelledby={`baritone-${kind}-heading`}>
    <header>
        <div>
            <p class="eyebrow">New task</p>
            <h2 id={`baritone-${kind}-heading`}>{heading}</h2>
            <p>{description}</p>
        </div>
        <span class="task-kind">{kind}</span>
    </header>

    <form onsubmit={submit}>
        {#if kind === "navigate"}
            <label class="select-field">
                <span>Target type</span>
                <select bind:value={navigateTarget} disabled={disabled || busy}>
                    <option value="coordinates">Coordinates</option>
                    <option value="block">Nearest block</option>
                </select>
            </label>
            {#if navigateTarget === "block"}
                <BaritoneField
                        id="baritone-navigate-block"
                        label="Block identifier"
                        value={block}
                        onValueChange={value => block = value}
                        list="baritone-block-options"
                        placeholder="minecraft:ancient_debris"
                        {disabled}
                        {nativeTextInput}
                />
            {:else}
                <div class="coordinate-grid">
                    <BaritoneField id="baritone-navigate-x" label="X" value={x} onValueChange={value => x = value} inputMode="decimal" {disabled} {nativeTextInput}/>
                    <BaritoneField id="baritone-navigate-y" label="Y" value={y} onValueChange={value => y = value} inputMode="decimal" {disabled} {nativeTextInput}/>
                    <BaritoneField id="baritone-navigate-z" label="Z" value={z} onValueChange={value => z = value} inputMode="decimal" {disabled} {nativeTextInput}/>
                </div>
            {/if}
        {:else if kind === "mine"}
            <div class="form-grid">
                <BaritoneField id="baritone-mine-block" label="Block identifier" value={block} onValueChange={value => block = value} list="baritone-block-options" placeholder="minecraft:diamond_ore" {disabled} {nativeTextInput}/>
                <BaritoneField id="baritone-mine-count" label="Count" value={count} onValueChange={value => count = value} inputMode="numeric" {disabled} {nativeTextInput}/>
            </div>
        {:else if kind === "follow"}
            <BaritoneField
                    id="baritone-follow-player"
                    label="Player name"
                    value={player}
                    onValueChange={value => player = value}
                    list="baritone-player-options"
                    placeholder="Player"
                    help="A plain player-name field; suggestions only include current non-bot remote players."
                    {disabled}
                    {nativeTextInput}
            />
        {:else if kind === "farm"}
            <BaritoneField
                    id="baritone-farm-radius"
                    label="Farm radius"
                    value={radius}
                    onValueChange={value => radius = value}
                    inputMode="decimal"
                    help="Baritone determines supported crops within this area."
                    {disabled}
                    {nativeTextInput}
            />
        {:else if kind === "explore"}
            <div class="coordinate-grid">
                <BaritoneField id="baritone-explore-x" label="Center X" value={x} onValueChange={value => x = value} inputMode="decimal" {disabled} {nativeTextInput}/>
                <BaritoneField id="baritone-explore-z" label="Center Z" value={z} onValueChange={value => z = value} inputMode="decimal" {disabled} {nativeTextInput}/>
                <BaritoneField id="baritone-explore-radius" label="Radius" value={radius} onValueChange={value => radius = value} inputMode="decimal" {disabled} {nativeTextInput}/>
            </div>
        {:else if kind === "build"}
            <BaritoneField
                    id="baritone-build-file"
                    label="Schematic file"
                    value={file}
                    onValueChange={value => file = value}
                    placeholder="castle.schematic"
                    help="Resolved by the backend inside Baritone's schematics directory."
                    {disabled}
                    {nativeTextInput}
            />
            <div class="coordinate-grid">
                <BaritoneField id="baritone-build-x" label="Origin X" value={x} onValueChange={value => x = value} inputMode="decimal" {disabled} {nativeTextInput}/>
                <BaritoneField id="baritone-build-y" label="Origin Y" value={y} onValueChange={value => y = value} inputMode="decimal" {disabled} {nativeTextInput}/>
                <BaritoneField id="baritone-build-z" label="Origin Z" value={z} onValueChange={value => z = value} inputMode="decimal" {disabled} {nativeTextInput}/>
            </div>
        {:else if kind === "elytra"}
            <div class="coordinate-grid">
                <BaritoneField id="baritone-elytra-x" label="X" value={x} onValueChange={value => x = value} inputMode="decimal" {disabled} {nativeTextInput}/>
                <BaritoneField id="baritone-elytra-y" label="Y" value={y} onValueChange={value => y = value} inputMode="decimal" {disabled} {nativeTextInput}/>
                <BaritoneField id="baritone-elytra-z" label="Z" value={z} onValueChange={value => z = value} inputMode="decimal" {disabled} {nativeTextInput}/>
            </div>
        {/if}

        {#if localError}
            <p class="form-error" role="alert">{localError}</p>
        {/if}

        <div class="form-actions">
            <button class="primary" type="submit" disabled={disabled || busy}>
                {busy ? "Starting…" : "Start task"}
            </button>
            {#if disabled}
                <span>Join a world and enable Baritone to start.</span>
            {/if}
        </div>
    </form>

    <datalist id="baritone-block-options">
        {#each blockOptions as option}
            <option value={option}></option>
        {/each}
    </datalist>
    <datalist id="baritone-player-options">
        {#each playerOptions as option}
            <option value={option}></option>
        {/each}
    </datalist>
</section>

<style lang="scss">
  .task-composer {
    display: grid;
    gap: 22px;
  }

  header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 20px;
  }

  .eyebrow {
    margin: 0 0 5px;
    color: color-mix(in srgb, var(--accent-color) 75%, #cbd0d7);
    font-size: 10px;
    font-weight: 750;
    letter-spacing: 0.13em;
    text-transform: uppercase;
  }

  h2 {
    margin: 0;
    color: var(--baritone-text-primary, #eef1f5);
    font-size: 20px;
    line-height: 1.2;
  }

  header p:last-child {
    max-width: 620px;
    margin: 7px 0 0;
    color: var(--baritone-text-muted, #8d96a3);
    font-size: 12px;
    line-height: 1.5;
  }

  .task-kind {
    padding: 6px 9px;
    color: var(--baritone-text-secondary, #aeb5bf);
    background: var(--baritone-surface-raised, rgba(255, 255, 255, 0.05));
    border: 1px solid var(--baritone-border, rgba(255, 255, 255, 0.1));
    border-radius: 999px;
    font: 650 9px/1 "JetBrains Mono", monospace;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  form {
    display: grid;
    gap: 16px;
  }

  .coordinate-grid,
  .form-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
  }

  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .select-field {
    display: grid;
    gap: 6px;
  }

  .select-field span {
    color: var(--baritone-text-secondary, #aeb5bf);
    font-size: 11px;
    font-weight: 650;
  }

  select {
    height: 36px;
    padding: 0 11px;
    color: var(--baritone-text-primary, #eef1f5);
    background: #171b21;
    border: 1px solid var(--baritone-border, rgba(255, 255, 255, 0.1));
    border-radius: 8px;
    outline: none;
  }

  select:focus-visible,
  button:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--accent-color) 72%, white);
    outline-offset: 2px;
  }

  .form-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    padding-top: 4px;
  }

  .form-actions span {
    color: var(--baritone-text-muted, #8d96a3);
    font-size: 10px;
  }

  button.primary {
    min-width: 112px;
    height: 36px;
    padding: 0 15px;
    color: #0b0d10;
    background: color-mix(in srgb, var(--accent-color) 78%, white);
    border: 0;
    border-radius: 8px;
    font-size: 11px;
    font-weight: 750;
    cursor: pointer;
    transition:
      filter var(--baritone-motion-duration, 140ms) ease,
      transform var(--baritone-motion-duration, 140ms) ease;
  }

  button.primary:hover:not(:disabled) {
    filter: brightness(1.08);
    transform: translateY(-1px);
  }

  button:disabled {
    cursor: not-allowed;
    opacity: 0.45;
  }

  .form-error {
    margin: 0;
    padding: 9px 11px;
    color: #ffc2c2;
    background: rgba(150, 40, 45, 0.15);
    border: 1px solid rgba(255, 100, 108, 0.25);
    border-radius: 8px;
    font-size: 11px;
  }

  @media (max-width: 720px) {
    .coordinate-grid,
    .form-grid {
      grid-template-columns: 1fr;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    button.primary {
      transition: none;
    }

    button.primary:hover:not(:disabled) {
      transform: none;
    }
  }
</style>
