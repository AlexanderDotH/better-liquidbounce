<script lang="ts">
    import type {Module} from "../../../../integration/types";
    import {spaceSeperatedNames} from "../../../../theme/theme_config";
    import {highlightModuleName} from "../../clickgui_store";
    import ModernSearchInput from "./ModernSearchInput.svelte";
    import ModernSearchInterop from "./ModernSearchInterop.svelte";
    import ModernSearchResults from "./ModernSearchResults.svelte";
    import type {ClickGuiDataSource} from "./model/clickGuiDataSource";
    import {productionClickGuiDataSource} from "./model/clickGuiDataSource";
    import {
        clampSearchSelection,
        moveSearchSelection,
    } from "./model/modernInteractionState";
    import {filterModulesBySearch} from "./model/moduleSearch";
    import {motionAwareScrollBehavior} from "./modernShellState";
    import {
        browserSearchCommand,
        prefersReducedMotion,
        toggleModernSearchModule,
        type ModernSearchCommand,
    } from "./model/modernSearchRuntime.ts";
    import "./ModernSearch.styles.scss";

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

    function updateQuery(value: string): void {
        query = value;
        selectedIndex = 0;
        popoverOpen = value.trim().length > 0;

        if (!value.trim()) {
            $highlightModuleName = null;
        }
    }

    function handleBrowserKeydown(event: KeyboardEvent): void {
        const command = browserSearchCommand(event.key);
        if (!allowNativeInput) {
            if (command) event.preventDefault();
            return;
        }
        if (!command || command === "locate" && !selectedModule) return;
        event.preventDefault();
        void executeSearchCommand(command);
    }

    async function executeSearchCommand(command: ModernSearchCommand): Promise<void> {
        switch (command) {
            case "next":
                moveSelection(1);
                break;
            case "previous":
                moveSelection(-1);
                break;
            case "toggle":
                await toggleSelectedModule();
                break;
            case "locate":
                if (selectedModule) locateModule(selectedModule.name);
                break;
            case "clear":
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
        if (pendingModuleNames.includes(module.name)) return;

        pendingModuleNames = [...pendingModuleNames, module.name];
        searchError = null;
        try {
            searchError = await toggleModernSearchModule(dataSource, module, enabled => {
                modules = modules.map(candidate => candidate.name === module.name
                    ? {...candidate, enabled}
                    : candidate
                );
            });
        } finally {
            pendingModuleNames = pendingModuleNames.filter(name => name !== module.name);
        }
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

</script>

<div class="modern-search" bind:this={container}>
    <ModernSearchInterop
            {dataSource}
            {autoFocus}
            {container}
            input={searchInput}
            onLoaded={(nextModules, nextAutoFocus, nextError) => {
                modules = nextModules;
                autoFocus = nextAutoFocus;
                searchError = nextError;
                loading = false;
            }}
            onModuleToggle={(name, enabled) => modules = modules.map(module => module.name === name ? {...module, enabled} : module)}
            onAutoFocus={enabled => autoFocus = enabled}
            onCommand={executeSearchCommand}
            onClose={() => popoverOpen = false}
    />
    <ModernSearchInput
            bind:input={searchInput}
            {query}
            {loading}
            error={searchError}
            resultsOpen={showResults}
            selectedResultId={showResults && selectedModule ? `modern-search-result-${selectedIndex}` : undefined}
            {allowNativeInput}
            onQuery={updateQuery}
            onFocus={() => {
                if (query.trim()) popoverOpen = true;
            }}
            onKeydown={handleBrowserKeydown}
            onClear={clearSearch}
    />
    {#if showResults}
        <ModernSearchResults
                {query}
                modules={filteredModules}
                {selectedIndex}
                pendingNames={pendingModuleNames}
                spacedNames={$spaceSeperatedNames}
                onSelect={selectResult}
                onHover={index => selectedIndex = index}
                onLocate={locateModule}
        />
    {/if}
    {#if searchError}
        <div id="modern-search-error" class="search-error" role="alert">{searchError}</div>
    {/if}
</div>
