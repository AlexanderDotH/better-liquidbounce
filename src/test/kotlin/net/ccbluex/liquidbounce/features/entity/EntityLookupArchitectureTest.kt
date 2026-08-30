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

package net.ccbluex.liquidbounce.features.entity

import it.unimi.dsi.fastutil.objects.ReferenceCollection
import net.ccbluex.liquidbounce.event.EventListener
import net.minecraft.world.level.entity.LevelEntityGetter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class EntityLookupArchitectureTest {

    @Test
    fun `lookup keeps its delegate and Java factory contract`() {
        val lookup = EntityLookup::class.java
        val factory = lookup.getDeclaredMethod("create", EventListener::class.java, EntityLookup.Collector::class.java)
        val getter = lookup.getDeclaredMethod("getValue", Any::class.java, kotlin.reflect.KProperty::class.java)

        assertTrue(Modifier.isStatic(factory.modifiers))
        assertSame(lookup, factory.returnType)
        assertSame(ReferenceCollection::class.java, getter.returnType)
        assertEquals(setOf("clear", "create", "getValue"), lookup.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.name }
            .toSet())

        val collect = EntityLookup.Collector::class.java.declaredMethods.single()
        assertEquals("collect", collect.name)
        assertEquals(
            listOf(LevelEntityGetter::class.java, ReferenceCollection::class.java),
            collect.parameterTypes.toList(),
        )
    }

    @Test
    fun `lookup reads the same Minecraft level without a module dependency`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/entity/EntityLookup.kt",
        ))

        assertTrue(source.contains("Minecraft.getInstance().level?.entityGetter?.collect(entities)"))
        assertFalse(source.contains("features.module.MinecraftShortcuts"))
        assertFalse(source.contains(": MinecraftShortcuts"))
    }
}
