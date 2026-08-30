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

package net.ccbluex.liquidbounce.utils.world

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class WorldExtensionsJvmContractTest {

    @Test
    fun `split keeps the public WorldExtensionsKt JVM facade unchanged`() {
        val facade = Class.forName(FACADE, false, javaClass.classLoader)
        val actual = facade.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { method -> method.descriptor() }
            .toSet()

        assertEquals(EXPECTED_METHODS, actual)
        facade.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .forEach { assertTrue(Modifier.isStatic(it.modifiers), it.name) }
    }

    private fun Method.descriptor(): String = buildString {
        append(name)
        append(parameterTypes.joinToString(separator = ",", prefix = "(", postfix = ")") { it.name })
        append(':')
        append(returnType.name)
    }

    private companion object {
        const val FACADE = "net.ccbluex.liquidbounce.utils.world.WorldExtensionsKt"

        val EXPECTED_METHODS = setOf(
            "any(net.minecraft.world.level.entity.LevelEntityGetter," +
                "net.minecraft.world.level.entity.EntityTypeTest,java.util.function.Predicate):boolean",
            "filter(net.minecraft.world.level.entity.LevelEntityGetter," +
                "net.minecraft.world.level.entity.EntityTypeTest,java.util.function.Predicate):java.util.List",
            "filterTo(net.minecraft.world.level.entity.LevelEntityGetter,java.util.Collection," +
                "net.minecraft.world.level.entity.EntityTypeTest,java.util.function.Predicate):java.util.Collection",
            "filterTo\$default(net.minecraft.world.level.entity.LevelEntityGetter,java.util.Collection," +
                "net.minecraft.world.level.entity.EntityTypeTest,java.util.function.Predicate,int,java.lang.Object):" +
                "java.util.Collection",
            "firstOrNull(net.minecraft.world.level.entity.LevelEntityGetter," +
                "net.minecraft.world.level.entity.EntityTypeTest,java.util.function.Predicate):" +
                "net.minecraft.world.entity.Entity",
            "forEach(net.minecraft.world.level.entity.LevelEntityGetter," +
                "net.minecraft.world.level.entity.EntityTypeTest,java.util.function.Consumer):void",
            "forEachBlock(net.minecraft.world.level.chunk.LevelChunkSection,kotlin.jvm.functions.Function4):void",
            "forEachSectionBlock(net.minecraft.world.level.chunk.LevelChunk,int," +
                "net.minecraft.core.BlockPos\$MutableBlockPos,kotlin.jvm.functions.Function2):void",
            "forEachSectionBlock\$default(net.minecraft.world.level.chunk.LevelChunk,int," +
                "net.minecraft.core.BlockPos\$MutableBlockPos,kotlin.jvm.functions.Function2,int,java.lang.Object):void",
            "getBedRule(net.minecraft.world.level.Level):net.minecraft.world.attribute.BedRule",
            "getEntitiesInCube(net.minecraft.world.level.EntityGetter,net.minecraft.world.phys.Vec3,double," +
                "java.util.function.Predicate):java.util.List",
            "getEntitiesInCube(net.minecraft.world.level.EntityGetter,net.minecraft.world.phys.Vec3,double," +
                "net.minecraft.world.entity.Entity,java.util.function.Predicate):java.util.List",
            "getEntitiesInCube\$default(net.minecraft.world.level.EntityGetter,net.minecraft.world.phys.Vec3,double," +
                "java.util.function.Predicate,int,java.lang.Object):java.util.List",
            "getEntitiesInCube\$default(net.minecraft.world.level.EntityGetter,net.minecraft.world.phys.Vec3,double," +
                "net.minecraft.world.entity.Entity,java.util.function.Predicate,int,java.lang.Object):java.util.List",
            "getEntityGetter(net.minecraft.world.level.Level):net.minecraft.world.level.entity.LevelEntityGetter",
            "getFilledSections(net.minecraft.world.level.chunk.ChunkAccess):java.util.List",
            "getRespawnAnchorWorks(net.minecraft.world.level.Level):boolean",
            "getWaterEvaporates(net.minecraft.world.level.Level):boolean",
            "nextLocalEntityId(net.minecraft.world.level.Level):int",
            "none(net.minecraft.world.level.entity.LevelEntityGetter," +
                "net.minecraft.world.level.entity.EntityTypeTest,java.util.function.Predicate):boolean",
            "sectionBottomY(net.minecraft.world.level.chunk.ChunkAccess,int):int",
        )
    }
}
