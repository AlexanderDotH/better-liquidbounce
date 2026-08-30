/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.render.events

import net.ccbluex.liquidbounce.event.EnvironmentEvent
import net.ccbluex.liquidbounce.render.renderEnvironment
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorldRenderEventStructureTest {

    @Test
    fun `world event delegates its collector lifecycle to the render environment`() {
        val event = read(WORLD_RENDER_EVENT)
        val environment = read(RENDER_3D)

        assertTrue(event.contains("WorldRenderEnvironment.create(renderTarget, poseStack, camera)"))
        assertTrue(event.contains("environment.flush(modelViewMatrix)"))
        assertFalse(event.contains("BatchCollector"))
        assertFalse(event.contains("getDynamicTransformsUniform"))
        assertTrue(environment.contains("internal fun create("))
        assertTrue(environment.contains("internal fun flush(modelViewMatrix: Matrix4fc)"))
    }

    @Test
    fun `world render event uses neutral registration and environment contracts`() {
        val eventManager = read(EVENT_MANAGER)
        val event = read(WORLD_RENDER_EVENT)
        val environment = read(WORLD_RENDER_ENVIRONMENT)
        val facade = read(RENDER_SHORTCUTS)
        val initializer = read(CLIENT_INITIALIZER)
        val contracts = read(EVENT_BOUNDARY_CONTRACTS)
        val maskCapture = read(ESP_MASK_CAPTURE)
        val shaderRenderer = read(ESP_SHADER_RENDERER)

        assertFalse(eventManager.contains("import net.ccbluex.liquidbounce.render.events.WorldRenderEvent"))
        assertFalse(eventManager.contains("WorldRenderEvent::class.java,"))
        assertFalse(eventManager.contains("import net.ccbluex.liquidbounce.event.events.WorldRenderEvent"))
        assertTrue(event.contains("RuntimeRegisteredEvent"))
        assertTrue(event.contains("EnvironmentEvent<WorldRenderEnvironment>"))
        assertTrue(event.contains("WorldRenderContext"))
        assertTrue(contracts.contains("interface WorldRenderContext"))
        assertTrue(environment.contains("import net.ccbluex.liquidbounce.event.EnvironmentEvent"))
        assertTrue(environment.contains("inline fun <E> EnvironmentEvent<E>.renderEnvironment"))
        assertFalse(environment.contains("import net.ccbluex.liquidbounce.render.events.WorldRenderEvent"))
        assertTrue(facade.contains("import net.ccbluex.liquidbounce.event.EnvironmentEvent;"))
        assertFalse(facade.contains("import net.ccbluex.liquidbounce.render.events.WorldRenderEvent;"))
        assertTrue(maskCapture.contains("import net.ccbluex.liquidbounce.event.WorldRenderContext"))
        assertFalse(maskCapture.contains("import net.ccbluex.liquidbounce.render.events.WorldRenderEvent"))
        assertTrue(shaderRenderer.contains("import net.ccbluex.liquidbounce.event.WorldRenderContext"))
        assertFalse(shaderRenderer.contains("import net.ccbluex.liquidbounce.render.events.WorldRenderEvent"))
        assertEquals(
            1,
            initializer.occurrencesOf("EventManager.registerEventClass(WorldRenderEvent::class.java)"),
        )
    }

    @Test
    fun `render environment extension executes against the exact environment instance`() {
        val expectedEnvironment = Any()
        var actualEnvironment: Any? = null
        val event = object : EnvironmentEvent<Any> {
            override val environment: Any = expectedEnvironment
        }

        event.renderEnvironment {
            actualEnvironment = this
        }

        assertSame(expectedEnvironment, actualEnvironment)
    }

    private fun read(path: String): String = Files.readString(Path.of(path))

    private fun String.occurrencesOf(value: String): Int = windowed(value.length).count { it == value }

    private companion object {
        const val WORLD_RENDER_EVENT =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/events/WorldRenderEvent.kt"
        const val RENDER_3D = "src/main/kotlin/net/ccbluex/liquidbounce/render/Render3D.kt"
        const val EVENT_MANAGER = "src/main/kotlin/net/ccbluex/liquidbounce/event/EventManager.kt"
        const val EVENT_BOUNDARY_CONTRACTS =
            "src/main/kotlin/net/ccbluex/liquidbounce/event/EventBoundaryContracts.kt"
        const val WORLD_RENDER_ENVIRONMENT =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/WorldRenderEnvironment.kt"
        const val RENDER_SHORTCUTS = "src/main/java/net/ccbluex/liquidbounce/render/RenderShortcutsKt.java"
        const val ESP_MASK_CAPTURE =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/esp/EspMaskCapture.kt"
        const val ESP_SHADER_RENDERER =
            "src/main/kotlin/net/ccbluex/liquidbounce/render/engine/esp/EspShaderRenderer.kt"
        const val CLIENT_INITIALIZER =
            "src/main/kotlin/net/ccbluex/liquidbounce/bootstrap/liquidbounce/ClientInitializer.kt"
    }
}
