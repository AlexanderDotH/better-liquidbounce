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
package net.ccbluex.liquidbounce.common.task

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskProgressModelTest {

    @Test
    fun `parent progress and completion reflect every stable child`() {
        val parent = Task("parent")
        val first = parent.getOrCreateTask("first")
        val sameFirst = parent.getOrCreateTask("first")
        val second = parent.getOrCreateFileTask("second")

        first.progress = 0.25f
        first.isCompleted = true
        second.update(bytesRead = 75, contentLength = 100)

        assertSame(first, sameFirst)
        assertEquals(0.5f, parent.progress)
        assertFalse(parent.isCompleted)

        second.isCompleted = true
        parent.isCompleted = true
        assertTrue(parent.isCompleted)
    }
}
