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
package net.ccbluex.liquidbounce.utils.movement.remote

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Feature-neutral access to the one server-visible movement stream.
 *
 * Every feature, including combat routes, acquires the same process-wide exclusive lease through
 * this neutral owner. Feature-specific compatibility facades must delegate here, never the reverse.
 */
internal object RemoteMovementOwnership {

    private val nextLeaseId = AtomicLong()
    private val activeLease = AtomicReference<LeaseRecord?>()

    val active: Boolean
        get() = activeLease.get() != null

    val currentOwner: String?
        get() = activeLease.get()?.owner

    val leaseCount: Int
        get() = if (active) 1 else 0

    fun acquire(owner: String): Lease = checkNotNull(tryAcquire(owner)) {
        "Remote movement is already owned by ${currentOwner ?: "another route"}"
    }

    /** Atomically reserves the one server-visible movement stream without side effects on failure. */
    fun tryAcquire(owner: String): Lease? {
        require(owner.isNotBlank()) { "Remote movement owner must not be blank" }
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

    private data class LeaseRecord(val id: Long, val owner: String)
}
