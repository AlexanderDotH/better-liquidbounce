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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.contract

import net.minecraft.world.phys.Vec3

/** Packet shape captured by a session route before live yaw and collision flags are available. */
internal sealed interface InteractablePacketInstruction {
    val onGround: Boolean
    val transportBurstId: Int?
        get() = null

    data class Status(
        override val onGround: Boolean,
        override val transportBurstId: Int? = null,
    ) : InteractablePacketInstruction

    data class Position(
        val position: Vec3,
        val fullPacket: Boolean,
        override val onGround: Boolean,
        val collisionChecked: Boolean = true,
        val requiresStandableEndpoint: Boolean = false,
        override val transportBurstId: Int? = null,
    ) : InteractablePacketInstruction
}
