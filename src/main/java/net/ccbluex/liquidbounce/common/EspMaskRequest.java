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

/**
 * Colors requested for LiquidBounce's two independent ESP mask phases.
 * A zero color means that the geometry must not be submitted to that phase.
 */
public record EspMaskRequest(int glowColor, int outlineColor) {

    public static final EspMaskRequest NONE = new EspMaskRequest(0, 0);

    public boolean isEmpty() {
        return glowColor == 0 && outlineColor == 0;
    }

    public EspMaskRequest withGlow(int color) {
        if (color == 0 || glowColor != 0) {
            return this;
        }

        return new EspMaskRequest(opaque(color), outlineColor);
    }

    public EspMaskRequest withOutline(int color) {
        if (color == 0 || outlineColor != 0) {
            return this;
        }

        return new EspMaskRequest(glowColor, opaque(color));
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }
}
