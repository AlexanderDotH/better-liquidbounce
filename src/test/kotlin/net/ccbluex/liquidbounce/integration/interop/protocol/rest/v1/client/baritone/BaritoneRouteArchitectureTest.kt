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
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client.baritone

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains

class BaritoneRouteArchitectureTest {

    private val sourcePath = Path.of(
        "src/main/kotlin/net/ccbluex/liquidbounce/integration/interop/protocol/rest/v1/client/BaritoneFunctions.kt"
    )

    @Test
    fun `baritone route exposes the complete public HTTP contract`() {
        val source = Files.readString(sourcePath)

        listOf(
            "route(\"/baritone\")",
            "get(\"/snapshot\")",
            "get(\"/route\")",
            "put(\"/task\")",
            "put(\"/control\")",
            "get(\"/settings/{name}\")",
            "put(\"/settings/{name}\")",
            "delete(\"/settings/{name}\")",
            "post(\"/settings/reset\")",
            "get(\"/waypoints\")",
            "post(\"/waypoints\")",
            "delete(\"/waypoints\")",
            "delete(\"/waypoints/{id}\")",
            "post(\"/command\")",
            "get(\"/completions\")",
        ).forEach { declaration ->
            assertContains(source, declaration)
        }
    }

    @Test
    fun `every mutating handler crosses the injected Minecraft dispatcher boundary`() {
        val source = Files.readString(sourcePath)

        assertContains(source, "writeDispatcher: CoroutineDispatcher = Dispatchers.Minecraft")
        assertContains(source, "withContext(writeDispatcher)")
    }

    @Test
    fun `baritone failures use the structured error contract`() {
        val source = Files.readString(sourcePath)

        assertContains(source, "\"code\" to")
        assertContains(source, "\"message\" to")
        assertContains(source, "HttpStatusCode.BadRequest")
        assertContains(source, "HttpStatusCode.Conflict")
        assertContains(source, "HttpStatusCode.ServiceUnavailable")
    }
}
