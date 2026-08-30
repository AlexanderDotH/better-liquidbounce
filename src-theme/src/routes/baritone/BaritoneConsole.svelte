<script lang="ts">
    import type {BaritoneCommandResult, BaritoneLogEntry} from "../../integration/baritone";
    import BaritoneField from "./BaritoneField.svelte";
    import type {BaritoneDashboardFields} from "./baritoneDashboardState.ts";

    let {fields, logs, canStartTask, busy, nativeTextInput, onInput, onRun, onSelectCompletion} = $props<{
        fields: BaritoneDashboardFields;
        logs: BaritoneLogEntry[];
        canStartTask: boolean;
        busy: boolean;
        nativeTextInput: boolean;
        onInput: (value: string) => void;
        onRun: (command: string) => Promise<BaritoneCommandResult | undefined>;
        onSelectCompletion: (completion: string) => void;
    }>();

    async function run(): Promise<void> {
        const command = fields.consoleInput.trim();
        if (!command) return;
        const result = await onRun(command);
        if (!result) return;
        fields.consoleOutput = result.output ?? (result.accepted ? "Command accepted." : "Command rejected.");
        fields.consoleInput = "";
        fields.completions = [];
    }
</script>

<section class="console-layout" aria-labelledby="console-heading">
    <div class="section-heading">
        <div><h3 id="console-heading">Advanced console</h3><p>Send the full upstream command set without enabling Baritone chat control.</p></div>
        <span>{logs.length} messages</span>
    </div>
    <div class="console-window" role="log" aria-live="polite" aria-label="Baritone output">
        {#if logs.length}
            {#each logs as entry (entry.revision)}
                <div class:error-log={entry.level === "ERROR"} class:warning-log={entry.level === "WARNING"}>
                    <time>{entry.timestamp}</time><strong>[{entry.level}]</strong><span>{entry.message}</span>
                </div>
            {/each}
        {:else}
            <p>No output yet.</p>
        {/if}
        {#if fields.consoleOutput}<div class="command-output"><strong>[RESULT]</strong><span>{fields.consoleOutput}</span></div>{/if}
    </div>
    <div class="console-command">
        <BaritoneField
                id="baritone-console-input"
                label="Command"
                value={fields.consoleInput}
                onValueChange={onInput}
                placeholder="goto 120 64 -30"
                disabled={!canStartTask || busy}
                {nativeTextInput}
        />
        <button class="primary" type="button" disabled={!canStartTask || busy || !fields.consoleInput.trim()} onclick={run}>Run</button>
    </div>
    {#if fields.completions.length}
        <div class="completion-list" aria-label="Command completions">
            {#each fields.completions as completion}
                <button type="button" onclick={() => onSelectCompletion(completion)}>{completion}</button>
            {/each}
        </div>
    {/if}
</section>
