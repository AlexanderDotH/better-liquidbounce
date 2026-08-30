<script lang="ts">
    import {onMount} from "svelte";
    import type {Module} from "../../../../integration/types";
    import type {ClickGuiValueChangeEvent, KeyboardKeyEvent, ModuleToggleEvent} from "../../../../integration/events";
    import {listen} from "../../../../integration/ws";
    import {isClickGuiScreen} from "../../../../util/utils";
    import type {ClickGuiDataSource} from "./model/clickGuiDataSource";
    import {readSearchBarAutoFocus} from "./model/modernInteractionState";
    import {
        loadModernSearch,
        minecraftSearchCommand,
        type ModernSearchCommand,
    } from "./model/modernSearchRuntime.ts";

    let {dataSource, autoFocus, container, input, onLoaded, onModuleToggle, onAutoFocus, onCommand, onClose} = $props<{
        dataSource: ClickGuiDataSource;
        autoFocus: boolean;
        container?: HTMLElement;
        input?: HTMLInputElement;
        onLoaded: (modules: Module[], nextAutoFocus: boolean, error: string | null) => void;
        onModuleToggle: (name: string, enabled: boolean) => void;
        onAutoFocus: (enabled: boolean) => void;
        onCommand: (command: ModernSearchCommand) => Promise<void>;
        onClose: () => void;
    }>();

    listen("moduleToggle", (event: ModuleToggleEvent) => onModuleToggle(event.moduleName, event.enabled));
    listen("keyboardKey", (event: KeyboardKeyEvent) => {
        if (!isClickGuiScreen(event.screen) || event.action === 0) return;
        if (autoFocus && document.activeElement === document.body) input?.focus();
        if (document.activeElement !== input) return;
        const command = minecraftSearchCommand(event.key);
        if (command) void onCommand(command);
    });
    listen("clickGuiValueChange", (event: ClickGuiValueChangeEvent) => {
        if (event.configurable.name === "ClickGUI") {
            onAutoFocus(readSearchBarAutoFocus(event.configurable, autoFocus));
        }
    });

    onMount(async () => {
        const result = await loadModernSearch(dataSource, autoFocus);
        onLoaded(result.modules, result.autoFocus, result.error);
        if (result.autoFocus && result.modulesLoaded) requestAnimationFrame(() => input?.focus());
    });

    function handlePointerDown(event: PointerEvent): void {
        if (!container?.contains(event.target as Node)) onClose();
    }

    function handleKeydown(event: KeyboardEvent): void {
        if (!autoFocus || document.activeElement !== document.body) return;
        if (event.ctrlKey || event.metaKey || event.altKey) return;
        input?.focus();
    }
</script>

<svelte:window onpointerdown={handlePointerDown} onkeydown={handleKeydown}/>
