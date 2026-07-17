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

package net.ccbluex.liquidbounce.utils.render

import net.minecraft.client.renderer.entity.state.EntityRenderState

/**
 * Tracks the [EntityRenderState] currently being submitted so nametag hooks can
 * identify which entity a deferred [SubmitNodeCollection.submitNameTag] belongs to.
 */
object NametagSubmitContext {

    private val currentState = ThreadLocal<EntityRenderState?>()

    @JvmStatic
    fun push(state: EntityRenderState) {
        currentState.set(state)
    }

    @JvmStatic
    fun pop() {
        currentState.remove()
    }

    @JvmStatic
    fun get(): EntityRenderState? = currentState.get()
}
