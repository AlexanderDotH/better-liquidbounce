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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.type.Color4b


internal class MaceKillPreview(parent: MaceKillModuleState) : ToggleableValueGroup(parent, "Preview", true) {
    val renderPath by boolean("RenderPath", false)
    val glow = Glow()
    val mode = choices("Mode", 0) { arrayOf<Mode>(glow) }

    inner class Glow : Mode("Glow") {
        override val parent: ModeValueGroup<Mode>
            get() = mode

        val glowColor by color("GlowColor", Color4b.RED)
        val glowStyle = EspGlowStyleConfig(this)
    }
}
