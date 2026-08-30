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

package net.ccbluex.liquidbounce.utils.entity

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class EntityExtensionFacadeContractTest {

    @Test
    fun `focused entity extensions retain their shared JVM facade without structural suppressions`() {
        ENTITY_EXTENSION_FILES.forEach { fileName ->
            val sourcePath = ENTITY_ROOT.resolve(fileName)
            assertTrue(Files.isRegularFile(sourcePath), "$sourcePath must remain a production source")

            val source = Files.readString(sourcePath)
            assertTrue("@file:JvmName(\"EntityExtensionsKt\")" in source, "$fileName must retain the JVM facade")
            assertTrue("@file:JvmMultifileClass" in source, "$fileName must remain part of the multifile facade")
            assertTrue("package $ENTITY_PACKAGE" in source, "$fileName must remain in the entity package")
            assertFalse("TooManyFunctions" in source, "$fileName must not suppress structural debt")
        }
    }

    @Test
    fun `spatial queries and interpolation keep their established extension surface`() {
        val spatialQueries = source("EntitySpatialQueries.kt")
        val interpolation = source("EntityInterpolation.kt")

        listOf(
            "val Entity.lastPos: Vec3",
            "val Entity.rotation: Rotation",
            "val LocalPlayer.lastRotation: Rotation",
            "fun Position.cameraDistanceSq()",
            "fun Entity.boxedDistanceTo(entity: Entity): Double",
            "fun Entity.squareBoxedDistanceTo(entity: Entity, offsetPos: Vec3): Double",
        ).forEach { declaration -> assertTrue(declaration in spatialQueries, declaration) }

        listOf(
            "fun Entity.interpolateCurrentPosition(tickDelta: Float): Vec3",
            "fun Entity.interpolateCurrentRotation(tickDelta: Float): Rotation",
            "fun LivingEntity.interpolateBodyYaw(tickDelta: Float): Float",
            "fun LivingEntity.interpolateHeadYaw(tickDelta: Float): Float",
            "fun LivingEntity.interpolatePitch(tickDelta: Float): Float",
        ).forEach { declaration -> assertTrue(declaration in interpolation, declaration) }
    }

    private fun source(fileName: String): String = Files.readString(ENTITY_ROOT.resolve(fileName))

    private companion object {
        const val ENTITY_PACKAGE = "net.ccbluex.liquidbounce.utils.entity"
        val ENTITY_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/entity")
        val ENTITY_EXTENSION_FILES = listOf(
            "EntityFluidAndEquipment.kt",
            "EntitySpatialQueries.kt",
            "EntityInterpolation.kt",
            "LivingEntityHealth.kt",
            "PlayerEnvironment.kt",
            "PlayerMovementGeometry.kt",
            "PlayerMovementState.kt",
        )
    }
}
