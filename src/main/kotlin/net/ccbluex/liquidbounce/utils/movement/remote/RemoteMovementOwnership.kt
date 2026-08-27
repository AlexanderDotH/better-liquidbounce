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

import net.ccbluex.liquidbounce.features.module.modules.combat.RemoteKillMovementOwnership

/**
 * Feature-neutral access to the one server-visible movement stream.
 *
 * Existing combat callers keep using [RemoteKillMovementOwnership], while Reach and future
 * non-combat routes use this facade. Both APIs acquire the same underlying exclusive lease.
 */
internal object RemoteMovementOwnership {

    val active: Boolean
        get() = RemoteKillMovementOwnership.active

    val currentOwner: String?
        get() = RemoteKillMovementOwnership.currentOwner

    val leaseCount: Int
        get() = RemoteKillMovementOwnership.leaseCount

    fun acquire(owner: String): Lease = checkNotNull(tryAcquire(owner)) {
        "Remote movement is already owned by ${currentOwner ?: "another route"}"
    }

    fun tryAcquire(owner: String): Lease? =
        RemoteKillMovementOwnership.tryAcquire(owner)?.let(::Lease)

    internal class Lease internal constructor(
        private val delegate: RemoteKillMovementOwnership.Lease,
    ) : AutoCloseable {

        val active: Boolean
            get() = delegate.active

        override fun close() = delegate.close()
    }
}
