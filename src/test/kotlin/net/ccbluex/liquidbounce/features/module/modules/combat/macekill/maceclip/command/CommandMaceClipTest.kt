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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.command



import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchAbortResult
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchPacketShape
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchPhase
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchProbeRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchProbeStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchStatus
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandMaceClipTest {

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `command exposes explicit probe status and abort operations`() {
        val command = command()

        assertEquals("maceclip", command.name)
        assertFalse(command.executable)
        assertEquals(listOf("probe", "status", "abort"), command.subcommands.map { it.name })
        assertEquals(listOf("move", "attack"), command.subcommands[0].subcommands.map { it.name })
        assertTrue(command.subcommands.all(Command::requiresIngame))
        assertTrue(command.subcommands[0].subcommands.all(Command::requiresIngame))
    }

    @Test
    fun `move probe parameters reject unbounded or non-finite research values`() {
        val move = moveCommand()

        assertOk(move.parameters[0], "0.01", 0.01)
        assertOk(move.parameters[0], "500", 500.0)
        assertError(move.parameters[0], "0")
        assertError(move.parameters[0], "500.01")
        assertError(move.parameters[0], "NaN")
        assertOk(move.parameters[1], "0", 0)
        assertOk(move.parameters[1], "18", 18)
        assertError(move.parameters[1], "-1")
        assertError(move.parameters[1], "19")
        assertOk(move.parameters[2], "pos", MaceClipResearchPacketShape.POSITION)
        assertOk(move.parameters[2], "positionrotation", MaceClipResearchPacketShape.POSITION_ROTATION)
        assertError(move.parameters[2], "status")
        assertOk(move.parameters[3], "4", 4.0)
        assertOk(move.parameters[3], "128", 128.0)
        assertError(move.parameters[3], "3.99")
        assertError(move.parameters[3], "128.01")
        assertOk(move.parameters[4], "0", 0)
        assertOk(move.parameters[4], "20", 20)
        assertError(move.parameters[4], "21")
        assertOk(move.parameters[5], "0", 0)
        assertOk(move.parameters[5], "20", 20)
        assertError(move.parameters[5], "-1")
    }

    @Test
    fun `move delegates exactly one bounded request`() {
        val requests = mutableListOf<MaceClipResearchProbeRequest>()
        val move = command(requests = requests).subcommands[0].subcommands.single { it.name == "move" }

        execute(move, 75.0, 9, MaceClipResearchPacketShape.POSITION, 99.0, 1, 2)

        assertEquals(
            listOf(
                MaceClipResearchProbeRequest.Move(
                    distance = 75.0,
                    primingPackets = 9,
                    packetShape = MaceClipResearchPacketShape.POSITION,
                    clearance = 99.0,
                    phaseDelayTicks = 1,
                    terminalHoldTicks = 2,
                )
            ),
            requests,
        )
    }

    @Test
    fun `attack status and abort delegate exactly once`() {
        val requests = mutableListOf<MaceClipResearchProbeRequest>()
        var statusCalls = 0
        var abortCalls = 0
        val command = command(
            requests = requests,
            status = {
                statusCalls++
                MaceClipResearchStatus.Active(
                    sessionId = "probe-1",
                    probe = MaceClipResearchProbeRequest.Probe.ATTACK,
                    phase = MaceClipResearchPhase.STRIKE,
                    profileId = "paper-26.2-build-112-unvalidated",
                    abortRequested = false,
                )
            },
            abort = {
                abortCalls++
                MaceClipResearchAbortResult.ABORT_REQUESTED
            },
        )
        val probe = command.subcommands.single { it.name == "probe" }

        execute(
            probe.subcommands.single { it.name == "attack" },
            4,
            MaceClipResearchPacketShape.POSITION_ROTATION,
            100.0,
            2,
            3,
        )
        execute(command.subcommands.single { it.name == "status" })
        execute(command.subcommands.single { it.name == "abort" })

        assertEquals(
            listOf(
                MaceClipResearchProbeRequest.Attack(
                    primingPackets = 4,
                    packetShape = MaceClipResearchPacketShape.POSITION_ROTATION,
                    clearance = 100.0,
                    phaseDelayTicks = 2,
                    terminalHoldTicks = 3,
                )
            ),
            requests,
        )
        assertEquals(1, statusCalls)
        assertEquals(1, abortCalls)
    }

    private fun moveCommand() = command().subcommands.single { it.name == "probe" }
        .subcommands.single { it.name == "move" }

    private fun command(
        requests: MutableList<MaceClipResearchProbeRequest> = mutableListOf(),
        status: () -> MaceClipResearchStatus = { MaceClipResearchStatus.Idle },
        abort: () -> MaceClipResearchAbortResult = { MaceClipResearchAbortResult.IDLE },
    ) = CommandMaceClip.createCommand(
        startProbe = { request ->
            requests += request
            MaceClipResearchProbeStartResult.STARTED
        },
        status = status,
        abort = abort,
        reportStart = {},
        reportStatus = {},
        reportAbort = {},
    )

    private fun execute(command: Command, vararg args: Any) {
        val context = Command.Handler.Context(command, args)
        with(requireNotNull(command.handler)) { context() }
    }

    private fun <T : Any> assertOk(parameter: Parameter<*>, source: String, expected: T) {
        val result = requireNotNull(parameter.verifier).verifyAndParse(source)
        assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, result)
        assertEquals(expected, (result as Parameter.Verificator.Result.Ok<*>).mappedResult)
    }

    private fun assertError(parameter: Parameter<*>, source: String) {
        assertInstanceOf(
            Parameter.Verificator.Result.Error::class.java,
            requireNotNull(parameter.verifier).verifyAndParse(source),
        )
    }
}
