/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.common;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-source colors requested for LiquidBounce's independent ESP mask phases.
 */
public record EspMaskRequest(Map<EspMaskLayer, Integer> colors) {

    public static final EspMaskRequest NONE = new EspMaskRequest(Map.of());

    public EspMaskRequest {
        colors = Map.copyOf(colors);
    }

    public boolean isEmpty() {
        return colors.isEmpty();
    }

    public int color(EspMaskLayer layer) {
        return colors.getOrDefault(layer, 0);
    }

    public EspMaskRequest with(EspMaskLayer layer, int color) {
        if (color == 0 || colors.containsKey(layer)) {
            return this;
        }

        var updated = new EnumMap<EspMaskLayer, Integer>(EspMaskLayer.class);
        updated.putAll(colors);
        updated.put(layer, opaque(color));
        return new EspMaskRequest(updated);
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }
}
