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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.type.Color4b

internal object BaseFinderRenderSettings : ToggleableValueGroup(
    ModuleBaseFinder,
    "Render",
    true,
    aliases = listOf("GlowBox"),
) {
    internal val maximumDistance by int("MaximumDistance", 512, 64..2048, "blocks")
    internal val renderLimit by int("RenderLimit", 32, 1..128, "markers")

    /** Fixed vs Dynamic footprint box — mode-owned settings only appear when selected. */
    internal val boxMode = choices("BoxMode", 0) { arrayOf(FixedBox, DynamicBox) }

    object FixedBox : Mode("Fixed") {
        override val parent: ModeValueGroup<Mode>
            get() = boxMode

        internal val boxRadius by int("BoxRadius", 4, 1..16, "blocks")
        internal val boxHeight by int("BoxHeight", 6, 1..32, "blocks")
    }

    object DynamicBox : Mode("Dynamic", aliases = listOf("Dynamic box")) {
        override val parent: ModeValueGroup<Mode>
            get() = boxMode

        internal val dynamicPadding by int("DynamicPadding", 1, 0..8, "blocks")
    }

    internal val activeBoxMode: BaseFinderBoxMode
        get() = when (boxMode.activeMode) {
            is DynamicBox -> BaseFinderBoxMode.DYNAMIC
            else -> BaseFinderBoxMode.FIXED
        }

    internal val mode = choices("Mode", 0) { arrayOf(Glow, Box) }

    /** Glow ESP style — only configurable under the Glow mode (not duplicated on the Render root). */
    object Glow : Mode("Glow") {
        override val parent: ModeValueGroup<Mode>
            get() = mode

        private val styleConfig = EspGlowStyleConfig(this)

        internal val style: EspGlowStyle
            get() = styleConfig.style
    }

    /** Through-wall boxes without the shared Gaussian glow pass. */
    object Box : Mode("Box") {
        override val parent: ModeValueGroup<Mode>
            get() = mode
    }

    internal val lowConfidenceColor by color("LowConfidenceColor", Color4b(255, 186, 32))
    internal val highConfidenceColor by color("HighConfidenceColor", Color4b(255, 60, 180))

    internal object Labels : ValueGroup("Labels") {
        internal val showLabels by boolean("ShowLabels", true)
        internal val maxLabels by int("MaxLabels", 8, 1..32)
        internal val labelText by text("LabelText", "")
        internal val labelScale by float("LabelScale", 1f, 0.5f..2.5f)
        internal val showEvidenceDetails by boolean("ShowEvidenceDetails", true)
        internal val maxEvidenceDetails by int("MaxEvidenceDetails", 4, 1..8)
    }

    init {
        tree(Labels)
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacyBaseFinderRenderConfig(jsonObject)
    }
}
