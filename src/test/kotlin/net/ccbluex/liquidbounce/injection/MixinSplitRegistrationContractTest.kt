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
import java.nio.file.Path
import kotlin.io.path.readText

class MixinSplitRegistrationContractTest {

    @Test
    fun `chat and submit node split mixins stay registered with their injection targets`() {
        val mixinConfig = Path.of("src/main/resources/liquidbounce.mixins.json").readText()
        val contracts = listOf(
            splitMixin(
                "minecraft.gui.MixinChatComponent",
                "gui/MixinChatComponent.java",
                "method = \"<init>(Lnet/minecraft/client/Minecraft;)V\"",
                "method = \"addMessageToQueue\"",
                "method = \"clearMessages\"",
                "method = \"addMessageToDisplayQueue\"",
            ),
            splitMixin(
                "minecraft.gui.MixinChatComponentHighlights",
                "gui/MixinChatComponentHighlights.java",
                "method = \"extractRenderState(",
            ),
            splitMixin(
                "minecraft.render.MixinSubmitNodeCollection",
                "render/MixinSubmitNodeCollection.java",
                "method = \"allPhases\"",
                "method = \"submitModel\"",
                "method = \"submitBlockModel\"",
                "method = \"submitItem\"",
                "method = \"submitCustomGeometry\"",
            ),
            splitMixin(
                "minecraft.render.MixinSubmitNodeCollectionHeldItem",
                "render/MixinSubmitNodeCollectionHeldItem.java",
                "method = \"submitModel(",
                "method = \"submitItem\"",
            ),
            splitMixin(
                "minecraft.render.MixinSubmitNodeCollectionNameTag",
                "render/MixinSubmitNodeCollectionNameTag.java",
                "method = \"submitNameTag(",
            ),
        )

        contracts.forEach { contract ->
            assertTrue("\"${contract.registration}\"" in mixinConfig, "Missing ${contract.registration}")
            val source = mixinSource(contract.relativeSource).readText()
            contract.targets.forEach { target -> assertTrue(target in source, "Missing $target in ${contract.relativeSource}") }
        }
    }

    private fun splitMixin(registration: String, relativeSource: String, vararg targets: String) =
        SplitMixinContract(registration, relativeSource, targets.asList())

    private fun mixinSource(relativeSource: String): Path = Path.of(
        "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft",
        relativeSource,
    )

    private data class SplitMixinContract(
        val registration: String,
        val relativeSource: String,
        val targets: List<String>,
    )
}
