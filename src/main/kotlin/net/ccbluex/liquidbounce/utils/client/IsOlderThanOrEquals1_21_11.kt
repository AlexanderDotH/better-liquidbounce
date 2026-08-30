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
@file:JvmName("ProtocolUtilKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.client

import com.viaversion.viafabricplus.ViaFabricPlus
import net.ccbluex.liquidbounce.utils.client.vfp.VfpCompatibility
import net.ccbluex.liquidbounce.utils.client.vfp.VfpCompatibility1_8
import net.minecraft.core.BlockPos

/**
 * Since 26.1 [net.minecraft.network.protocol.game.ServerboundInteractPacket] has only one mode
 * with entity and relative position (previous `INTERACT_AT`).
 */
val isOlderThanOrEquals1_21_11: Boolean
    get() = runCatching {
        // Check if the ViaFabricPlus mod is loaded - prevents from causing too many exceptions
        usesViaFabricPlus && VfpCompatibility.INSTANCE.isOlderThanOrEqual1_21_11
    }.onFailure {
        logger.error("Failed to check if the server is using 1.21.11", it)
    }.getOrDefault(false)

val isOlderThanOrEqual1_11_1: Boolean
    get() = runCatching {
        // Check if the ViaFabricPlus mod is loaded - prevents from causing too many exceptions
        usesViaFabricPlus && VfpCompatibility.INSTANCE.isOlderThanOrEqual1_11_1
    }.onFailure {
        logger.error("Failed to check if the server is using 1.11.1", it)
    }.getOrDefault(false)

fun selectProtocolVersion(protocolId: Int) {
    // Check if the ViaFabricPlus mod is loaded - prevents from causing too many exceptions
    if (usesViaFabricPlus) {
        VfpCompatibility.INSTANCE.unsafeSelectProtocolVersion(protocolId)
    } else {
        error("ViaFabricPlus is not loaded")
    }
}

fun openVfpProtocolSelection() {
    // Check if the ViaFabricPlus mod is loaded
    if (!usesViaFabricPlus) {
        logger.error("ViaFabricPlus is not loaded")
        return
    }

    VfpCompatibility.INSTANCE.unsafeOpenVfpProtocolSelection()
}

@Suppress("FunctionName")
fun send1_8SignUpdate(blockPos: BlockPos, lines: Array<String>) {
    require(usesViaFabricPlus) { "ViaFabricPlus is missing" }
    require(isEqual1_8) { "Not 1.8 protocol" }

    VfpCompatibility1_8.INSTANCE.sendSignUpdate(blockPos, lines)
}

@Suppress("FunctionName")
fun send1_8PlayerInput(sideways: Float, forward: Float, jumping: Boolean, sneaking: Boolean) {
    require(usesViaFabricPlus) { "ViaFabricPlus is missing" }
    require(isEqual1_8) { "Not 1.8 protocol" }

    VfpCompatibility1_8.INSTANCE.sendPlayerInput(sideways, forward, jumping, sneaking)
}
