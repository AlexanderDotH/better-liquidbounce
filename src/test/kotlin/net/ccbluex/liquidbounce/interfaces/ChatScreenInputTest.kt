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

import net.ccbluex.liquidbounce.injection.mixins.minecraft.gui.MixinChatScreenAccessor
import net.minecraft.client.gui.components.EditBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.jspecify.annotations.Nullable
import java.nio.file.Files
import java.nio.file.Path

class ChatScreenInputTest {

    @Test
    fun `start typing fails closed for screens without the bridge`() {
        val screenParameter = ChatScreenInput::class.java
            .getDeclaredMethod("startTyping", Object::class.java, String::class.java)
            .parameters
            .first()

        assertTrue(screenParameter.annotatedType.isAnnotationPresent(Nullable::class.java))
        assertFalse(ChatScreenInput.startTyping(null, "hello"))
        assertFalse(ChatScreenInput.startTyping(Any(), "hello"))
    }

    @Test
    fun `start typing delegates the exact initial text once`() {
        val received = mutableListOf<String>()
        val input = ChatScreenInput { text ->
            received += text
            true
        }

        assertTrue(ChatScreenInput.startTyping(input, "initial text"))
        assertEquals(listOf("initial text"), received)
    }

    @Test
    fun `start typing preserves a bridge refusal for the caller fallback`() {
        assertFalse(ChatScreenInput.startTyping(ChatScreenInput { false }, "draft"))
    }

    @Test
    fun `start typing does not hide an input failure`() {
        val failure = IllegalStateException("input unavailable")
        val input = ChatScreenInput { throw failure }

        val thrown = assertThrows(IllegalStateException::class.java) {
            ChatScreenInput.startTyping(input, "draft")
        }

        assertEquals(failure, thrown)
    }

    @Test
    fun `existing accessor stays a pure accessor with its original signature`() {
        val accessorMethod = MixinChatScreenAccessor::class.java.getDeclaredMethod("getInput")

        assertSame(EditBox::class.java, accessorMethod.returnType)
        assertEquals(listOf("getInput"), MixinChatScreenAccessor::class.java.declaredMethods.map { it.name })
        assertFalse(ChatScreenInput::class.java.isAssignableFrom(MixinChatScreenAccessor::class.java))
    }

    @Test
    fun `dedicated chat screen mixin owns the bridge and fails closed before input initialization`() {
        val source = Files.readString(Path.of(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/minecraft/gui/MixinChatScreenInput.java",
        ))
        val mixinConfig = Files.readString(Path.of("src/main/resources/liquidbounce.mixins.json"))

        assertTrue(source.contains("@Mixin(ChatScreen.class)"))
        assertTrue(source.contains("implements ChatScreenInput"))
        assertTrue(source.contains("@Shadow"))
        assertTrue(source.contains("private @Nullable EditBox input"))
        assertTrue(source.contains("if (input == null)"))
        assertTrue(source.contains("input.setValue(text)"))
        assertTrue(source.contains("return false"))
        assertTrue(source.contains("return true"))
        assertTrue(mixinConfig.contains("\"minecraft.gui.MixinChatScreenInput\""))
    }
}
