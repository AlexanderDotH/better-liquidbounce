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

package net.ccbluex.liquidbounce.integration.gameplay

import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.trigger.triggers.BlockChangeTrigger
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.trigger.triggers.CrystalDestroyTrigger
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.trigger.triggers.CrystalSpawnTrigger
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.trigger.triggers.EntityMoveTrigger
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.trigger.triggers.ExplodeSoundTrigger
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket

object PacketListenerTriggerHook {

    @JvmStatic
    fun entityMoved(packet: ClientboundTeleportEntityPacket) = EntityMoveTrigger.notify(packet)

    @JvmStatic
    fun blockUpdated(packet: ClientboundBlockUpdatePacket) = BlockChangeTrigger.notify(packet)

    @JvmStatic
    fun chunkBlocksUpdated(packet: ClientboundSectionBlocksUpdatePacket) =
        BlockChangeTrigger.postChunkUpdateHandler(packet)

    @JvmStatic
    fun entityAdded(packet: ClientboundAddEntityPacket) = CrystalSpawnTrigger.notify(packet)

    @JvmStatic
    fun entitySoundPlayed(packet: ClientboundSoundEntityPacket) = ExplodeSoundTrigger.notify(packet)

    @JvmStatic
    fun entitiesRemoved(packet: ClientboundRemoveEntitiesPacket) = CrystalDestroyTrigger.notify(packet)
}
