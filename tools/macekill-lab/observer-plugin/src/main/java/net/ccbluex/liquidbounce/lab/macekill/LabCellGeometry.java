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
package net.ccbluex.liquidbounce.lab.macekill;

final class LabCellGeometry {
    private LabCellGeometry() {
    }

    static boolean isShell(int x, int y, int z) {
        return Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3;
    }
}
