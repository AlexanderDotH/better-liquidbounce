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
package net.ccbluex.liquidbounce.features.baritone.flight.runtime

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaritoneFlightPathObservationTest {

    @Test
    fun `remaining current path is joined to the planned next segment without duplicate junctions`() {
        val observation = observeBaritoneFlightPath(
            current = RuntimePathSegment(
                positions = listOf(point(0), point(1), point(2)),
                currentIndex = 1,
            ),
            next = RuntimePathSegment(
                positions = listOf(point(2), point(3), point(4)),
                currentIndex = 0,
            ),
            elytraDestination = FlightRuntimePosition(100.0, 80.0, 100.0),
        )

        assertEquals(listOf(point(1), point(2), point(3), point(4)), observation.anchors)
        assertEquals(BaritonePathSource.WALKING_PATH, observation.source)
    }

    @Test
    fun `elytra destination remains observable before its native path exists`() {
        val destination = FlightRuntimePosition(100.0, 80.0, 100.0)

        val observation = observeBaritoneFlightPath(
            current = null,
            next = null,
            elytraDestination = destination,
        )

        assertEquals(listOf(destination), observation.anchors)
        assertEquals(BaritonePathSource.ELYTRA_DESTINATION, observation.source)
    }

    @Test
    fun `empty native state is represented explicitly instead of fabricating an anchor`() {
        val observation = observeBaritoneFlightPath(null, null, null)

        assertTrue(observation.anchors.isEmpty())
        assertEquals(BaritonePathSource.NONE, observation.source)
    }

    private fun point(x: Int) = FlightRuntimePosition(x.toDouble(), 64.0, 0.0)
}
