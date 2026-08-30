<script lang="ts">
    import {cefTextInput} from "../../../../integration/input/cefTextInput";

    let {
        input = $bindable(), query, loading, error, resultsOpen, selectedResultId, allowNativeInput,
        onQuery, onFocus, onKeydown, onClear,
    } = $props<{
        input?: HTMLInputElement;
        query: string;
        loading: boolean;
        error: string | null;
        resultsOpen: boolean;
        selectedResultId?: string;
        allowNativeInput: boolean;
        onQuery: (value: string) => void;
        onFocus: () => void;
        onKeydown: (event: KeyboardEvent) => void;
        onClear: () => void;
    }>();
</script>

<svg class="search-icon" aria-hidden="true" viewBox="0 0 20 20">
    <path d="M8.8 3a5.8 5.8 0 1 0 3.5 10.4l3.8 3.8 1.2-1.2-3.8-3.8A5.8 5.8 0 0 0 8.8 3Zm0 1.7a4.1 4.1 0 1 1 0 8.2 4.1 4.1 0 0 1 0-8.2Z"/>
</svg>
<input
        class="search-input"
        type="text"
        value={query}
        placeholder={loading ? "Loading modules…" : "Search modules"}
        aria-label="Search modules"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={resultsOpen}
        aria-controls="modern-search-results"
        aria-busy={loading}
        aria-describedby={error ? "modern-search-error" : undefined}
        aria-activedescendant={selectedResultId}
        autocomplete="off"
        spellcheck="false"
        readonly={!allowNativeInput}
        bind:this={input}
        use:cefTextInput={{getValue: () => query, onChange: onQuery}}
        oninput={event => onQuery((event.currentTarget as HTMLInputElement).value)}
        onfocus={onFocus}
        onkeydown={onKeydown}
/>
{#if query}
    <button class="clear-button" type="button" aria-label="Clear search" onclick={onClear}>
        <svg aria-hidden="true" viewBox="0 0 16 16"><path d="m4 3 4 4 4-4 1 1-4 4 4 4-1 1-4-4-4 4-1-1 4-4-4-4 1-1Z"/></svg>
    </button>
{:else}
    <span class="shortcut-hint" aria-hidden="true">Type to search</span>
{/if}
