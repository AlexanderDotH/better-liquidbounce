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
package net.ccbluex.liquidbounce.event

import net.ccbluex.liquidbounce.annotations.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DynamicEventRegistrationTest {

    @Test
    fun `dynamic registration updates hooks flows and event-name catalogs`() {
        EventManager.registerEventClass(DynamicEvent::class.java)

        assertTrue(DynamicEvent::class.java in EventManager.registeredEventClasses())
        assertEquals(EVENT_NAME, DynamicEvent::class.java.eventName)
        assertSame(DynamicEvent::class.java, EVENT_NAME_TO_CLASS[EVENT_NAME.uppercase()])
    }

    @Test
    fun `runtime event access paths share exactly one registration`() {
        val listener = object : EventListener {
            override val running: Boolean = true
        }
        var observedEvent: RuntimeEvent? = null

        val flow = EventManager.eventFlow(RuntimeEvent::class.java)
        val hook = listener.handler(RuntimeEvent::class.java) { observedEvent = it }

        try {
            val event = EventManager.callEvent(RuntimeEvent())
            EventManager.registerEventClass(RuntimeEvent::class.java)

            assertSame(event, observedEvent)
            assertTrue(event.isCompleted)
            assertSame(flow, EventManager.eventFlow(RuntimeEvent::class.java))
            assertEquals(1, EventManager.registeredEventClasses().count { it == RuntimeEvent::class.java })
            assertEquals("runtimeContract", RuntimeEvent::class.java.eventName)
            assertSame(RuntimeEvent::class.java, EVENT_NAME_TO_CLASS[RuntimeEvent::class.java.eventName])
        } finally {
            EventManager.unregisterEventHook(RuntimeEvent::class.java, hook)
        }
    }

    @Test
    fun `calling a runtime event first registers and completes it`() {
        val event = EventManager.callEvent(CallFirstRuntimeEvent())

        assertTrue(event.isCompleted)
        assertEquals(1, EventManager.registeredEventClasses().count { it == CallFirstRuntimeEvent::class.java })
        assertSame(
            EventManager.eventFlow(CallFirstRuntimeEvent::class.java),
            EventManager.eventFlow(CallFirstRuntimeEvent::class.java),
        )
    }

    @Test
    fun `unmarked event hook keeps the strict registration error`() {
        val listener = object : EventListener {
            override val running: Boolean = true
        }

        val error = assertFailsWith<IllegalStateException> {
            listener.handler(UnregisteredEvent::class.java) { }
        }

        assertEquals(
            "The event '${UnregisteredEvent::class.java.name}' is not registered in Events.kt::ALL_EVENT_CLASSES.",
            error.message,
        )
    }

    @Tag(EVENT_NAME)
    private class DynamicEvent : Event()

    @Tag("runtimeContract")
    private class RuntimeEvent : Event(), RuntimeRegisteredEvent

    @Tag("callFirstRuntimeContract")
    private class CallFirstRuntimeEvent : Event(), RuntimeRegisteredEvent

    @Tag("unregisteredContract")
    private class UnregisteredEvent : Event()

    private companion object {
        const val EVENT_NAME = "dynamicRegistrationContract"
    }
}
