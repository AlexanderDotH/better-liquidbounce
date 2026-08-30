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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.type.Color4b

internal class SpearKillPreviewConfig(
    internal val owner: SpearKillModuleState,
) : ToggleableValueGroup(owner, "Preview", true) {
    val renderPath by boolean("RenderPath", false)
    val box = Box()
    val glow = Glow()
    val mode = choices("Mode", 0) { arrayOf<Mode>(box, glow) }

    inner class Box : Mode("Box") {
        override val parent: ModeValueGroup<Mode>
            get() = mode

        val fillColor by color("FillColor", Color4b.RED.alpha(67))
        val outlineColor by color("OutlineColor", Color4b.WHITE.alpha(167))
    }

    inner class Glow : Mode("Glow") {
        override val parent: ModeValueGroup<Mode>
            get() = mode

        val glowColor by color("GlowColor", Color4b.RED)
        val glowStyle = EspGlowStyleConfig(this)
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacySpearKillPreviewConfig(jsonObject)
    }
}

internal object SpearKillPreview {
    private var boundConfig: SpearKillPreviewConfig? = null

    internal fun bind(parent: SpearKillModuleState): SpearKillPreviewConfig {
        val current = boundConfig
        if (current != null) {
            check(current.owner === parent) {
                "SpearKillPreview is already bound to a different module state"
            }
            return current
        }

        return SpearKillPreviewConfig(parent).also { boundConfig = it }
    }

    private val config: SpearKillPreviewConfig
        get() = checkNotNull(boundConfig) { "SpearKillPreview has not been bound to a module state" }

    var enabled: Boolean
        get() = config.enabled
        set(value) {
            config.enabled = value
        }
    val renderPath get() = config.renderPath
    val mode get() = config.mode
    val Box get() = config.box
    val Glow get() = config.glow
}
