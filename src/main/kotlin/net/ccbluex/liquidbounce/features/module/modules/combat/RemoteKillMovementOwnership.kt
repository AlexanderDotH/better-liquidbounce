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

package net.ccbluex.liquidbounce.features.module.modules.combat

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide packet-movement ownership for weapon-neutral remote-kill routes.
 *
 * Consumers such as movement helpers only need [active]. Route engines keep one exclusive lease so
 * handoff/recovery cannot briefly expose the route as unowned, and every terminal path closes its
 * lease idempotently.
 */
internal object RemoteKillMovementOwnership {

    private val nextLeaseId = AtomicLong()
    private val activeLease = AtomicReference<LeaseRecord?>()

    val active: Boolean
        get() = activeLease.get() != null

    val currentOwner: String?
        get() = activeLease.get()?.owner

    val leaseCount: Int
        get() = if (active) 1 else 0

    fun acquire(owner: String): Lease = checkNotNull(tryAcquire(owner)) {
        "Remote-kill movement is already owned by ${activeLease.get()?.owner ?: "another route"}"
    }

    /** Atomically reserves the one remote movement pipeline, or returns null without side effects. */
    fun tryAcquire(owner: String): Lease? {
        require(owner.isNotBlank()) { "Remote-kill movement owner must not be blank" }
        val record = LeaseRecord(nextLeaseId.incrementAndGet(), owner)
        return if (activeLease.compareAndSet(null, record)) Lease(record.id) else null
    }

    internal class Lease internal constructor(
        private val leaseId: Long,
    ) : AutoCloseable {

        private val closed = AtomicBoolean()

        val active: Boolean
            get() = !closed.get()

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                release(leaseId)
            }
        }
    }

    private fun release(leaseId: Long) {
        while (true) {
            val current = activeLease.get()?.takeIf { it.id == leaseId } ?: return
            if (activeLease.compareAndSet(current, null)) return
        }
    }

    private data class LeaseRecord(
        val id: Long,
        val owner: String,
    )
}
