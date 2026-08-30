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

package net.ccbluex.liquidbounce.utils.aiming

import net.ccbluex.liquidbounce.common.runtime.RunningOwner
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.client.RequestHandler
import net.ccbluex.liquidbounce.utils.kotlin.Priority

internal class RotationRequestCoordinator {
    private val requests = RequestHandler<RotationTarget>()

    val activeTarget: RotationTarget?
        get() = requests.getActiveRequestValue()

    fun request(target: RotationTarget, priority: Priority, provider: RunningOwner) {
        requests.request(RequestHandler.Request(
            expiresIn = requestLifetime(target),
            priority = priority.priority,
            provider = provider,
            value = target,
        ))
    }

    fun tick() = requests.tick()

    fun clear() = requests.clear()

    private fun requestLifetime(target: RotationTarget): Int =
        if (target.movementCorrection == MovementCorrection.CHANGE_LOOK) 1 else target.ticksUntilReset
}
