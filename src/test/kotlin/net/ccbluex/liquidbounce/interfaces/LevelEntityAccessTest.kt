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
package net.ccbluex.liquidbounce.interfaces

import net.ccbluex.liquidbounce.injection.mixins.minecraft.client.MixinLevelInvoker
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.entity.LevelEntityGetter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class LevelEntityAccessTest {

    @Test
    fun `bridge returns the exact entity getter instance`() {
        val expected = entityGetterStub()
        val access = LevelEntityAccess { expected }

        assertSame(expected, LevelEntityAccess.getEntities(access))
    }

    @Test
    fun `bridge preserves cast and provider exceptions`() {
        assertThrows(ClassCastException::class.java) {
            LevelEntityAccess.getEntities(Any())
        }

        val expected = IllegalStateException("entity storage unavailable")
        val thrown = assertThrows(IllegalStateException::class.java) {
            LevelEntityAccess.getEntities(LevelEntityAccess { throw expected })
        }
        assertSame(expected, thrown)
    }

    @Test
    fun `existing invoker stays pure and keeps its return signature`() {
        val method = MixinLevelInvoker::class.java.getDeclaredMethod("invokeGetEntities")

        assertSame(LevelEntityGetter::class.java, method.returnType)
        assertEquals(listOf("invokeGetEntities"), MixinLevelInvoker::class.java.declaredMethods.map { it.name })
        assertFalse(LevelEntityAccess::class.java.isAssignableFrom(MixinLevelInvoker::class.java))
    }

    @Test
    fun `dedicated level mixin implements and registers the stable bridge`() {
        val source = Files.readString(Path.of(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/client/MixinLevelEntityAccess.java",
        ))
        val mixinConfig = Files.readString(Path.of("src/main/resources/liquidbounce.mixins.json"))

        assertTrue(source.contains("@Mixin(Level.class)"))
        assertTrue(source.contains("implements LevelEntityAccess"))
        assertTrue(source.contains("((MixinLevelInvoker) (Object) this).invokeGetEntities()"))
        assertTrue(mixinConfig.contains("\"minecraft.client.MixinLevelEntityAccess\""))
    }

    @Suppress("UNCHECKED_CAST")
    private fun entityGetterStub(): LevelEntityGetter<Entity> = Proxy.newProxyInstance(
        LevelEntityGetter::class.java.classLoader,
        arrayOf(LevelEntityGetter::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "toString" -> "LevelEntityGetterStub"
            "hashCode" -> 1
            "equals" -> false
            else -> null
        }
    } as LevelEntityGetter<Entity>
}
