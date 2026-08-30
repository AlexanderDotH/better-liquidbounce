<script lang="ts">
    import type {BaritoneTaskRequest} from "../../integration/baritone";
    import BaritoneField from "./BaritoneField.svelte";
    import {
        buildBaritoneTaskRequest,
        composerDescription,
        composerHeading,
        describeComposerError,
        type BaritoneComposerKind,
    } from "./baritoneTaskModel";

    let {
        kind,
        disabled = false,
        busy = false,
        blockOptions = [],
        playerOptions = [],
        nativeTextInput = false,
        onSubmit,
    } = $props<{
        kind: BaritoneComposerKind;
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
            await onSubmit(buildBaritoneTaskRequest(kind, {
                x, y, z, block, player, count, radius, file, navigateTarget,
            }));
        } catch (error) {
            localError = describeComposerError(error);
        }
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
  @use "./BaritoneTaskComposer.styles";
</style>
