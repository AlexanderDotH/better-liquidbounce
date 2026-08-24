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

package net.ccbluex.liquidbounce.features.baritone.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BaritoneContractsTest {

    @Test
    fun `all public task requests expose their stable task kind`() {
        val position = BaritoneBlockPosition(12, 64, -8)
        val tasks = listOf(
            BaritoneTaskRequest.GoTo(BaritoneGoal.Block(position)),
            BaritoneTaskRequest.GetToBlock(BaritoneNamespacedId("minecraft:crafting_table")),
            BaritoneTaskRequest.Mine(listOf(BaritoneNamespacedId("minecraft:diamond_ore")), 3),
            BaritoneTaskRequest.Follow("Alex", 2.5),
            BaritoneTaskRequest.Farm(position, 32),
            BaritoneTaskRequest.Explore(BaritoneHorizontalPosition(12, -8), 256),
            BaritoneTaskRequest.Build("castle.schematic", position),
            BaritoneTaskRequest.Elytra(position),
        )

        assertEquals(BaritoneTaskKind.entries, tasks.map(BaritoneTaskRequest::kind))
    }

    @Test
    fun `task and snapshot collections are immutable defensive copies`() {
        val blocks = mutableListOf(BaritoneNamespacedId("minecraft:stone"))
        val settings = mutableListOf(sampleSetting())
        val waypoints = mutableListOf(sampleWaypoint())
        val logs = mutableListOf(sampleLog(1))
        val task = BaritoneTaskRequest.Mine(blocks, quantity = 4)
        val snapshot = BaritoneSnapshot(
            revision = BaritoneRevision(1),
            availability = BaritoneCapability.AVAILABLE,
            status = BaritonePhase.PATHING,
            task = task,
            settings = settings,
            waypoints = waypoints,
            logs = logs,
        )

        blocks += BaritoneNamespacedId("minecraft:dirt")
        settings.clear()
        waypoints.clear()
        logs.clear()

        assertEquals(listOf(BaritoneNamespacedId("minecraft:stone")), task.blocks)
        assertEquals(1, snapshot.settings.size)
        assertEquals(1, snapshot.waypoints.size)
        assertEquals(1, snapshot.logs.size)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (snapshot.logs as MutableList<BaritoneLogEntry>).add(sampleLog(2))
        }
    }

    @Test
    fun `setting values are type safe and enum options are copied`() {
        val options = mutableListOf("FAST", "SAFE")
        val setting = BaritoneSetting(
            name = BaritoneSettingName("pathingMode"),
            type = BaritoneSettingType.ENUM,
            value = BaritoneSettingValue.EnumValue("SAFE"),
            defaultValue = BaritoneSettingValue.EnumValue("FAST"),
            description = "Selects the pathing profile.",
            mutable = true,
            options = options,
        )

        options += "EXPERIMENTAL"

        assertEquals(listOf("FAST", "SAFE"), setting.options)
        assertFailsWith<IllegalArgumentException> {
            BaritoneSetting(
                name = BaritoneSettingName("invalid"),
                type = BaritoneSettingType.BOOLEAN,
                value = BaritoneSettingValue.TextValue("true"),
                defaultValue = BaritoneSettingValue.BooleanValue(true),
                description = "Invalid on purpose.",
                mutable = true,
            )
        }
    }

    @Test
    fun `structured failures retain transport-independent category and field`() {
        val failure = BaritoneResult.Failure(
            BaritoneError(
                code = BaritoneErrorCode.INVALID_FIELD,
                message = "quantity must be positive",
                field = "quantity",
            ),
        )

        assertEquals(BaritoneErrorCategory.VALIDATION, failure.error.category)
        assertEquals("quantity", failure.error.field)
        assertIs<BaritoneResult.Failure>(failure)
    }

    @Test
    fun `domain values reject malformed input at their boundary`() {
        assertFailsWith<IllegalArgumentException> { BaritoneRevision(-1) }
        assertFailsWith<IllegalArgumentException> { BaritoneSettingName(" ") }
        assertFailsWith<IllegalArgumentException> { BaritoneNamespacedId("stone") }
        assertFailsWith<IllegalArgumentException> { BaritoneProgress(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { BaritoneProgress(1.01) }
        assertTrue(BaritoneProgress(0.25).fraction in 0.0..1.0)
    }

    private fun sampleSetting() = BaritoneSetting(
        name = BaritoneSettingName("allowBreak"),
        type = BaritoneSettingType.BOOLEAN,
        value = BaritoneSettingValue.BooleanValue(true),
        defaultValue = BaritoneSettingValue.BooleanValue(true),
        description = "Allows Baritone to break blocks.",
        mutable = true,
    )

    private fun sampleWaypoint() = BaritoneWaypoint(
        id = BaritoneWaypointId("home-1"),
        name = "Home",
        tag = BaritoneWaypointTag.HOME,
        position = BaritoneBlockPosition(0, 64, 0),
    )

    private fun sampleLog(revision: Long) = BaritoneLogEntry(
        revision = BaritoneRevision(revision),
        level = BaritoneLogLevel.INFO,
        message = "Path updated",
        timestamp = revision * 1000,
    )
}
