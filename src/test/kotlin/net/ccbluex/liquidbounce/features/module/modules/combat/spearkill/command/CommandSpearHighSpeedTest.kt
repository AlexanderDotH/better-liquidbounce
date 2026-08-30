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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.command


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchFinalPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeStartResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandSpearHighSpeedTest {

    @Test
    fun `command exposes only one-shot move and attack probes`() {
        val command = command()

        assertEquals("spearhighspeed", command.name)
        assertFalse(command.executable)
        assertEquals(listOf("move", "attack"), command.subcommands.map { it.name })
        assertTrue(command.subcommands.all(Command::requiresIngame))
    }

    @Test
    fun `move accepts only bounded finite distances`() {
        val distance = command().subcommands.single { it.name == "move" }.parameters[0]
        val verifier = requireNotNull(distance.verifier)

        assertOk(verifier, "0.01", 0.01)
        assertOk(verifier, "200", 200.0)
        assertError(verifier, "0")
        assertError(verifier, "200.01")
        assertError(verifier, "NaN")
        assertError(verifier, "Infinity")
    }

    @Test
    fun `priming count supports the requested zero through eighteen matrix`() {
        val primingPackets = command().subcommands.single { it.name == "move" }.parameters[1]
        val verifier = requireNotNull(primingPackets.verifier)

        assertOk(verifier, "0", 0)
        assertOk(verifier, "18", 18)
        assertError(verifier, "-1")
        assertError(verifier, "19")
    }

    @Test
    fun `packet type parsers accept documented tokens and reject unknown values`() {
        val move = command().subcommands.single { it.name == "move" }
        val primingType = requireNotNull(move.parameters[2].verifier)
        val finalType = requireNotNull(move.parameters[3].verifier)

        assertOk(primingType, "position", SpearKillHighSpeedResearchPacketType.POSITION)
        assertOk(primingType, "posrot", SpearKillHighSpeedResearchPacketType.POSITION_ROTATION)
        assertOk(primingType, "rotation", SpearKillHighSpeedResearchPacketType.ROTATION)
        assertOk(primingType, "statusonly", SpearKillHighSpeedResearchPacketType.STATUS_ONLY)
        assertOk(finalType, "pos", SpearKillHighSpeedResearchFinalPacketType.POSITION)
        assertOk(finalType, "posrot", SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION)
        assertError(primingType, "full")
        assertError(finalType, "rotation")
    }

    @Test
    fun `move delegates exactly one probe with position rotation as default final packet`() {
        val requests = mutableListOf<SpearKillHighSpeedResearchProbeRequest>()
        val results = mutableListOf<SpearKillHighSpeedResearchProbeStartResult>()
        val command = command(requests, results)
        val move = command.subcommands.single { it.name == "move" }

        execute(
            move,
            100.0,
            9,
            SpearKillHighSpeedResearchPacketType.POSITION,
        )

        assertEquals(
            listOf(
                SpearKillHighSpeedResearchProbeRequest.Move(
                    distance = 100.0,
                    primingPackets = 9,
                    primingPacketType = SpearKillHighSpeedResearchPacketType.POSITION,
                    finalPacketType = SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION,
                )
            ),
            requests,
        )
        assertEquals(listOf(SpearKillHighSpeedResearchProbeStartResult.STARTED), results)
    }

    @Test
    fun `attack delegates exactly one probe with an explicit final packet type`() {
        val requests = mutableListOf<SpearKillHighSpeedResearchProbeRequest>()
        val results = mutableListOf<SpearKillHighSpeedResearchProbeStartResult>()
        val command = command(requests, results)
        val attack = command.subcommands.single { it.name == "attack" }

        execute(
            attack,
            4,
            SpearKillHighSpeedResearchPacketType.STATUS_ONLY,
            SpearKillHighSpeedResearchFinalPacketType.POSITION,
        )

        assertEquals(
            listOf(
                SpearKillHighSpeedResearchProbeRequest.Attack(
                    primingPackets = 4,
                    primingPacketType = SpearKillHighSpeedResearchPacketType.STATUS_ONLY,
                    finalPacketType = SpearKillHighSpeedResearchFinalPacketType.POSITION,
                )
            ),
            requests,
        )
        assertEquals(listOf(SpearKillHighSpeedResearchProbeStartResult.STARTED), results)
    }

    private fun command(
        requests: MutableList<SpearKillHighSpeedResearchProbeRequest> = mutableListOf(),
        results: MutableList<SpearKillHighSpeedResearchProbeStartResult> = mutableListOf(),
    ) = CommandSpearHighSpeed.createCommand(
        startProbe = { request ->
            requests += request
            SpearKillHighSpeedResearchProbeStartResult.STARTED
        },
        reportResult = results::add,
    )

    private fun execute(command: Command, vararg args: Any) {
        val context = Command.Handler.Context(command, args)
        with(requireNotNull(command.handler)) {
            context()
        }
    }

    private fun <T : Any> assertOk(verifier: Parameter.Verificator<*>, source: String, expected: T) {
        val result = verifier.verifyAndParse(source)
        assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, result)
        assertEquals(expected, (result as Parameter.Verificator.Result.Ok<*>).mappedResult)
    }

    private fun assertError(verifier: Parameter.Verificator<*>, source: String) {
        assertInstanceOf(Parameter.Verificator.Result.Error::class.java, verifier.verifyAndParse(source))
    }
}
