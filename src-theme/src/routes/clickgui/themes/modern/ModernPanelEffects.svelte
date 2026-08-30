<script lang="ts">
    import {onDestroy} from "svelte";
    import type {Module as ClickGuiModule} from "../../../../integration/types";
    import {highlightModuleName} from "../../clickgui_store";
    import type {ModernPanelState} from "./model/modernPanelState";

    let {scaleFactor, resetVersion, initialState, modules, onScaleChange, onReset, onHighlight} = $props<{
        scaleFactor: number;
        resetVersion: number;
        initialState: ModernPanelState;
        modules: ClickGuiModule[];
        onScaleChange: () => void;
        onReset: (state: ModernPanelState) => void;
        onHighlight: () => void;
    }>();
    let scaleChangeFrame: number | null = null;
    let observedScaleFactor: number | null = null;
    let observedResetVersion: number | null = null;

    $effect(() => {
        const currentScaleFactor = scaleFactor;
        if (currentScaleFactor === observedScaleFactor) return;
        observedScaleFactor = currentScaleFactor;
        if (scaleChangeFrame !== null) cancelAnimationFrame(scaleChangeFrame);
        scaleChangeFrame = requestAnimationFrame(() => {
            scaleChangeFrame = null;
            onScaleChange();
        });
    });

    $effect(() => {
        const requestedResetVersion = resetVersion;
        const nextInitialState = initialState;
        if (observedResetVersion === null) {
            observedResetVersion = requestedResetVersion;
            return;
        }
        if (requestedResetVersion === observedResetVersion) return;
        observedResetVersion = requestedResetVersion;
        onReset(nextInitialState);
    });

    const unsubscribeHighlight = highlightModuleName.subscribe(name => {
        if (name && modules.some((module: ClickGuiModule) => module.name === name)) onHighlight();
    });

    onDestroy(() => {
        unsubscribeHighlight();
        if (scaleChangeFrame !== null) cancelAnimationFrame(scaleChangeFrame);
    });
</script>
