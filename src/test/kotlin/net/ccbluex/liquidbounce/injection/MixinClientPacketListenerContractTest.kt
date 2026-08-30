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

class MixinClientPacketListenerContractTest {

    @Test
    fun `packet listener keeps all injection targets during bridge extraction`() {
        val source = Path.of(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/network/" +
                "MixinClientPacketListener.java",
        ).readText()
        val targets = listOf(
            "method = \"<init>\"",
            "method = \"handleRespawn\"",
            "method = \"handleLevelChunkWithLight\"",
            "method = \"handleForgetLevelChunk\"",
            "method = \"handleChunkBlocksUpdate\"",
            "method = \"handleTeleportEntity\"",
            "method = \"handleBlockUpdate\"",
            "method = \"handleAddEntity\"",
            "method = \"handleSoundEntityEvent\"",
            "method = \"handleRemoveEntities\"",
            "method = \"setTitleText\"",
            "method = \"setSubtitleText\"",
            "method = \"setTitlesAnimation\"",
            "method = \"handleTitlesClear\"",
            "method = \"handleExplosion\"",
            "method = \"handleParticleEvent\"",
            "method = \"handleGameEvent\"",
            "method = \"handleSetHealth\"",
            "method = \"handlePlayerAbilities\"",
            "method = \"handleMovePlayer\"",
            "method = \"sendChat\"",
        )

        targets.forEach { target -> assertTrue(target in source, "Missing injection target $target") }
    }
}
