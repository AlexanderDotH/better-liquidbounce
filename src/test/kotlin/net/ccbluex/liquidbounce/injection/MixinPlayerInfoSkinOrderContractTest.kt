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

package net.ccbluex.liquidbounce.injection

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MixinPlayerInfoSkinOrderContractTest {

    @Test
    fun `skin selection keeps hide custom skin cape and fetch order`() {
        val source = Files.readString(Path.of(SOURCE))
        val skinMethod = source.method("private PlayerSkin liquid_bounce\$skin")

        skinMethod.assertInOrder(
            "HideAppearanceHook.isDestructed()",
            "liquid_bounce\$localSkin(original)",
            "liquid_bounce\$capeSkin(original)",
        )
        assertTrue(skinMethod.contains("return original;"))
    }

    @Test
    fun `custom skin lookup keeps every null guard before supplier evaluation`() {
        val source = Files.readString(Path.of(SOURCE))
        val localSkinMethod = source.method("private PlayerSkin liquid_bounce\$localSkin")

        localSkinMethod.assertInOrder(
            "ModuleSkinChanger.INSTANCE.getRunning()",
            "Minecraft.getInstance().player",
            "player.getPlayerInfo()",
            "playerListEntry.equals((PlayerInfo) (Object) this)",
            "ModuleSkinChanger.INSTANCE.getSkinTextures()",
            "customSkinTextures.get()",
        )
        assertTrue(localSkinMethod.contains("customSkinTextures == null"))
    }

    @Test
    fun `cape lookup wraps the selected skin or fetches and returns it unchanged`() {
        val source = Files.readString(Path.of(SOURCE))
        val capeSkinMethod = source.method("private PlayerSkin liquid_bounce\$capeSkin")

        capeSkinMethod.assertInOrder(
            "capeTexture != null",
            "new PlayerSkin(original.body()",
            "liquid_bounce\$fetchCapeTexture()",
            "return original;",
        )
    }

    private fun String.method(signature: String): String {
        val start = indexOf(signature)
        assertTrue(start >= 0, signature)
        val nextMethod = indexOf("\n    @", start + signature.length)
        return substring(start, if (nextMethod < 0) length else nextMethod)
    }

    private fun String.assertInOrder(vararg operations: String) {
        val positions = operations.map(::indexOf)
        assertTrue(positions.all { it >= 0 }, operations.joinToString())
        assertTrue(positions.zipWithNext().all { (left, right) -> left < right }, operations.joinToString())
    }

    private companion object {
        const val SOURCE =
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/network/MixinPlayerInfo.java"
    }
}
