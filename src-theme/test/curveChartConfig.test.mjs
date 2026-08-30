import assert from "node:assert/strict";
import test from "node:test";

import {createCurveChartConfiguration} from "../src/routes/clickgui/setting/curveChartConfig.ts";

test("curve chart configuration preserves axes, tension, colors, and drag callbacks", () => {
    const setting = {
        value: [{x: 1, y: 2}],
        tension: 0.35,
        xAxis: {label: "Input", range: {from: 0, to: 10}},
        yAxis: {label: "Output", range: {from: -2, to: 2}},
    };
    const callbacks = {
        onDragStart() {},
        onDrag() {},
        onDragEnd() {},
    };
    const color = name => `color:${name}`;
    const config = createCurveChartConfiguration(setting, color, callbacks);

    assert.equal(config.type, "line");
    assert.deepEqual(config.data.datasets[0].data, [{x: 1, y: 2}]);
    assert.notEqual(config.data.datasets[0].data, setting.value);
    assert.equal(config.data.datasets[0].tension, 0.35);
    assert.equal(config.options.scales.x.min, 0);
    assert.equal(config.options.scales.x.max, 10);
    assert.equal(config.options.scales.x.title.text, "Input");
    assert.equal(config.options.scales.y.title.text, "Output");
    assert.equal(config.options.plugins.dragData.onDragStart, callbacks.onDragStart);
    assert.equal(config.options.plugins.dragData.onDrag, callbacks.onDrag);
    assert.equal(config.options.plugins.dragData.onDragEnd, callbacks.onDragEnd);
});
