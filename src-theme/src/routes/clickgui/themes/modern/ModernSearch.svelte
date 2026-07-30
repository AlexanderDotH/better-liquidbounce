<script lang="ts">
    import {onMount} from "svelte";
    import type {ConfigurableSetting, Module} from "../../../../integration/types";
    import type {
        ClickGuiValueChangeEvent,
        KeyboardKeyEvent,
        ModuleToggleEvent,
    } from "../../../../integration/events";
    import {listen} from "../../../../integration/ws";
    import {isClickGuiScreen} from "../../../../util/utils";
    import {
        convertToSpacedString,
        spaceSeperatedNames,
    } from "../../../../theme/theme_config";
    import {highlightModuleName} from "../../clickgui_store";
    import {cefTextInput} from "../../setting/common/cefTextInput";
    import type {ClickGuiDataSource} from "./model/clickGuiDataSource";
    import {productionClickGuiDataSource} from "./model/clickGuiDataSource";
    import {
        clampSearchSelection,
        moveSearchSelection,
        readSearchBarAutoFocus,
    } from "./model/modernInteractionState";
    import {
        MODERN_RESULT_STAGGER_LIMIT,
        motionStaggerIndex,
    } from "./model/modernMotion";
    import {filterModulesBySearch} from "./model/moduleSearch";
    import {motionAwareScrollBehavior} from "./modernShellState";

    interface Props {
        dataSource?: ClickGuiDataSource;
        allowNativeInput?: boolean;
    }

    let {
        dataSource = productionClickGuiDataSource,
        allowNativeInput = false,
    }: Props = $props();

    let container = $state<HTMLElement>();
    let searchInput = $state<HTMLInputElement>();
    let modules = $state<Module[]>([]);
    let query = $state("");
    let selectedIndex = $state(0);
    let autoFocus = $state(true);
    let popoverOpen = $state(false);
    let loading = $state(true);
    let searchError = $state<string | null>(null);
    let pendingModuleNames = $state<string[]>([]);

    let filteredModules = $derived(filterModulesBySearch(modules, query));
    let selectedModule = $derived(filteredModules[selectedIndex]);
    let showResults = $derived(popoverOpen && query.trim().length > 0);

    $effect(() => {
        selectedIndex = clampSearchSelection(selectedIndex, filteredModules.length);
    });

    listen("moduleToggle", (event: ModuleToggleEvent) => {
        modules = modules.map(module => module.name === event.moduleName
            ? {...module, enabled: event.enabled}
            : module
        );
    });

    listen("keyboardKey", (event: KeyboardKeyEvent) => {
        if (!isClickGuiScreen(event.screen) || event.action === 0) {
            return;
        }

        if (autoFocus && document.activeElement === document.body) {
            searchInput?.focus();
        }

        if (document.activeElement !== searchInput) {
            return;
        }

        void handleMinecraftKey(event);
    });

    listen("clickGuiValueChange", (event: ClickGuiValueChangeEvent) => {
        if (event.configurable.name === "ClickGUI") {
            applyClickGuiSettings(event.configurable);
        }
    });

    onMount(() => {
        void initializeSearch();
    });

    async function initializeSearch(): Promise<void> {
        loading = true;
        searchError = null;

        const [moduleResult, clickGuiResult] = await Promise.allSettled([
            dataSource.getModules(),
            dataSource.getModuleSettings("ClickGUI"),
        ]);

        if (moduleResult.status === "fulfilled") {
            modules = moduleResult.value;
        } else {
            searchError = describeError(moduleResult.reason, "Modules could not be loaded.");
        }

        if (clickGuiResult.status === "fulfilled") {
            applyClickGuiSettings(clickGuiResult.value);
        }

        loading = false;
        if (autoFocus && moduleResult.status === "fulfilled") {
            requestAnimationFrame(() => searchInput?.focus());
        }
    }

    function applyClickGuiSettings(configurable: ConfigurableSetting): void {
        autoFocus = readSearchBarAutoFocus(configurable, autoFocus);
    }

    function updateQuery(value: string): void {
        query = value;
        selectedIndex = 0;
        popoverOpen = value.trim().length > 0;

        if (!value.trim()) {
            $highlightModuleName = null;
        }
    }

    function handleInput(event: Event): void {
        updateQuery((event.currentTarget as HTMLInputElement).value);
    }

    function handleBrowserKeydown(event: KeyboardEvent): void {
        if (!allowNativeInput) {
            if (isSearchCommandKey(event.key)) {
                event.preventDefault();
            }
            return;
        }

        switch (event.key) {
            case "ArrowDown":
                event.preventDefault();
                moveSelection(1);
                break;
            case "ArrowUp":
                event.preventDefault();
                moveSelection(-1);
                break;
            case "Enter":
                event.preventDefault();
                void toggleSelectedModule();
                break;
            case "Tab":
                if (selectedModule) {
                    event.preventDefault();
                    locateModule(selectedModule.name);
                }
                break;
            case "Escape":
                event.preventDefault();
                clearSearch();
                break;
        }
    }

    function isSearchCommandKey(key: string): boolean {
        return key === "ArrowDown"
            || key === "ArrowUp"
            || key === "Enter"
            || key === "Tab"
            || key === "Escape";
    }

    async function handleMinecraftKey(event: KeyboardKeyEvent): Promise<void> {
        switch (event.key) {
            case "key.keyboard.down":
                moveSelection(1);
                break;
            case "key.keyboard.up":
                moveSelection(-1);
                break;
            case "key.keyboard.enter":
                await toggleSelectedModule();
                break;
            case "key.keyboard.tab":
                if (selectedModule) {
                    locateModule(selectedModule.name);
                }
                break;
            case "key.keyboard.escape":
                clearSearch();
                break;
        }
    }

    function moveSelection(direction: -1 | 1): void {
        if (filteredModules.length === 0) {
            return;
        }

        popoverOpen = true;
        selectedIndex = moveSearchSelection(
            selectedIndex,
            filteredModules.length,
            direction,
        );
        scrollSelectedResultIntoView();
    }

    function scrollSelectedResultIntoView(): void {
        requestAnimationFrame(() => {
            document
                .getElementById(`modern-search-result-${selectedIndex}`)
                ?.scrollIntoView({
                    behavior: motionAwareScrollBehavior(prefersReducedMotion()),
                    block: "nearest",
                });
        });
    }

    function selectResult(module: Module): void {
        searchInput?.focus({preventScroll: true});
        void toggleModule(module);
    }

    async function toggleSelectedModule(): Promise<void> {
        if (selectedModule) {
            await toggleModule(selectedModule);
        }
    }

    async function toggleModule(module: Module): Promise<void> {
        if (isTogglePending(module.name)) {
            return;
        }

        const nextEnabled = !module.enabled;
        pendingModuleNames = [...pendingModuleNames, module.name];
        modules = modules.map(candidate => candidate.name === module.name
            ? {...candidate, enabled: nextEnabled}
            : candidate
        );
        searchError = null;

        try {
            await dataSource.setModuleEnabled(module.name, nextEnabled);
        } catch (error) {
            modules = modules.map(candidate => candidate.name === module.name
                ? {...candidate, enabled: module.enabled}
                : candidate
            );
            searchError = describeError(error, "Module state could not be changed.");
        } finally {
            pendingModuleNames = pendingModuleNames.filter(name => name !== module.name);
        }
    }

    function isTogglePending(name: string): boolean {
        return pendingModuleNames.includes(name);
    }

    function locateModule(name: string): void {
        $highlightModuleName = name;
        popoverOpen = false;
        searchInput?.blur();
    }

    function clearSearch(): void {
        query = "";
        selectedIndex = 0;
        popoverOpen = false;
        $highlightModuleName = null;
        searchInput?.blur();
    }

    function handleWindowPointerDown(event: PointerEvent): void {
        if (!container?.contains(event.target as Node)) {
            popoverOpen = false;
        }
    }

    function handleWindowKeydown(event: KeyboardEvent): void {
        if (!autoFocus || document.activeElement !== document.body) {
            return;
        }

        if (event.ctrlKey || event.metaKey || event.altKey) {
            return;
        }

        searchInput?.focus();
    }

    function displayName(value: string): string {
        return $spaceSeperatedNames ? convertToSpacedString(value) : value;
    }

    function describeError(error: unknown, fallback: string): string {
        if (!(error instanceof Error) || !error.message.trim()) {
            return fallback;
        }

        return `${fallback} ${error.message}`;
    }

    function prefersReducedMotion(): boolean {
        return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    }
</script>

<svelte:window
        onpointerdown={handleWindowPointerDown}
        onkeydown={handleWindowKeydown}
/>

<div class="modern-search" bind:this={container}>
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
            aria-expanded={showResults}
            aria-controls="modern-search-results"
            aria-busy={loading}
            aria-describedby={searchError ? "modern-search-error" : undefined}
            aria-activedescendant={showResults && selectedModule
                ? `modern-search-result-${selectedIndex}`
                : undefined}
            autocomplete="off"
            spellcheck="false"
            readonly={!allowNativeInput}
            bind:this={searchInput}
            use:cefTextInput={{
                getValue: () => query,
                onChange: updateQuery,
            }}
            oninput={handleInput}
            onfocus={() => {
                if (query.trim()) {
                    popoverOpen = true;
                }
            }}
            onkeydown={handleBrowserKeydown}
    />

    {#if query}
        <button class="clear-button" type="button" aria-label="Clear search" onclick={clearSearch}>
            <svg aria-hidden="true" viewBox="0 0 16 16">
                <path d="m4 3 4 4 4-4 1 1-4 4 4 4-1 1-4-4-4 4-1-1 4-4-4-4 1-1Z"/>
            </svg>
        </button>
    {:else}
        <span class="shortcut-hint" aria-hidden="true">Type to search</span>
    {/if}

    {#if showResults}
        <div id="modern-search-results" class="results" role="listbox" aria-label="Module search results">
            {#if filteredModules.length > 0}
                {#each filteredModules as module, index (module.name)}
                    <button
                            id="modern-search-result-{index}"
                            class="result"
                            class:selected={selectedIndex === index}
                            class:enabled={module.enabled}
                            type="button"
                            role="option"
                            tabindex="-1"
                            aria-selected={selectedIndex === index}
                            aria-busy={isTogglePending(module.name)}
                            disabled={isTogglePending(module.name)}
                            style:--modern-result-enter-index={motionStaggerIndex(index, MODERN_RESULT_STAGGER_LIMIT)}
                            onclick={() => selectResult(module)}
                            onmouseenter={() => selectedIndex = index}
                            oncontextmenu={(event) => {
                                event.preventDefault();
                                locateModule(module.name);
                            }}
                    >
                        <span class="result-state" aria-hidden="true"></span>
                        <span class="result-copy">
                            <strong>{displayName(module.name)}</strong>
                            {#if module.aliases.length > 0}
                                <span>{module.aliases.map(displayName).join(", ")}</span>
                            {/if}
                        </span>
                        <span class="result-action">{module.enabled ? "On" : "Off"}</span>
                    </button>
                {/each}
            {:else}
                <div class="empty-result" role="status">No modules match “{query}”</div>
            {/if}

            <div class="result-footer" aria-hidden="true">
                <span>↑↓ Select</span>
                <span>Enter Toggle</span>
                <span>Tab Locate</span>
            </div>
        </div>
    {/if}

    {#if searchError}
        <div id="modern-search-error" class="search-error" role="alert">{searchError}</div>
    {/if}
</div>

<style lang="scss">
  .modern-search {
    position: relative;
    width: 100%;
    min-width: 0;
    height: 34px;
    display: grid;
    grid-template-columns: 16px minmax(0, 1fr) auto;
    align-items: center;
    gap: 7px;
    padding: 0 9px;
    color: var(--modern-text-muted, #919aa6);
    background: var(--modern-surface-raised, rgba(255, 255, 255, 0.045));
    border: 1px solid rgba(255, 255, 255, 0.085);
    border-radius: 9px;
    transition:
      background-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      border-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease);
  }

  .modern-search:focus-within {
    background: rgba(255, 255, 255, 0.065);
    border-color: color-mix(in srgb, var(--accent-color) 44%, rgba(255, 255, 255, 0.12));
  }

  .search-icon {
    width: 15px;
    height: 15px;
    fill: currentColor;
  }

  .search-input {
    min-width: 0;
    width: 100%;
    height: 100%;
    padding: 0;
    color: var(--modern-text-primary, #edf0f4);
    background: transparent;
    border: 0;
    outline: 0;
    font-family: inherit;
    font-size: 11px;
  }

  .search-input::placeholder {
    color: var(--modern-text-muted, #8d96a3);
  }

  .clear-button {
    width: 22px;
    height: 22px;
    display: grid;
    place-items: center;
    padding: 0;
    color: #88919c;
    background: transparent;
    border: 0;
    border-radius: 6px;
    cursor: pointer;
    animation:
      modern-search-control-enter
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1))
      backwards;
    transition:
      color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      background-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .clear-button:hover {
    color: #eef1f5;
    background: rgba(255, 255, 255, 0.07);
  }

  .clear-button:focus-visible {
    outline: 2px solid var(--modern-focus-ring, color-mix(in srgb, var(--accent-color) 78%, white));
    outline-offset: 1px;
  }

  .clear-button svg {
    width: 12px;
    height: 12px;
    fill: currentColor;
  }

  .shortcut-hint {
    padding-right: 2px;
    color: var(--modern-text-muted, #7f8894);
    font-size: 9px;
    white-space: nowrap;
  }

  .results,
  .search-error {
    position: absolute;
    z-index: 1000002;
    top: calc(100% + 8px);
    right: 0;
    left: 0;
    overflow: hidden;
    background: rgba(14, 17, 22, 0.97);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 11px;
    box-shadow: 0 16px 36px rgba(0, 0, 0, 0.34);
    animation:
      results-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
  }

  .results {
    max-height: min(340px, calc(var(--modern-logical-viewport-height, 100vh) - 110px));
    overflow-y: auto;
    scrollbar-width: thin;
    scrollbar-color: rgba(255, 255, 255, 0.14) transparent;
  }

  .result {
    position: relative;
    width: 100%;
    min-height: 46px;
    display: grid;
    grid-template-columns: 7px minmax(0, 1fr) auto;
    align-items: center;
    gap: 9px;
    padding: 7px 10px;
    color: #abb2bc;
    background: transparent;
    border: 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    overflow: hidden;
    cursor: pointer;
    font-family: inherit;
    text-align: left;
    transition:
      color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      background-color var(--modern-motion-duration, 140ms) var(--modern-motion-easing, ease),
      transform var(--modern-motion-fast, 100ms) var(--modern-motion-easing, ease);
    animation:
      modern-search-result-enter
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1))
      backwards;
    animation-delay:
      calc(
        var(--modern-result-enter-index, 0)
        * var(--modern-motion-stagger, 24ms)
      );
  }

  .result::before {
    position: absolute;
    top: 22%;
    bottom: 22%;
    left: 0;
    width: 2px;
    content: "";
    background: color-mix(in srgb, var(--accent-color) 78%, white);
    border-radius: 0 2px 2px 0;
    opacity: 0;
    pointer-events: none;
    transform: scaleY(0.25);
    transform-origin: center;
    transition:
      opacity
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      transform
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1));
  }

  .result:hover:not(:disabled) {
    transform: translateX(2px);
  }

  .result:hover,
  .result.selected {
    color: #eef1f5;
    background: rgba(255, 255, 255, 0.055);
  }

  .result.selected::before {
    opacity: 1;
    transform: scaleY(1);
  }

  .result:disabled {
    cursor: progress;
    opacity: 0.64;
  }

  .result:focus-visible {
    position: relative;
    outline: 2px solid var(--modern-focus-ring, color-mix(in srgb, var(--accent-color) 78%, white));
    outline-offset: -2px;
  }

  .result-state {
    --modern-result-state-scale: 1;

    width: 6px;
    height: 6px;
    background: #555e69;
    border-radius: 50%;
    transition:
      background-color
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1)),
      transform
      var(--modern-motion-duration, 140ms)
      var(--modern-motion-easing, cubic-bezier(0.2, 0.8, 0.2, 1));
  }

  .result.enabled .result-state {
    background: color-mix(in srgb, var(--accent-color) 82%, white);
    animation:
      modern-search-state-confirm
      var(--modern-motion-entrance-duration, 260ms)
      var(--modern-motion-entrance-easing, cubic-bezier(0.16, 1, 0.3, 1));
  }

  .result.selected .result-state {
    --modern-result-state-scale: 1.3;

    transform: scale(var(--modern-result-state-scale));
  }

  .result-copy {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .result-copy strong,
  .result-copy span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .result-copy strong {
    font-size: 11px;
    font-weight: 620;
  }

  .result-copy span {
    color: var(--modern-text-muted, #8d96a3);
    font-size: 9px;
  }

  .result.enabled .result-copy strong {
    color: color-mix(in srgb, var(--accent-color) 76%, white);
  }

  .result-action {
    color: var(--modern-text-muted, #8d96a3);
    font-size: 9px;
    font-weight: 650;
    letter-spacing: 0.05em;
    text-transform: uppercase;
  }

  .empty-result {
    padding: 17px 12px;
    color: #858e9a;
    font-size: 11px;
    text-align: center;
  }

  .result-footer {
    position: sticky;
    bottom: 0;
    display: flex;
    justify-content: center;
    gap: 12px;
    padding: 6px 8px;
    color: var(--modern-text-muted, #828b97);
    background: rgba(11, 14, 18, 0.97);
    border-top: 1px solid rgba(255, 255, 255, 0.055);
    font-size: 8px;
    letter-spacing: 0.025em;
  }

  .search-error {
    padding: 9px 11px;
    color: #e5a5a5;
    font-size: 10px;
  }

  @keyframes results-enter {
    from {
      opacity: 0;
      transform: translateY(-6px);
    }
  }

  @keyframes modern-search-result-enter {
    from {
      opacity: 0;
      transform: translateY(-4px);
    }
  }

  @keyframes modern-search-control-enter {
    from {
      opacity: 0;
      transform: rotate(-45deg);
    }
  }

  @keyframes modern-search-state-confirm {
    0% {
      opacity: 0.45;
      transform: scale(0.55);
    }

    58% {
      transform: scale(1.35);
    }

    100% {
      opacity: 1;
      transform: scale(var(--modern-result-state-scale));
    }
  }

  @media (max-width: 760px) {
    .shortcut-hint {
      display: none;
    }

    .result-footer {
      gap: 7px;
    }
  }

  @media (prefers-reduced-motion: reduce) {
    .modern-search,
    .result,
    .result::before,
    .result-state,
    .clear-button {
      transition-duration: 0.01ms;
    }

    .results,
    .search-error,
    .result,
    .result.enabled .result-state,
    .clear-button {
      animation: none;
    }

    .result:hover:not(:disabled) {
      transform: none;
    }
  }
</style>
