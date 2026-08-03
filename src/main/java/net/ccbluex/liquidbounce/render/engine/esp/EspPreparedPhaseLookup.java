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

package net.ccbluex.liquidbounce.render.engine.esp;

import java.util.List;
import java.util.Map;

/**
 * Looks up render work after Minecraft has drained the source phase into a prepared frame.
 */
public final class EspPreparedPhaseLookup {

    private EspPreparedPhaseLookup() {
    }

    public static boolean hasPreparedGroups(Map<?, ?> groupsByPhase, Object phase) {
        return groupsByPhase.get(phase) instanceof List<?> groups && !groups.isEmpty();
    }
}
