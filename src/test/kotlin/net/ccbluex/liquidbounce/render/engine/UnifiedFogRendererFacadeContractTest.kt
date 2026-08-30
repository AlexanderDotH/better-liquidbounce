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
package net.ccbluex.liquidbounce.render.engine

import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix4fc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class UnifiedFogRendererFacadeContractTest {

    @Test
    fun `unified fog keeps its Java mixin entry points`() {
        val facade = Class.forName(FACADE, false, javaClass.classLoader)
        val beginFrame = facade.getMethod("beginFrame")
        val shouldReplaceNativeFog = facade.getMethod("shouldReplaceNativeFog")
        val render = facade.getMethod("render", CameraRenderState::class.java, Matrix4fc::class.java)

        assertTrue(Modifier.isStatic(beginFrame.modifiers))
        assertTrue(Modifier.isStatic(shouldReplaceNativeFog.modifiers))
        assertTrue(Modifier.isStatic(render.modifiers))
        assertEquals(Boolean::class.javaPrimitiveType, shouldReplaceNativeFog.returnType)
    }

    private companion object {
        const val FACADE = "net.ccbluex.liquidbounce.render.engine.UnifiedFogRenderer"
    }
}
