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

package net.ccbluex.liquidbounce.features.litematica.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LitematicaPlacementSelectionTest {

    private val planner = LitematicaPlanner()

    @Test
    fun `inclusive bounds contain their edge blocks and measure distance to block volume`() {
        val bounds = LitematicaBounds(
            min = LitematicaPosition(-2, 60, 4),
            max = LitematicaPosition(2, 64, 8),
        )

        assertTrue(bounds.contains(LitematicaPosition(-2, 60, 4)))
        assertTrue(bounds.contains(LitematicaPosition(2, 64, 8)))
        assertFalse(bounds.contains(LitematicaPosition(3, 64, 8)))
        assertEquals(0.0, bounds.distanceSquaredTo(LitematicaPoint(2.5, 64.5, 8.5)))
        assertEquals(4.0, bounds.distanceSquaredTo(LitematicaPoint(5.0, 64.5, 8.5)))
    }

    @Test
    fun `bounds reject inverted axes`() {
        assertFailsWith<IllegalArgumentException> {
            LitematicaBounds(
                min = LitematicaPosition(1, 64, 0),
                max = LitematicaPosition(0, 64, 0),
            )
        }
    }

    @Test
    fun `nearest active rendered placement with local incomplete work is selected`() {
        val result = plan(
            placement("disabled", cell(1), enabled = false),
            placement("hidden", cell(2), rendered = false),
            placement("complete", cell(0, actual = STONE)),
            placement("far", cell(4)),
            placement("near", cell(-1)),
        )

        assertEquals(placementId("near"), result.selectedPlacement?.id)
    }

    @Test
    fun `sticky placement wins over a newly nearer placement while local work remains`() {
        val result = plan(
            placement("near", cell(1)),
            placement("sticky", cell(4)),
            stickyPlacementId = placementId("sticky"),
        )

        assertEquals(placementId("sticky"), result.selectedPlacement?.id)
    }

    @Test
    fun `completed or unreachable sticky placement releases selection to the nearest candidate`() {
        val completed = plan(
            placement("near", cell(1)),
            placement("sticky", cell(2, actual = STONE)),
            stickyPlacementId = placementId("sticky"),
        )
        val unreachable = plan(
            placement("near", cell(1)),
            placement("sticky", cell(6)),
            stickyPlacementId = placementId("sticky"),
        )

        assertEquals(placementId("near"), completed.selectedPlacement?.id)
        assertEquals(placementId("near"), unreachable.selectedPlacement?.id)
    }

    @Test
    fun `render layer excludes hidden cells from selection counts and actions`() {
        val placement = placement(
            id = "layered",
            cells = arrayOf(cell(1, y = 64), cell(1, y = 65)),
            renderLayer = LitematicaRenderLayer(minY = 64, maxY = 64),
        )

        val result = plan(placement)

        assertEquals(listOf(LitematicaPosition(1, 64, 0)), result.cells.map { it.position })
        assertEquals(1, result.statusCounts.getValue(LitematicaCellStatus.MISSING))
        assertEquals(1, result.actions.size)
    }

    @Test
    fun `render layer honors Litematica x axis slicing`() {
        val placement = placement(
            id = "x-layered",
            cells = arrayOf(cell(1), cell(2)),
            renderLayer = LitematicaRenderLayer(LitematicaAxis.X, minimum = 2, maximum = 2),
        )

        val result = plan(placement)

        assertEquals(listOf(LitematicaPosition(2, 64, 0)), result.cells.map { it.position })
    }

    @Test
    fun `placement with all incomplete work outside render layer is not selected`() {
        val hiddenWork = placement(
            id = "hidden-work",
            cells = arrayOf(cell(1, y = 65)),
            renderLayer = LitematicaRenderLayer(minY = 64, maxY = 64),
        )

        val result = plan(hiddenWork)

        assertNull(result.selectedPlacement)
        assertTrue(result.cells.isEmpty())
    }

    @Test
    fun `conflicting visible overlap is ambiguous and never actionable`() {
        val first = placement("first", cell(1, desired = STONE))
        val second = placement("second", cell(1, desired = DIRT))

        val result = plan(first, second, stickyPlacementId = placementId("first"))

        val cell = result.cells.single()
        assertEquals(LitematicaCellStatus.AMBIGUOUS, cell.status)
        assertEquals(LitematicaBlockReason.AMBIGUOUS_OVERLAP, cell.blockReason)
        assertNull(cell.action)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun `identical visible overlap remains actionable`() {
        val first = placement("first", cell(1, desired = STONE))
        val second = placement("second", cell(1, desired = STONE))

        val result = plan(first, second, stickyPlacementId = placementId("first"))

        assertEquals(LitematicaCellStatus.MISSING, result.cells.single().status)
        assertEquals(LitematicaActionKind.PLACE, result.actions.single().kind)
    }

    @Test
    fun `range includes the exact boundary and excludes anything farther`() {
        val origin = LitematicaPoint(0.5, 64.5, 0.5)
        val result = planner.plan(
            LitematicaPlanRequest(
                origin = origin,
                placements = listOf(
                    placement("boundary", cell(4)),
                    placement("outside", cell(5)),
                ),
                settings = LitematicaPlannerSettings(range = 4.0),
            ),
        )

        assertEquals(placementId("boundary"), result.selectedPlacement?.id)
        assertEquals(listOf(LitematicaPosition(4, 64, 0)), result.cells.map { it.position })
    }

    @Test
    fun `equal distance placements use id as deterministic tie break`() {
        val result = plan(
            placement("zeta", cell(-1)),
            placement("alpha", cell(1)),
        )

        assertEquals(placementId("alpha"), result.selectedPlacement?.id)
    }

    @Test
    fun `placement snapshot copies the adapter cell collection`() {
        val source = mutableListOf(cell(1))
        val placement = placement("snapshot", *source.toTypedArray())
        source.clear()

        val result = plan(placement)

        assertEquals(1, result.cells.size)
    }

    private fun plan(
        vararg placements: LitematicaPlacementSnapshot,
        stickyPlacementId: LitematicaPlacementId? = null,
    ) = planner.plan(
        LitematicaPlanRequest(
            origin = LitematicaPoint(0.5, 64.5, 0.5),
            placements = placements.asList(),
            stickyPlacementId = stickyPlacementId,
        ),
    )

    private fun placement(
        id: String,
        vararg cells: LitematicaCellSnapshot,
        enabled: Boolean = true,
        rendered: Boolean = true,
        renderLayer: LitematicaRenderLayer = LitematicaRenderLayer.ALL,
    ): LitematicaPlacementSnapshot {
        val positions = cells.map(LitematicaCellSnapshot::position)
        val bounds = if (positions.isEmpty()) {
            LitematicaBounds(LitematicaPosition(0, 64, 0), LitematicaPosition(0, 64, 0))
        } else {
            LitematicaBounds.enclosing(positions)
        }
        return LitematicaPlacementSnapshot(
            id = placementId(id),
            name = id,
            enabled = enabled,
            rendered = rendered,
            bounds = bounds,
            renderLayer = renderLayer,
            cells = cells.asList(),
        )
    }

    private fun cell(
        x: Int,
        y: Int = 64,
        desired: LitematicaBlockSnapshot = STONE,
        actual: LitematicaBlockSnapshot = AIR,
    ) = LitematicaCellSnapshot(
        position = LitematicaPosition(x, y, 0),
        desired = desired,
        actual = actual,
        placementMethod = LitematicaPlacementMethod.NEIGHBOR_FACE,
    )

    private fun placementId(value: String) = LitematicaPlacementId(value)

    private companion object {
        val AIR = LitematicaBlockSnapshot.air()
        val STONE = LitematicaBlockSnapshot.solid("minecraft:stone")
        val DIRT = LitematicaBlockSnapshot.solid("minecraft:dirt")
    }
}
