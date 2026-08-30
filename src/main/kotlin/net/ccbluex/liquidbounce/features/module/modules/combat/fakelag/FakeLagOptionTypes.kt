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
package net.ccbluex.liquidbounce.features.module.modules.combat.fakelag

import net.ccbluex.liquidbounce.common.Tagged
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.network.protocol.game.ServerboundSpectatorActionPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import java.util.function.Predicate

internal enum class FakeLagFlushOn(
    override val tag: String,
    private val testPacket: Predicate<Packet<*>?>,
) : Tagged, Predicate<Packet<*>?> by testPacket {
    ENTITY_INTERACT("EntityInteract", {
        it is ServerboundInteractPacket || it is ServerboundAttackPacket || it is ServerboundSpectatorActionPacket ||
            it is ServerboundSwingPacket
    }),
    BLOCK_INTERACT("BlockInteract", {
        it is ServerboundUseItemOnPacket || it is ServerboundSignUpdatePacket
    }),
    ACTION("Action", {
        it is ServerboundPlayerActionPacket
    }),
}

internal enum class FakeLagMode(override val tag: String) : Tagged {
    CONSTANT("Constant"),
    DYNAMIC("Dynamic"),
}
