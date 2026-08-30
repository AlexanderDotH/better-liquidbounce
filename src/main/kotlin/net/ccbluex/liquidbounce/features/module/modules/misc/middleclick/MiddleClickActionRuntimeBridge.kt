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

package net.ccbluex.liquidbounce.features.module.modules.misc.middleclick

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult

internal interface MiddleClickActionRuntime {
    fun isActive(mode: Mode): Boolean
    fun findPlayerInCrosshair(pickUpRange: Float): Player?
    fun toggleFriend(entity: Player)
    fun setAmnesiaTarget(entity: Player): Boolean
    fun selectNukerBlock(hitResult: BlockHitResult): Boolean
}

internal object MiddleClickActionRuntimeBridge : MiddleClickActionRuntime {
    private object DisabledRuntime : MiddleClickActionRuntime {
        override fun isActive(mode: Mode) = false
        override fun findPlayerInCrosshair(pickUpRange: Float): Player? = null
        override fun toggleFriend(entity: Player) = Unit
        override fun setAmnesiaTarget(entity: Player) = false
        override fun selectNukerBlock(hitResult: BlockHitResult) = false
    }

    private var runtime: MiddleClickActionRuntime = DisabledRuntime

    fun install(runtime: MiddleClickActionRuntime) {
        this.runtime = runtime
    }

    override fun isActive(mode: Mode) = runtime.isActive(mode)
    override fun findPlayerInCrosshair(pickUpRange: Float) = runtime.findPlayerInCrosshair(pickUpRange)
    override fun toggleFriend(entity: Player) = runtime.toggleFriend(entity)
    override fun setAmnesiaTarget(entity: Player) = runtime.setAmnesiaTarget(entity)
    override fun selectNukerBlock(hitResult: BlockHitResult) = runtime.selectNukerBlock(hitResult)
}
