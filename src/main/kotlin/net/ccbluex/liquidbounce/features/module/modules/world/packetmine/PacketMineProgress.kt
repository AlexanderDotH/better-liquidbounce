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
package net.ccbluex.liquidbounce.features.module.modules.world.packetmine

import net.ccbluex.liquidbounce.render.progress.BreakingProgress
import net.ccbluex.liquidbounce.utils.block.outlineBox
import net.ccbluex.liquidbounce.utils.client.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot

internal object PacketMineProgress : MinecraftShortcuts {

    fun update(mineTarget: MineTarget, slot: HotbarItemSlot?) {
        val switchMode = ModulePacketMine.switchMode.activeMode
        mineTarget.progress += switchMode.getBlockBreakingDelta(
            mineTarget.targetPos,
            mineTarget.blockState,
            slot?.itemStack,
        )

        ModulePacketMine.switch(slot, mineTarget)
        if (switchMode.getSwitchingMethod().shouldSync) {
            interaction.ensureHasSentCarriedItem()
        }

        val damage = ModulePacketMine.breakDamage
        val scale = if (damage > 0f) {
            mineTarget.progress.coerceIn(0f, damage) / damage * 0.5f
        } else {
            0.5f
        }

        val box = mineTarget.targetPos.outlineBox
        ModulePacketMine.targetRenderer.updateBox(
            mineTarget.targetPos,
            box.inflate(
                box.xsize * (scale - 0.5f),
                box.ysize * (scale - 0.5f),
                box.zsize * (scale - 0.5f),
            ),
        )
    }

    fun current(): BreakingProgress? {
        val target = ModulePacketMine._target?.takeIf { it.started } ?: return null
        val damage = ModulePacketMine.breakDamage
        val progress = if (damage > 0f) target.progress / damage else 1f
        return BreakingProgress(target.targetPos, progress.coerceIn(0f, 1f))
    }
}
