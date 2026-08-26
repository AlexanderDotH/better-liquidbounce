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
package net.ccbluex.liquidbounce.integration.theme.component.components.trialchamber

import net.ccbluex.liquidbounce.utils.render.Alignment
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialChamberHudComponentIntegrationTest {

    @Test
    fun `native TrialChamber HUD is enabled and anchored top right by default`() {
        val managerSource = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/component/HudComponentManager.kt",
        ))
        val componentSource = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/component/components/trialchamber/" +
                "TrialChamberHudComponent.kt",
        ))

        assertTrue(managerSource.contains("TrialChamberHudComponent"))
        assertTrue(componentSource.contains("enabled = true"))
        assertEquals(Alignment.ScreenAxisX.RIGHT, TrialChamberHudLayout.HORIZONTAL_ALIGNMENT)
        assertEquals(Alignment.ScreenAxisY.TOP, TrialChamberHudLayout.VERTICAL_ALIGNMENT)
        assertEquals(16, TrialChamberHudLayout.HORIZONTAL_OFFSET)
        assertEquals(16, TrialChamberHudLayout.VERTICAL_OFFSET)
        assertEquals(240.0F, TrialChamberHudLayout.WIDTH)
        assertEquals(80.0F, TrialChamberHudLayout.HEIGHT)
    }

    @Test
    fun `native TrialChamber HUD follows bundled Classic and Modern presentation`() {
        val componentSource = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/component/components/trialchamber/" +
                "TrialChamberHudComponent.kt",
        ))
        val rendererSource = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/component/components/trialchamber/" +
                "TrialChamberHudRenderer.kt",
        ))
        val drawSupportSource = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/integration/theme/component/components/trialchamber/" +
                "TrialChamberHudDrawSupport.kt",
        ))

        assertTrue(componentSource.contains("ModuleHud.theme"))
        assertTrue(componentSource.contains("isBundledHudRendered()"))
        assertTrue(componentSource.contains("resolveTrialChamberHudChrome"))
        assertTrue(componentSource.contains("buildTrialChamberHudPresentation"))
        assertTrue(rendererSource.contains("FontManager.FONT_RENDERER"))
        assertFalse(rendererSource.contains("drawTitle"))
        assertFalse(rendererSource.contains("drawStatusBadge"))
        assertTrue(rendererSource.contains("drawSpawnerPanel"))
        assertTrue(rendererSource.contains("drawVaultPanel"))
        assertTrue(rendererSource.contains("drawLootPanel"))
        assertTrue(drawSupportSource.contains("drawRoundedRect"))
        assertTrue(drawSupportSource.contains("shadow = TrialChamberHudTypography.TEXT_SHADOW"))
        assertTrue(drawSupportSource.contains(
            "\"${'$'}{stat.label} ${'$'}{stat.count}\".asPlainText(Style.EMPTY + ChatFormatting.BOLD)",
        ))
        assertTrue(rendererSource.contains("GuiGlowRenderer.requestRoundedFrame"))
        assertTrue(rendererSource.contains("chrome.backgroundBlurRadius"))
        assertFalse(rendererSource.contains("shortLabel"))
    }
}
