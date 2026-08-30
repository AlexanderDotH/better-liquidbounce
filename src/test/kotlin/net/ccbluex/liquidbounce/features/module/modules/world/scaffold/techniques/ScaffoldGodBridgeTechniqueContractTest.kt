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
package net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ScaffoldGodBridgeTechniqueContractTest {

    @Test
    fun `ledge keeps selection simulation raycast and action order`() {
        assertInOrder(
            techniqueSource,
            "if (!isSelected)",
            "PlayerSimulationCache.getSimulationForLocalPlayer()",
            "simulatedPlayerCache.getSnapshotAt(1)",
            "debugParameter(\"Snapshot Ledged\")",
            "if (!snapshotOne.clipLedged)",
            "snapshotOne.pos.add(0.0, player.eyeHeight.toDouble(), 0.0)",
            "traceFromPoint(start = cameraPosition, direction = rotation.directionVector)",
            "if (target == null)",
            "target.doesCrosshairTargetMatchRequirements(currentCrosshairTarget)",
            "ModuleScaffold.isValidCrosshairTarget(currentCrosshairTarget)",
            "debugParameter(\"targetFulfillsRequirements\")",
            "debugParameter(\"isValidCrosshairTarget\")",
            "if (targetFulfillsRequirements && isValidCrosshairTarget)",
            "if (ModuleScaffold.blockCount < forceSneakBelowCount) Mode.SNEAK else modes.random()",
            "GodBridgeLedgeModeSelector.select(",
            "effectiveMode.creator.get().also",
            "debugParameter(\"LastLedgeAction\")",
        )
    }

    @Test
    fun `configuration and public technique contracts remain stable`() {
        assertInOrder(
            techniqueSource,
            "ScaffoldTechnique(\"GodBridge\"), ScaffoldLedgeExtension",
            "multiEnumChoice(\"Modes\", Mode.JUMP, canBeNone = false)",
            "int(\"ForceSneakBelowCount\", 3, 0..10)",
            "intRange(\"SneakTime\", 1..1, 1..10)",
            "override fun ledge(",
            "override fun findPlacementTarget(",
            "override fun getRotations(",
        )
        assertFalse("@Suppress" in techniqueSource)
    }

    @Test
    fun `placement and rotation decisions retain their order`() {
        assertInOrder(
            techniqueSource,
            "BlockPlacementTargetFindingOptions(",
            "BlockOffsetOptions(",
            "FaceHandlingOptions(CenterTargetPositionFactory)",
            "stackToPlaceWith = bestStack",
            "PlayerLocationOnPlacement(position = predictedPos, pose = predictedPose)",
            "findBestBlockPlacementTarget(getTargetedPosition(predictedPos.toBlockPos()), searchOptions)",
        )
        assertInOrder(
            techniqueSource,
            "if (rawInput == DirectionalInput.NONE)",
            "target ?: return null",
            "getRotationForNoInput(target)",
            "player.getMovementDirectionOfInput(rawInput) + 180",
            "round(direction / 45) * 45",
            "movingYaw % 90 == 0f",
            "getRotationForStraightInput(movingYaw)",
            "getRotationForDiagonalInput(movingYaw)",
        )
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, startIndex = previousIndex + 1)
            assertTrue(index > previousIndex, "Expected `$marker` after index $previousIndex")
            previousIndex = index
        }
    }

    private companion object {
        val TECHNIQUES_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/scaffold/techniques",
        )
        val techniqueSource: String = Files.readString(TECHNIQUES_ROOT.resolve("ScaffoldGodBridgeTechnique.kt"))
    }
}
