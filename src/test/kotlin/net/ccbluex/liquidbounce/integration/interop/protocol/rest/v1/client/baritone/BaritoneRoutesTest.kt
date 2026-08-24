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

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineDispatcher
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneBlockPosition
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCapability
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCommandOutput
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneControlAction
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFlyOwnership
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneGoal
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogEntry
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogLevel
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneProgress
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRevision
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoute
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneRoutePoint
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSnapshot
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskKind
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneTaskRequest
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypoint
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointDraft
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointId
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointSelector
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneWaypointTag
import net.ccbluex.liquidbounce.integration.interop.HttpStatusException
import net.ccbluex.liquidbounce.integration.interop.installGson
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client.baritoneRoutes
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BaritoneRoutesTest {

    @Test
    fun `snapshot and route expose flat presentation revisions and values`() = testApplication {
        val stub = FacadeStub()
        installBaritoneRoutes(stub)

        val snapshot = client.get("$API/snapshot")
        val route = client.get("$API/route")

        assertEquals(HttpStatusCode.OK, snapshot.status)
        assertEquals(HttpStatusCode.OK, route.status)
        snapshot.json().apply {
            assertEquals(7, get("revision").asLong)
            assertEquals("AVAILABLE", get("availability").asString)
            assertEquals("PATHING", get("status").asString)
            assertEquals(0.5, get("progress").asDouble)
            getAsJsonObject("navigation").apply {
                assertEquals("FLY", get("requested").asString)
                assertEquals("FLY", get("active").asString)
                assertEquals("FLYING", get("phase").asString)
                assertEquals("Vanilla", get("flyMode").asString)
                assertEquals("BARITONE", get("ownership").asString)
                assertEquals(2, get("restartsRemaining").asInt)
            }
            assertTrue(getAsJsonArray("settings")[0].asJsonObject.get("value").asBoolean)
            assertEquals("home", getAsJsonArray("waypoints")[0].asJsonObject.get("id").asString)
        }
        route.json().apply {
            assertEquals(8, get("revision").asLong)
            assertEquals(1.25, getAsJsonArray("points")[0].asJsonObject.get("x").asDouble)
        }
    }

    @Test
    fun `task route maps all eight task kinds`() = testApplication {
        val stub = FacadeStub()
        installBaritoneRoutes(stub)
        val requests = listOf(
            """{"type":"GOTO","x":1,"y":64,"z":2}""",
            """{"type":"GET_TO_BLOCK","block":"minecraft:crafting_table"}""",
            """{"type":"MINE","block":"minecraft:diamond_ore","count":3}""",
            """{"type":"FOLLOW","player":"Alex"}""",
            """{"type":"FARM","radius":32}""",
            """{"type":"EXPLORE","x":1,"z":2,"radius":256}""",
            """{"type":"BUILD","file":"castle.schematic","x":1,"y":64,"z":2}""",
            """{"type":"ELYTRA","x":1,"y":96,"z":2}""",
        )

        requests.forEach { body ->
            assertEquals(HttpStatusCode.NoContent, client.putJson("$API/task", body).status)
        }

        val tasks = stub.calls.named("submitTask").map { it.arguments.single() as BaritoneTaskRequest }
        assertEquals(BaritoneTaskKind.entries, tasks.map(BaritoneTaskRequest::kind))
    }

    @Test
    fun `goto accepts block horizontal level and near goals`() = testApplication {
        val stub = FacadeStub()
        installBaritoneRoutes(stub)
        listOf(
            """{"type":"GOTO","x":1,"y":64,"z":2}""",
            """{"type":"GOTO","x":1,"z":2}""",
            """{"type":"GOTO","y":64}""",
            """{"type":"GOTO","x":1,"y":64,"z":2,"radius":4}""",
        ).forEach { body -> client.putJson("$API/task", body) }

        val goals = stub.calls.named("submitTask")
            .map { (it.arguments.single() as BaritoneTaskRequest.GoTo).goal }
        assertIs<BaritoneGoal.Block>(goals[0])
        assertIs<BaritoneGoal.Horizontal>(goals[1])
        assertIs<BaritoneGoal.Level>(goals[2])
        assertIs<BaritoneGoal.Near>(goals[3])
    }

    @Test
    fun `invalid input and facade failures become structured 400 409 and 503 responses`() = testApplication {
        val stub = FacadeStub()
        installBaritoneRoutes(stub)

        val invalid = client.putJson("$API/task", """{"type":"GOTO","x":1}""")
        assertError(invalid, HttpStatusCode.BadRequest, "INVALID_FIELD", "coordinates")

        val malformed = client.putJson("$API/task", "{")
        assertError(malformed, HttpStatusCode.BadRequest, "INVALID_REQUEST", null)

        stub.failure = BaritoneResult.Failure(
            BaritoneError(BaritoneErrorCode.INVALID_STATE, "Join a world", "action")
        )
        val conflict = client.putJson("$API/control", """{"action":"PAUSE"}""")
        assertError(conflict, HttpStatusCode.Conflict, "INVALID_STATE", "action")

        stub.failure = BaritoneResult.Failure(
            BaritoneError(BaritoneErrorCode.UNAVAILABLE, "Baritone is unavailable")
        )
        val unavailable = client.get("$API/completions?input=go")
        assertError(unavailable, HttpStatusCode.ServiceUnavailable, "UNAVAILABLE", null)
    }

    @Test
    fun `settings waypoints command and completions use their typed facade contracts`() = testApplication {
        val stub = FacadeStub()
        installBaritoneRoutes(stub)

        assertEquals(HttpStatusCode.OK, client.get("$API/settings/allowBreak").status)
        assertEquals(
            HttpStatusCode.NoContent,
            client.putJson("$API/settings/allowBreak", """{"value":false}""").status,
        )
        assertEquals(HttpStatusCode.NoContent, client.delete("$API/settings/allowBreak").status)
        assertEquals(HttpStatusCode.NoContent, client.post("$API/settings/reset").status)
        assertEquals(HttpStatusCode.OK, client.get("$API/waypoints").status)
        assertEquals(
            HttpStatusCode.NoContent,
            client.postJson(
                "$API/waypoints",
                """{"name":"Mine","tag":"USER","x":10,"y":12,"z":14}""",
            ).status,
        )
        assertEquals(HttpStatusCode.NoContent, client.delete("$API/waypoints/home").status)
        assertEquals(
            HttpStatusCode.NoContent,
            client.deleteJson("$API/waypoints", """{"name":"Home"}""").status,
        )
        val command = client.postJson("$API/command", """{"command":"goto 1 64 2"}""")
        assertEquals(true, command.json().get("accepted").asBoolean)
        assertEquals(listOf("goto", "goal"), Gson().fromJson(
            client.get("$API/completions?input=go").bodyAsText(),
            Array<String>::class.java,
        ).toList())

        assertIs<BaritoneSettingValue.BooleanValue>(stub.calls.named("updateSetting").single().arguments[1])
        assertIs<BaritoneWaypointDraft>(stub.calls.named("addWaypoint").single().arguments.single())
        assertIs<BaritoneWaypointSelector.ById>(stub.calls.named("deleteWaypoint")[0].arguments.single())
        assertIs<BaritoneWaypointSelector.ByName>(stub.calls.named("deleteWaypoint")[1].arguments.single())
    }

    @Test
    fun `every successful mutation dispatches through the configured write dispatcher`() = testApplication {
        val stub = FacadeStub()
        val dispatcher = RecordingDispatcher()
        installBaritoneRoutes(stub, dispatcher)

        client.putJson("$API/task", """{"type":"GOTO","y":64}""")
        client.putJson("$API/control", """{"action":"PAUSE"}""")
        client.putJson("$API/settings/allowBreak", """{"value":false}""")
        client.delete("$API/settings/allowBreak")
        client.post("$API/settings/reset")
        client.postJson("$API/waypoints", """{"name":"Mine","x":1,"y":2,"z":3}""")
        client.delete("$API/waypoints/home")
        client.postJson("$API/command", """{"command":"pause"}""")

        assertEquals(8, dispatcher.dispatchCount)
    }

    private fun ApplicationTestBuilder.installBaritoneRoutes(
        stub: FacadeStub,
        dispatcher: CoroutineDispatcher = RecordingDispatcher(),
    ) {
        application {
            install(StatusPages) {
                exception<HttpStatusException> { call, cause ->
                    call.respond(cause.status, cause.body)
                }
            }
            installGson(Gson())
            routing {
                route("/api/v1/client") {
                    baritoneRoutes(stub.facade, dispatcher)
                }
            }
        }
    }

    private suspend fun assertError(
        response: HttpResponse,
        status: HttpStatusCode,
        code: String,
        field: String?,
    ) {
        assertEquals(status, response.status)
        response.json().apply {
            assertEquals(code, get("code").asString)
            assertTrue(get("message").asString.isNotBlank())
            assertEquals(field, get("field")?.asString)
        }
    }

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
            block.run()
        }
    }

    private class FacadeStub {
        val calls = mutableListOf<FacadeCall>()
        var failure: BaritoneResult.Failure? = null
        private val setting = BaritoneSetting(
            BaritoneSettingName("allowBreak"),
            BaritoneSettingType.BOOLEAN,
            BaritoneSettingValue.BooleanValue(true),
            BaritoneSettingValue.BooleanValue(true),
            "Allows Baritone to break blocks.",
            true,
        )
        private val waypoint = BaritoneWaypoint(
            BaritoneWaypointId("home"),
            "Home",
            BaritoneWaypointTag.HOME,
            BaritoneBlockPosition(0, 64, 0),
        )
        private val snapshot = BaritoneSnapshot(
            revision = BaritoneRevision(7),
            availability = BaritoneCapability.AVAILABLE,
            status = BaritonePhase.PATHING,
            task = BaritoneTaskRequest.GoTo(BaritoneGoal.Block(BaritoneBlockPosition(1, 64, 2))),
            etaSeconds = 12,
            progress = BaritoneProgress(0.5),
            settings = listOf(setting),
            waypoints = listOf(waypoint),
            logs = listOf(BaritoneLogEntry(BaritoneRevision(6), BaritoneLogLevel.INFO, "Path ready", 1_000)),
            navigation = BaritoneNavigationSnapshot(
                requestedMode = BaritoneNavigationMode.FLY,
                activeMode = BaritoneNavigationMode.FLY,
                phase = BaritoneNavigationPhase.FLYING,
                flyMode = "Vanilla",
                flyOwnership = BaritoneFlyOwnership.BARITONE,
                detail = "Following aerial route",
                restartsRemaining = 2,
            ),
        )
        private val route = BaritoneRoute(BaritoneRevision(8), listOf(BaritoneRoutePoint(1.25, 64.0, 2.5)))

        val facade = Proxy.newProxyInstance(
            BaritoneFacade::class.java.classLoader,
            arrayOf(BaritoneFacade::class.java),
        ) { proxy, method, arguments ->
            if (method.declaringClass == Any::class.java) {
                return@newProxyInstance when (method.name) {
                    "toString" -> "FacadeStub"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === arguments?.singleOrNull()
                    else -> null
                }
            }

            val args = arguments?.toList().orEmpty()
            val methodName = method.name.substringBefore('-')
            calls += FacadeCall(methodName, args)
            failure?.let { return@newProxyInstance it }
            when (methodName) {
                "capability" -> BaritoneCapability.AVAILABLE
                "snapshot" -> snapshot
                "route" -> route
                "submitTask", "control" -> BaritoneResult.Success(snapshot)
                "settings" -> listOf(setting)
                "setting" -> setting.takeIf { requested ->
                    args.single() == requested.name || args.single() == requested.name.value
                }
                "updateSetting", "resetSetting" -> BaritoneResult.Success(setting)
                "resetSettings" -> BaritoneResult.Success(listOf(setting))
                "deleteSetting", "deleteWaypoint", "lifecycle", "clearAllKeys" -> BaritoneResult.Success(Unit)
                "waypoints" -> listOf(waypoint)
                "addWaypoint" -> BaritoneResult.Success(waypoint)
                "executeCommand" -> BaritoneResult.Success(BaritoneCommandOutput(listOf("ok")))
                "completions" -> BaritoneResult.Success(listOf("goto", "goal"))
                else -> error("Unexpected facade method ${method.name}")
            }
        } as BaritoneFacade
    }

    private data class FacadeCall(val name: String, val arguments: List<Any?>)

    private fun List<FacadeCall>.named(name: String) = filter { it.name == name }

    private suspend fun HttpResponse.json(): JsonObject = JsonParser.parseString(bodyAsText()).asJsonObject

    private companion object {
        const val API = "/api/v1/client/baritone"
    }
}

private suspend fun io.ktor.client.HttpClient.putJson(url: String, body: String) = put(url) {
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(body)
}

private suspend fun io.ktor.client.HttpClient.postJson(url: String, body: String) = post(url) {
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(body)
}

private suspend fun io.ktor.client.HttpClient.deleteJson(url: String, body: String) = delete(url) {
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(body)
}
