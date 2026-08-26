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
 */
package net.ccbluex.liquidbounce.features.module.modules.world.trialchamber

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialChamberRenderPlannerTest {

    @Test
    fun `default distance includes exactly 192 blocks and excludes anything farther`() {
        val plan = plan(
            target("boundary", x = 192.0),
            target("outside", x = 192.001),
        )

        assertEquals(listOf("boundary"), plan.glowBoxes.map { it.targetId })
        assertEquals(192.0, plan.glowBoxes.single().distance)
    }

    @Test
    fun `every target kind has an independent enabled-by-default filter`() {
        val targets = TrialChamberRenderTargetKind.entries.mapIndexed { index, kind ->
            target(kind.name, kind = kind, x = index + 1.0)
        }

        assertEquals(
            TrialChamberRenderTargetKind.entries.toSet(),
            plan(*targets.toTypedArray()).glowBoxes.map { it.kind }.toSet(),
        )

        val disabledFilters = mapOf(
            TrialChamberRenderTargetKind.SPAWNER to { filters: TrialChamberRenderFilters ->
                filters.copy(spawners = false)
            },
            TrialChamberRenderTargetKind.NORMAL_VAULT to { filters: TrialChamberRenderFilters ->
                filters.copy(normalVaults = false)
            },
            TrialChamberRenderTargetKind.OMINOUS_VAULT to { filters: TrialChamberRenderFilters ->
                filters.copy(ominousVaults = false)
            },
            TrialChamberRenderTargetKind.CHEST to { filters: TrialChamberRenderFilters ->
                filters.copy(chests = false)
            },
            TrialChamberRenderTargetKind.BARREL to { filters: TrialChamberRenderFilters ->
                filters.copy(barrels = false)
            },
            TrialChamberRenderTargetKind.POT to { filters: TrialChamberRenderFilters ->
                filters.copy(pots = false)
            },
            TrialChamberRenderTargetKind.DISPENSER to { filters: TrialChamberRenderFilters ->
                filters.copy(dispensers = false)
            },
        )

        for ((disabledKind, disable) in disabledFilters) {
            val settings = TrialChamberRenderSettings(filters = disable(TrialChamberRenderFilters()))
            val visibleKinds = plan(*targets.toTypedArray(), settings = settings).glowBoxes.map { it.kind }.toSet()

            assertEquals(TrialChamberRenderTargetKind.entries.toSet() - disabledKind, visibleKinds)
        }
    }

    @Test
    fun `visited and completed targets are hidden by default and can be revealed independently`() {
        val active = target("active", x = 1.0)
        val visited = target("visited", x = 2.0, visited = true)
        val completed = target("completed", x = 3.0, completed = true)

        assertEquals(listOf("active"), plan(active, visited, completed).targetIds())
        assertEquals(
            listOf("active", "visited"),
            plan(
                active,
                visited,
                completed,
                settings = TrialChamberRenderSettings(showVisited = true),
            ).targetIds(),
        )
        assertEquals(
            listOf("active", "completed"),
            plan(
                active,
                visited,
                completed,
                settings = TrialChamberRenderSettings(showCompleted = true),
            ).targetIds(),
        )
        assertEquals(
            listOf("active", "visited", "completed"),
            plan(
                active,
                visited,
                completed,
                settings = TrialChamberRenderSettings(showVisited = true, showCompleted = true),
            ).targetIds(),
        )
    }

    @Test
    fun `labels are hard capped at 24 after filtering and nearest-first sorting`() {
        val targets = (30 downTo 1).map { distance ->
            target("target-${distance.toString().padStart(2, '0')}", x = distance.toDouble())
        }

        val plan = plan(
            *targets.toTypedArray(),
            settings = TrialChamberRenderSettings(maximumLabels = Int.MAX_VALUE),
        )

        assertEquals(30, plan.glowBoxes.size)
        assertEquals(24, plan.labels.size)
        assertEquals(
            plan.glowBoxes.take(24).map { it.targetId },
            plan.labels.map { it.targetId },
        )
    }

    @Test
    fun `targets are nearest first with target id as deterministic distance tie break`() {
        val plan = plan(
            target("tie-b", x = 10.0),
            target("far", x = 30.0),
            target("tie-a", x = -10.0),
            target("near", x = 2.0),
        )

        assertEquals(listOf("near", "tie-a", "tie-b", "far"), plan.targetIds())
    }

    @Test
    fun `disabling labels keeps the complete glow plan`() {
        val plan = plan(
            target("one", x = 1.0),
            target("two", x = 2.0),
            settings = TrialChamberRenderSettings(showLabels = false),
        )

        assertEquals(listOf("one", "two"), plan.targetIds())
        assertTrue(plan.labels.isEmpty())
    }

    @Test
    fun `disabling glow keeps labels without creating glow geometry`() {
        val plan = plan(
            target("one", x = 1.0),
            target("two", x = 2.0),
            settings = TrialChamberRenderSettings(showGlow = false),
        )

        assertTrue(plan.glowBoxes.isEmpty())
        assertEquals(listOf("one", "two"), plan.labels.map { it.targetId })
    }

    @Test
    fun `request snapshots its source collection and preserves world geometry`() {
        val source = mutableListOf(
            target(
                id = "box",
                x = 10.0,
                worldBox = AABB(9.0, 63.0, -2.0, 11.0, 66.0, 2.0),
            ),
        )
        val request = TrialChamberRenderRequest(
            cameraPosition = Vec3(4.0, 60.0, -5.0),
            targets = source,
        )
        source.clear()

        val plan = TrialChamberRenderPlanner.plan(request)

        assertEquals(AABB(9.0, 63.0, -2.0, 11.0, 66.0, 2.0), plan.glowBoxes.single().worldBox)
        assertEquals(Vec3(10.0, 66.25, 0.0), plan.labels.single().position)
    }

    private fun plan(
        vararg targets: TrialChamberRenderTarget,
        settings: TrialChamberRenderSettings = TrialChamberRenderSettings(),
    ): TrialChamberRenderPlan = TrialChamberRenderPlanner.plan(
        TrialChamberRenderRequest(
            cameraPosition = Vec3.ZERO,
            targets = targets.asList(),
            settings = settings,
        ),
    )

    private fun target(
        id: String,
        kind: TrialChamberRenderTargetKind = TrialChamberRenderTargetKind.SPAWNER,
        x: Double,
        visited: Boolean = false,
        completed: Boolean = false,
        worldBox: AABB = AABB(x - 0.5, -0.5, -0.5, x + 0.5, 0.5, 0.5),
    ) = TrialChamberRenderTarget(
        id = id,
        kind = kind,
        position = Vec3(x, 0.0, 0.0),
        worldBox = worldBox,
        label = id,
        color = Color4b(0x55, 0xAA, 0xFF),
        visited = visited,
        completed = completed,
    )

    private fun TrialChamberRenderPlan.targetIds(): List<String> = glowBoxes.map { it.targetId }
}
