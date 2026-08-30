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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;

final class LabCellGeometryTest {
    @Test
    void fiveByFiveCellKeepsTwentySevenAirBlocksInsideNinetyEightShellBlocks() {
        long shellBlocks = IntStream.range(0, 125).filter(index -> {
            int x = index / 25 - 2;
            int y = index % 25 / 5 - 1;
            int z = index % 5 - 2;
            return LabCellGeometry.isShell(x, y, z);
        }).count();

        assertEquals(98, shellBlocks);
        assertTrue(LabCellGeometry.isShell(-2, 0, 0));
        assertTrue(LabCellGeometry.isShell(0, -1, 0));
        assertFalse(LabCellGeometry.isShell(0, 0, 0));
    }
}
