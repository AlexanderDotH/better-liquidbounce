<script lang="ts">
    import type {CurveSetting, ModuleSetting} from "../../../integration/types";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import {createEventDispatcher, onDestroy, onMount} from "svelte";
    import {
        Chart,
        LinearScale,
        LineController,
        LineElement,
        type Point,
        PointElement,
        ScatterController,
        type ScatterDataPoint
    } from "chart.js";
    import dragDataPlugin from "chartjs-plugin-dragdata";
    import ExpandArrow from "./common/ExpandArrow.svelte";
    import {setItem} from "../../../integration/persistent_storage";
    import {
        clampCurveValue,
        CURVE_EDGE_MARGIN,
        CURVE_EPSILON,
        curvePosition,
        ensureCurveEndpoints,
        lockCurveEdgePoint,
        sortCurvePoints,
        type CurveChart,
    } from "./curveModel";
    import {createCurveChartConfiguration} from "./curveChartConfig.ts";

    export let setting: ModuleSetting;
    export let path: string;

    const cSetting = setting as CurveSetting;

    const dispatch = createEventDispatcher();

    const thisPath = `${path}.${cSetting.name}`;
    let expanded = localStorage.getItem(thisPath) === "true";

    $: setItem(thisPath, expanded.toString());

    let canvasElement: HTMLCanvasElement;
    let chart: CurveChart | null = null;

    Chart.register(LinearScale, PointElement, LineElement, LineController, ScatterController, dragDataPlugin);

    let isDragging = false;
    let themeObserver: MutationObserver | null = null;

    function getThemeColor(name: string) {
        return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    }

    function applyThemeColors() {
        if (!chart) {
            return;
        }

        const accentColor = getThemeColor("--clickgui-curve-accent-color");
        const gridColor = getThemeColor("--clickgui-curve-grid-color");
        const axisColor = getThemeColor("--clickgui-curve-axis-color");

        const dataset = chart.data.datasets[0];
        dataset.borderColor = accentColor;
        dataset.pointBackgroundColor = accentColor;
        dataset.pointHoverBackgroundColor = accentColor;

        const xAxis = chart.options.scales?.x as any;
        const yAxis = chart.options.scales?.y as any;

        xAxis.grid.color = gridColor;
        yAxis.grid.color = gridColor;
        xAxis.ticks.color = axisColor;
        yAxis.ticks.color = axisColor;
        xAxis.title.color = axisColor;
        yAxis.title.color = axisColor;

        chart.update();
    }

    function updateValue() {
        if (!chart) return;
        const ds = chart.data.datasets[0] as any;
        cSetting.value = ds.data.map((p: ScatterDataPoint) => ({x: p.x, y: p.y})) as Point[];
        setting = { ...cSetting };
        dispatch("change");
    }

    /**
     * Ensures that there is always one point at the exact edges of the x-axis.
     */
    function ensureEndpoints() {
        if (!chart) return;

        ensureCurveEndpoints(
            chart.data.datasets[0].data,
            cSetting.xAxis.range,
            cSetting.yAxis.range,
        );
    }

    function handleCurveDrag(_event: unknown, datasetIndex: number, index: number, value: Point | number | null): void {
        if (!chart) return;
        const previousPoint = chart.data.datasets[datasetIndex].data[index];
        lockCurveEdgePoint(previousPoint, value as Point, cSetting.xAxis.range, cSetting.yAxis.range);
    }

    function handleCurveDragEnd(_event: unknown, datasetIndex: number, index: number, value: Point | number | null): void {
        if (!chart) return;
        const dataset = chart.data.datasets[datasetIndex];
        lockCurveEdgePoint(dataset.data[index], value as Point, cSetting.xAxis.range, cSetting.yAxis.range);
        sortCurvePoints(dataset.data);
        chart.update();
        isDragging = false;
        ensureEndpoints();
        chart.update();
        updateValue();
    }

    onMount(() => {
        const ctx = canvasElement.getContext("2d")!;
        chart = new Chart(ctx, createCurveChartConfiguration(cSetting, getThemeColor, {
            onDragStart: () => isDragging = true,
            onDrag: handleCurveDrag,
            onDragEnd: handleCurveDragEnd,
        }));

        // Ensure endpoints exist and snap exactly to min/max at startup
        ensureEndpoints();
        applyThemeColors();
        chart.update();

        themeObserver = new MutationObserver(() => {
            applyThemeColors();
        });
        themeObserver.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ["style", "class"]
        });
    });

    // Adds a new point close to the position that was clicked.
    function addPoint(e: MouseEvent) {
        if (!chart || isDragging) return;

        const {x, y} = curvePosition(e, chart);
        const minOpen = cSetting.xAxis.range.from + CURVE_EDGE_MARGIN;
        const maxOpen = cSetting.xAxis.range.to - CURVE_EDGE_MARGIN;

        const nx = clampCurveValue(x as number, minOpen, maxOpen);
        const ny = clampCurveValue(y as number, cSetting.yAxis.range.from, cSetting.yAxis.range.to);

        const dataset = chart.data.datasets[0];
        dataset.data.push({x: nx, y: ny});
        sortCurvePoints(dataset.data);
        ensureEndpoints();
        chart.update();
        updateValue();
    }

    // Removes a point which was right clicked
    function removePoint(e: MouseEvent) {
        e.preventDefault();
        if (!chart) return;

        const hits = chart.getElementsAtEventForMode(e, "nearest", {intersect: true}, true);
        if (!hits.length) return;

        const {datasetIndex, index} = hits[0];
        const dataset = chart.data.datasets[datasetIndex];
        const p = dataset.data[index];

        // Don't remove the required endpoints
        if (Math.abs(p.x - cSetting.xAxis.range.from) <= CURVE_EPSILON) return;
        if (Math.abs(p.x - cSetting.xAxis.range.to) <= CURVE_EPSILON) return;

        dataset.data.splice(index, 1);
        sortCurvePoints(dataset.data);
        ensureEndpoints();
        chart.update();
        updateValue();
    }

    onDestroy(() => {
        themeObserver?.disconnect();
        themeObserver = null;
        chart?.destroy();
        chart = null;
    });
</script>

<div class="setting">
    <!-- svelte-ignore a11y-no-static-element-interactions -->
    <div class="head" class:expanded on:contextmenu|preventDefault={() => expanded = !expanded}>
        <div class="title">{$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}</div>
        <ExpandArrow bind:expanded/>
    </div>

    <div class="canvas-wrapper" class:visible={expanded}>
        <canvas on:click={addPoint} on:contextmenu={removePoint}
                bind:this={canvasElement}></canvas>
    </div>
</div>

<style lang="scss">
  @use "./CurveSetting.styles";
</style>
