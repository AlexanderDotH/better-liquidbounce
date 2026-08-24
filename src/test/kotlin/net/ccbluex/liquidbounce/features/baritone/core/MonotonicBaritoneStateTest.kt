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
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MonotonicBaritoneStateTest {

    @Test
    fun `state accepts only strictly newer revisions`() {
        val state = MonotonicBaritoneState(RevisionedText(BaritoneRevision(4), "initial"))

        assertFalse(state.update(RevisionedText(BaritoneRevision(3), "stale")))
        assertFalse(state.update(RevisionedText(BaritoneRevision(4), "same revision")))
        assertTrue(state.update(RevisionedText(BaritoneRevision(5), "new")))

        assertEquals("new", state.current().text)
        assertEquals(BaritoneRevision(5), state.current().revision)
    }

    @Test
    fun `revision clock advances after an observed external revision`() {
        val clock = BaritoneRevisionClock(BaritoneRevision(2))

        assertTrue(clock.observe(BaritoneRevision(8)))
        assertFalse(clock.observe(BaritoneRevision(7)))
        assertEquals(BaritoneRevision(9), clock.next())
        assertEquals(BaritoneRevision(9), clock.current())
    }

    @Test
    fun `revision clock never wraps`() {
        val clock = BaritoneRevisionClock(BaritoneRevision(Long.MAX_VALUE))

        assertFailsWith<IllegalStateException> { clock.next() }
    }

    private data class RevisionedText(
        override val revision: BaritoneRevision,
        val text: String,
    ) : BaritoneRevisioned
}
