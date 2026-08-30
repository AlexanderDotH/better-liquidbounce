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
package net.ccbluex.liquidbounce.features.module.modules.player.offhand

import net.ccbluex.liquidbounce.utils.block.getBlock
import net.ccbluex.liquidbounce.utils.block.getPotentialSecondBedBlock
import net.ccbluex.liquidbounce.utils.block.isCharged
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.entity.getDamageFromExplosion
import net.ccbluex.liquidbounce.utils.math.center
import net.ccbluex.liquidbounce.utils.world.bedRule
import net.ccbluex.liquidbounce.utils.world.respawnAnchorWorks
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.RespawnAnchorBlock

internal object ExplosiveBlockDamage : MinecraftShortcuts {

    fun maximum(allowedDamage: Float, enabled: Boolean, offsets: Array<BlockPos>?): Float {
        if (!enabled || offsets == null) {
            return 0f
        }

        val overworld = !world.bedRule.explodes
        val nether = world.respawnAnchorWorks
        val playerPos = player.blockPosition()
        val damages = offsets.asSequence()
            .map { it.offset(playerPos) }
            .mapNotNull { damageAt(it, overworld, nether) }
        return maximumUntilThreshold(allowedDamage, damages)
    }

    internal fun maximumUntilThreshold(allowedDamage: Float, damages: Sequence<Float>): Float {
        var maxDamage = 0f
        for (damage in damages) {
            maxDamage = maxDamage.coerceAtLeast(damage)
            if (maxDamage >= allowedDamage) {
                return maxDamage
            }
        }
        return maxDamage
    }

    private fun damageAt(pos: BlockPos, overworld: Boolean, nether: Boolean): Float? {
        val block = pos.getBlock()
        val state = pos.stateOrEmpty
        val noBedExplosion = overworld || block !is BedBlock
        val noAnchorExplosion = nether || block !is RespawnAnchorBlock || !block.isCharged(state)
        if (noBedExplosion && noAnchorExplosion) {
            return null
        }

        val excludedBlocks = if (noBedExplosion) {
            listOf(pos)
        } else {
            listOf(pos, block.getPotentialSecondBedBlock(state, pos))
        }
        return player.getDamageFromExplosion(
            pos = pos.center,
            power = 5f,
            explosionRange = 10f,
            damageDistance = 100f,
            exclude = excludedBlocks,
            damageSource = player.damageSources().badRespawnPointExplosion(pos.center),
        )
    }
}
