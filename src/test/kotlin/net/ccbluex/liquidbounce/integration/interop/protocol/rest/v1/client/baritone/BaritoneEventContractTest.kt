/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client.baritone

import com.google.gson.Gson
import net.ccbluex.liquidbounce.annotations.Tag
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneAvailabilityDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneFlyOwnershipDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneLocomotionDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneLogEntryDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneLogEvent
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneLogLevelDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneNavigationDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneNavigationPhaseDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritonePhaseDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritonePointDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneRouteDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneRouteEvent
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneSnapshotDto
import net.ccbluex.liquidbounce.integration.interop.protocol.event.baritone.BaritoneStateEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BaritoneEventContractTest {

    @Test
    fun `websocket events expose stable names and numeric revisions`() {
        val snapshot = snapshot(12)
        val route = BaritoneRouteDto(13, listOf(BaritonePointDto(1.0, 64.0, 2.0)))
        val entry = BaritoneLogEntryDto(14, BaritoneLogLevelDto.INFO, "Path ready", "12:00:00")
        val events = listOf(
            BaritoneStateEvent(12, snapshot),
            BaritoneRouteEvent(13, route),
            BaritoneLogEvent(14, entry),
        )

        assertEquals(
            listOf("baritoneState", "baritoneRoute", "baritoneLog"),
            events.map { it.javaClass.getAnnotation(Tag::class.java).name },
        )
        events.forEach { event ->
            val json = Gson().toJsonTree(event).asJsonObject
            assertTrue(json.getAsJsonPrimitive("revision").isNumber)
        }

        Gson().toJsonTree(events.first()).asJsonObject
            .getAsJsonObject("snapshot")
            .getAsJsonObject("navigation")
            .apply {
                assertEquals("FLY", get("requested").asString)
                assertEquals("FLY", get("active").asString)
                assertEquals("ARMING", get("phase").asString)
                assertEquals("Vanilla", get("flyMode").asString)
                assertEquals("BARITONE", get("ownership").asString)
                assertEquals("Waiting for Fly", get("detail").asString)
                assertEquals(2, get("restartsRemaining").asInt)
            }
    }

    @Test
    fun `event envelopes reject mismatched or negative revisions`() {
        assertFailsWith<IllegalArgumentException> { BaritoneStateEvent(-1, snapshot(-1)) }
        assertFailsWith<IllegalArgumentException> { BaritoneStateEvent(2, snapshot(1)) }
        assertFailsWith<IllegalArgumentException> {
            BaritoneRouteEvent(2, BaritoneRouteDto(1, emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            BaritoneLogEvent(2, BaritoneLogEntryDto(1, BaritoneLogLevelDto.INFO, "old", "11:59:59"))
        }
    }

    @Test
    fun `navigation DTO keeps Fly defaults for older snapshot producers`() {
        val navigation = BaritoneNavigationDto()

        assertEquals(BaritoneLocomotionDto.FLY, navigation.requested)
        assertEquals(null, navigation.active)
        assertEquals(BaritoneNavigationPhaseDto.IDLE, navigation.phase)
        assertEquals(null, navigation.flyMode)
        assertEquals(null, navigation.ownership)
        assertEquals(null, navigation.detail)
        assertEquals(3, navigation.restartsRemaining)
    }

    private fun snapshot(revision: Long) = BaritoneSnapshotDto(
        revision = revision,
        availability = BaritoneAvailabilityDto.AVAILABLE,
        status = BaritonePhaseDto.IDLE,
        task = null,
        etaSeconds = null,
        progress = null,
        pauseReason = null,
        settings = emptyList(),
        waypoints = emptyList(),
        logs = emptyList(),
        failure = null,
        navigation = BaritoneNavigationDto(
            requested = BaritoneLocomotionDto.FLY,
            active = BaritoneLocomotionDto.FLY,
            phase = BaritoneNavigationPhaseDto.ARMING,
            flyMode = "Vanilla",
            ownership = BaritoneFlyOwnershipDto.BARITONE,
            detail = "Waiting for Fly",
            restartsRemaining = 2,
        ),
    )
}
