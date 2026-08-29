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
package net.ccbluex.liquidbounce.features.module.modules.world

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoToolPacketRenderContractTest {

    @Test
    fun `packet selection keeps the live client slot visible in first person`() {
        val method = bracedDeclaration(firstPersonSource, "private ItemStack injectSilentHotbar")
        val packetBranch = bracedDeclaration(
            method,
            "if (SilentHotbar.INSTANCE.getShouldKeepClientSlotVisible())",
        )
        val standardBranch = bracedDeclaration(
            method,
            "if (ModuleSilentHotbar.INSTANCE.getRunning())",
        )

        assertContains(packetBranch, "SilentHotbar.INSTANCE.getVisualSlot()")
        assertFalse(packetBranch.contains("ModuleSilentHotbar"))
        assertContains(standardBranch, "SilentHotbar.INSTANCE.getClientsideSlot()")
        assertTrue(method.indexOf(packetBranch) < method.indexOf(standardBranch))
    }

    @Test
    fun `packet selection suppresses first person swap progress without the render module`() {
        val method = bracedDeclaration(firstPersonSource, "private float injectSilentHotbarNoCooldown")

        assertTrue(
            Regex(
                """if\s*\(\s*SilentHotbar\.INSTANCE\.getShouldKeepClientSlotVisible\(\)\s*\|\|\s*""" +
                    """\(ModuleSilentHotbar\.INSTANCE\.getRunning\(\)\s*&&\s*""" +
                    """ModuleSilentHotbar\.INSTANCE\.getNoCooldownProgress\(\)\s*&&\s*""" +
                    """SilentHotbar\.INSTANCE\.isSlotModified\(\)\)\s*\)\s*\{\s*return 1f;""",
            ).containsMatchIn(method),
        )
    }

    @Test
    fun `packet selection changes only the local main arm in third person`() {
        val method = bracedDeclaration(thirdPersonSource, "private static ItemStack hideOffhandShield")
        val packetBranch = bracedDeclaration(method, "if (entity == localPlayer")
        val swordBlockBranch = bracedDeclaration(
            method,
            "if (entity == Minecraft.getInstance().player",
        )

        assertContains(packetBranch, "arm == reusedState.mainArm")
        assertContains(packetBranch, "SilentHotbar.INSTANCE.getShouldKeepClientSlotVisible()")
        assertContains(packetBranch, "SilentHotbar.INSTANCE.getVisualSlot()")
        assertContains(swordBlockBranch, "ModuleSwordBlock.INSTANCE.shouldHideOffhand()")
        assertContains(swordBlockBranch, "arm != reusedState.mainArm")
        assertContains(method, "return original.call(entity, arm);")
    }

    private fun bracedDeclaration(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        check(markerIndex >= 0) { "Missing declaration marker: $marker" }
        val openingBrace = source.indexOf('{', markerIndex)
        check(openingBrace >= 0) { "Missing opening brace after: $marker" }

        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(markerIndex, index + 1)
            }
        }
        error("Unclosed declaration after: $marker")
    }

    private companion object {
        val firstPersonSource: String = Files.readString(
            Path.of(
                "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/item/" +
                    "MixinItemInHandRenderer.java",
            ),
        )
        val thirdPersonSource: String = Files.readString(
            Path.of(
                "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/render/entity/" +
                    "MixinArmedEntityRenderState.java",
            ),
        )
    }
}
