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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.shouldRewriteInteractableAmbientMovement
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InteractableRuntimePolicyTest {

    @Test
    fun `ambient movement stays at the confirmed endpoint except during vanilla correction acknowledgement`() {
        assertFalse(shouldRewriteInteractableAmbientMovement(
            movementLeaseRequired = false,
            correctionInProgress = false,
        ))
        assertTrue(shouldRewriteInteractableAmbientMovement(
            movementLeaseRequired = true,
            correctionInProgress = false,
        ))
        assertFalse(shouldRewriteInteractableAmbientMovement(
            movementLeaseRequired = true,
            correctionInProgress = true,
        ))
    }
}
