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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SpearKillPreviewStateBindingContractTest {

    @Test
    fun `preview configuration binds idempotently to module state without the concrete facade`() {
        val source = Files.readString(PREVIEW_SOURCE)

        assertFalse("ModuleSpearKill" in source)
        assertTrue("internal class SpearKillPreviewConfig(" in source)
        assertTrue("internal val owner: SpearKillModuleState" in source)
        assertTrue("ToggleableValueGroup(owner, \"Preview\", true)" in source)
        assertTrue("internal fun bind(parent: SpearKillModuleState): SpearKillPreviewConfig" in source)
        assertTrue("check(current.owner === parent)" in source)
        assertTrue("SpearKillPreviewConfig(parent).also { boundConfig = it }" in source)
    }

    @Test
    fun `preview compatibility facade retains defaults mode order and migration`() {
        val source = Files.readString(PREVIEW_SOURCE)

        assertTrue("var enabled: Boolean" in source)
        assertTrue("val renderPath get() = config.renderPath" in source)
        assertTrue("val mode get() = config.mode" in source)
        assertTrue("val Box get() = config.box" in source)
        assertTrue("val Glow get() = config.glow" in source)
        assertTrue("val renderPath by boolean(\"RenderPath\", false)" in source)
        assertTrue("val box = Box()" in source)
        assertTrue("val glow = Glow()" in source)
        assertTrue(source.indexOf("val box = Box()") < source.indexOf("val mode = choices"))
        assertTrue(source.indexOf("val glow = Glow()") < source.indexOf("val mode = choices"))
        assertTrue("arrayOf<Mode>(box, glow)" in source)
        assertTrue("inner class Box : Mode(\"Box\")" in source)
        assertTrue("inner class Glow : Mode(\"Glow\")" in source)
        assertTrue("override fun prepareDeserialize(jsonObject: JsonObject)" in source)
        assertTrue("migrateLegacySpearKillPreviewConfig(jsonObject)" in source)
    }

    private companion object {
        val PREVIEW_SOURCE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill/orchestration/preview/SpearKillPreview.kt",
        )
    }
}
