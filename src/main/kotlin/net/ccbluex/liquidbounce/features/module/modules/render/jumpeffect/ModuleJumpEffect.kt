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
package net.ccbluex.liquidbounce.features.module.modules.render.jumpeffect

import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.jumpeffect.modes.JumpEffectImage
import net.ccbluex.liquidbounce.features.module.modules.render.jumpeffect.modes.JumpEffectSimple
import net.ccbluex.liquidbounce.features.module.modules.render.jumpeffect.runtime.JumpEffectRuntime

object ModuleJumpEffect : ClientModule("JumpEffect", ModuleCategories.RENDER) {

    private val runtime = JumpEffectRuntime(this)

    val modes = choices("Mode", 0) { parent ->
        arrayOf(
            JumpEffectSimple(parent, runtime),
            JumpEffectImage(parent, runtime),
        )
    }.apply {
        tagBy(this)
        onChanged {
            runtime.circles.clear()
        }
    }

    @Suppress("unused")
    val playerJumpHandler = handler<PlayerJumpEvent> { _ ->
        // Adds new circle when the player jumps
        runtime.circles.add(player.position(), modes.activeMode.lifetime.last)
    }

    override fun onDisabled() {
        runtime.circles.clear()
    }

}
