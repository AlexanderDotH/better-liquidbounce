import type {Point} from "chart.js";
import type {CurveSetting} from "../../../integration/types";
import {sortCurvePoints} from "./curveModel.ts";

export interface CurveDragCallbacks {
    onDragStart: () => void;
    onDrag: (event: unknown, datasetIndex: number, index: number, value: Point | number | null) => void;
    onDragEnd: (event: unknown, datasetIndex: number, index: number, value: Point | number | null) => void;
}

export function createCurveChartConfiguration(
    setting: CurveSetting,
    themeColor: (name: string) => string,
    callbacks: CurveDragCallbacks,
) {
    const accentColor = themeColor("--clickgui-curve-accent-color");
    const gridColor = themeColor("--clickgui-curve-grid-color");
    const axisColor = themeColor("--clickgui-curve-axis-color");
    return {
        type: "line" as const,
        data: curveData(setting, accentColor),
        options: curveOptions(setting, gridColor, axisColor, callbacks),
    };
}

function curveData(setting: CurveSetting, accentColor: string) {
    return {
        datasets: [{
            type: "line" as const,
            data: sortCurvePoints(setting.value.map(point => ({x: point.x, y: point.y}))),
            showLine: true,
            parsing: false as const,
            borderWidth: 2,
            borderColor: accentColor,
            pointRadius: 5,
            pointBackgroundColor: accentColor,
            pointBorderWidth: 0,
            pointHoverRadius: 6,
            pointHoverBackgroundColor: accentColor,
            tension: setting.tension,
        }],
    };
}

function curveOptions(
    setting: CurveSetting,
    gridColor: string,
    axisColor: string,
    callbacks: CurveDragCallbacks,
) {
    return {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
            x: axisOptions(setting.xAxis.label, setting.xAxis.range.from, setting.xAxis.range.to, gridColor, axisColor),
            y: axisOptions(setting.yAxis.label, setting.yAxis.range.from, setting.yAxis.range.to, gridColor, axisColor),
        },
        plugins: {
            legend: {display: false},
            tooltip: {enabled: false},
            dragData: {dragX: true, ...callbacks},
        },
    };
}

function axisOptions(label: string, minimum: number, maximum: number, gridColor: string, axisColor: string) {
    return {
        type: "linear" as const,
        min: minimum,
        max: maximum,
        grid: {color: gridColor},
        ticks: {color: axisColor},
        title: {
            display: true,
            text: label,
            color: axisColor,
        },
    };
}
