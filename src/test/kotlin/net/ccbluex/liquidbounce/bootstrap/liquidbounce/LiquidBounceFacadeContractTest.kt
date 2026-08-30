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

package net.ccbluex.liquidbounce.bootstrap.liquidbounce

import net.ccbluex.liquidbounce.event.EventListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiquidBounceFacadeContractTest {
    @Test
    fun `bootstrap facade keeps public fqcn constants and resource entry points`() {
        val facade = Class.forName("net.ccbluex.liquidbounce.LiquidBounce", false, javaClass.classLoader)

        assertNotNull(facade.getMethod("identifier", String::class.java))
        assertNotNull(facade.getMethod("resource", String::class.java))
        assertNotNull(facade.getMethod("resourceToString", String::class.java))
        assertNotNull(facade.getMethod("getClientVersion"))
        assertNotNull(facade.getMethod("getClientCommit"))
        assertNotNull(facade.getMethod("getClientBranch"))
        assertNotNull(facade.getMethod("getTaskManager"))
        assertNotNull(facade.getMethod("setTaskManager", Class.forName(
            "net.ccbluex.liquidbounce.integration.task.TaskManager",
            false,
            javaClass.classLoader,
        )))
        assertNotNull(facade.getMethod("isInitialized"))
        assertEquals("LiquidBounce", facade.getField("CLIENT_NAME").get(null))
        assertEquals("CCBlueX", facade.getField("CLIENT_AUTHOR").get(null))
        assertEquals(true, facade.getField("IN_DEVELOPMENT").get(null))
        assertTrue(EventListener::class.java.isAssignableFrom(facade))
    }
}
