import type {Chart as ChartJS, Point, ScatterDataPoint} from "chart.js";
import type {Range} from "../../../integration/types";

export type CurveChart = ChartJS<"line", ScatterDataPoint[], unknown>;

export const CURVE_EPSILON = 1e-9;
export const CURVE_EDGE_MARGIN = 1e-6;

export function clampCurveValue(value: number, minimum: number, maximum: number): number {
    return Math.min(Math.max(value, minimum), maximum);
}

export function sortCurvePoints(points: ScatterDataPoint[]): ScatterDataPoint[] {
    return points.sort((left, right) => left.x - right.x);
}

export function ensureCurveEndpoints(
    points: ScatterDataPoint[],
    xRange: Range,
    yRange: Range,
): void {
    const findAtX = (x: number) => points.find(point => Math.abs(point.x - x) <= CURVE_EPSILON);
    if (findAtX(xRange.from) === undefined) {
        points.push({x: xRange.from, y: yRange.from / 2});
    }
    if (findAtX(xRange.to) === undefined) {
        points.push({x: xRange.to, y: yRange.from / 2});
    }
    for (const point of points) {
        if (Math.abs(point.x - xRange.from) <= CURVE_EPSILON) point.x = xRange.from;
        if (Math.abs(point.x - xRange.to) <= CURVE_EPSILON) point.x = xRange.to;
    }
    sortCurvePoints(points);
}

export function curvePosition(event: MouseEvent, chart: CurveChart) {
    const rect = (chart.canvas as HTMLCanvasElement).getBoundingClientRect();
    const xPixel = event.clientX - rect.left;
    const yPixel = event.clientY - rect.top;
    return {
        xPixel,
        yPixel,
        x: chart.scales.x.getValueForPixel(xPixel),
        y: chart.scales.y.getValueForPixel(yPixel),
    };
}

export function lockCurveEdgePoint(
    previousPoint: Point,
    currentPoint: Point,
    xRange: Range,
    yRange: Range,
): void {
    const minimumOpen = xRange.from + CURVE_EDGE_MARGIN;
    const maximumOpen = xRange.to - CURVE_EDGE_MARGIN;
    if (Math.abs(previousPoint.x - xRange.from) <= CURVE_EPSILON) {
        currentPoint.x = xRange.from;
    } else if (Math.abs(previousPoint.x - xRange.to) <= CURVE_EPSILON) {
        currentPoint.x = xRange.to;
    } else {
        currentPoint.x = clampCurveValue(currentPoint.x, minimumOpen, maximumOpen);
    }
    currentPoint.y = clampCurveValue(currentPoint.y, yRange.from, yRange.to);
}
