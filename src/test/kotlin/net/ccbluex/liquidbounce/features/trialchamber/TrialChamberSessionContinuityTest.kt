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
package net.ccbluex.liquidbounce.features.trialchamber

import kotlin.test.Test
import kotlin.test.assertEquals

class TrialChamberSessionContinuityTest {

    @Test
    fun `partial anchor unloads and reloads keep the current chamber session`() {
        val continuity = TrialChamberSessionContinuity()
        val first = position(0, 20, 0)
        val second = position(40, 20, 0)

        assertEquals(TrialChamberContinuity.STARTED, continuity.observe(cluster(first, second)))
        assertEquals(TrialChamberContinuity.CONTINUED, continuity.observe(cluster(first)))
        assertEquals(TrialChamberContinuity.CONTINUED, continuity.observe(cluster(first, second)))
    }

    @Test
    fun `newly loaded connected anchors continue the same chamber without direct overlap`() {
        val continuity = TrialChamberSessionContinuity()

        assertEquals(
            TrialChamberContinuity.STARTED,
            continuity.observe(cluster(position(0, 20, 0))),
        )
        assertEquals(
            TrialChamberContinuity.CONTINUED,
            continuity.observe(cluster(position(96, 84, 0))),
        )
    }

    @Test
    fun `distant anchor cluster starts a different chamber session`() {
        val continuity = TrialChamberSessionContinuity()

        continuity.observe(cluster(position(0, 20, 0)))

        assertEquals(
            TrialChamberContinuity.CHANGED,
            continuity.observe(cluster(position(193, 20, 0))),
        )
    }

    @Test
    fun `temporary absence does not erase continuity but world reset does`() {
        val continuity = TrialChamberSessionContinuity()
        val chamber = cluster(position(0, 20, 0))

        assertEquals(TrialChamberContinuity.STARTED, continuity.observe(chamber))
        assertEquals(TrialChamberContinuity.CONTINUED, continuity.observe(chamber))

        continuity.clear()

        assertEquals(TrialChamberContinuity.STARTED, continuity.observe(chamber))
    }

    private fun cluster(vararg positions: TrialBlockPosition): TrialChamberCluster = TrialChamberCluster.from(
        positions.map { TrialChamberAnchor(it, TrialChamberAnchorKind.VAULT) },
    )

    private fun position(x: Int, y: Int, z: Int) = TrialBlockPosition(x, y, z)
}
