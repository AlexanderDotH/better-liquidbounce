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

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Accepts only strictly newer revisioned values, making stale websocket/adapter updates harmless. */
class MonotonicBaritoneState<T : BaritoneRevisioned>(initial: T) {
    private val value = AtomicReference(initial)

    fun current(): T = value.get()

    fun update(candidate: T): Boolean {
        while (true) {
            val current = value.get()
            if (candidate.revision <= current.revision) {
                return false
            }
            if (value.compareAndSet(current, candidate)) {
                return true
            }
        }
    }
}

/** Generates local revisions and advances its watermark when a newer external revision is observed. */
class BaritoneRevisionClock(initial: BaritoneRevision = BaritoneRevision.ZERO) {
    private val value = AtomicLong(initial.value)

    fun current(): BaritoneRevision = BaritoneRevision(value.get())

    fun observe(candidate: BaritoneRevision): Boolean {
        while (true) {
            val current = value.get()
            if (candidate.value <= current) {
                return false
            }
            if (value.compareAndSet(current, candidate.value)) {
                return true
            }
        }
    }

    fun next(): BaritoneRevision {
        while (true) {
            val current = value.get()
            check(current < Long.MAX_VALUE) { "Baritone revision space is exhausted" }
            val next = current + 1
            if (value.compareAndSet(current, next)) {
                return BaritoneRevision(next)
            }
        }
    }
}
