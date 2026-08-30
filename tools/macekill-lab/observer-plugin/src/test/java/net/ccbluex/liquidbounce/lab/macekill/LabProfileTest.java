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

final class LabProfileTest {
    @Test
    void retainsThePinnedLabProfileContract() {
        assertEquals("paper-26.2-build-112-unvalidated", LabProfile.ID);
        assertEquals(200.0, LabProfile.TARGET_HEALTH);
    }
}
