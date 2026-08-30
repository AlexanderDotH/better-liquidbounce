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
@file:JvmName("CommandMaceClipKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.command


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.features.command.Parameter.Verificator.Result
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MACE_CLIP_MAXIMUM_CLEARANCE
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MACE_CLIP_MAXIMUM_DISTANCE
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MACE_CLIP_MINIMUM_CLEARANCE
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchAbortResult
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchControlRegistry
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchPacketShape
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchProbeRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchProbeStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchStatus
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.regular

internal val MACE_CLIP_DISTANCE_VERIFIER = Parameter.Verificator<Double> { sourceText ->
    sourceText.toFiniteDoubleInRange(
        minimum = 0.0,
        maximum = MACE_CLIP_MAXIMUM_DISTANCE,
        lowerExclusive = true,
        finiteErrorKey = "finiteDistance",
        rangeErrorKey = "distanceRange",
    )
}

internal val MACE_CLIP_CLEARANCE_VERIFIER = Parameter.Verificator<Double> { sourceText ->
    sourceText.toFiniteDoubleInRange(
        MACE_CLIP_MINIMUM_CLEARANCE,
        MACE_CLIP_MAXIMUM_CLEARANCE,
        lowerExclusive = false,
        finiteErrorKey = "finiteClearance",
        rangeErrorKey = "clearanceRange",
    )
}

internal val MACE_CLIP_PACKET_SHAPE_VERIFIER = Parameter.Verificator<MaceClipResearchPacketShape> { sourceText ->
    parseMaceClipPacketShape(sourceText)?.let { Result.Ok(it) }
        ?: Result.Error(validationMessage("packetShape"))
}

/** Explicit, one-shot ClipReach research probes. This command never starts an automatic sweep. */
object CommandMaceClip : Command.Factory {

    override fun createCommand(): Command = createCommand(
        startProbe = MaceClipResearchControlRegistry::startProbe,
        status = MaceClipResearchControlRegistry::status,
        abort = MaceClipResearchControlRegistry::abort,
        reportStart = MaceClipCommandReporter::reportStart,
        reportStatus = MaceClipCommandReporter::reportStatus,
        reportAbort = MaceClipCommandReporter::reportAbort,
    )

    @Suppress("LongParameterList")
    internal fun createCommand(
        startProbe: (MaceClipResearchProbeRequest) -> MaceClipResearchProbeStartResult,
        status: () -> MaceClipResearchStatus,
        abort: () -> MaceClipResearchAbortResult,
        reportStart: (MaceClipResearchProbeStartResult) -> Unit,
        reportStatus: (MaceClipResearchStatus) -> Unit,
        reportAbort: (MaceClipResearchAbortResult) -> Unit,
    ): Command = CommandBuilder
        .begin("maceclip")
        .hub()
        .subcommand(probeCommand(startProbe, reportStart))
        .subcommand(statusCommand(status, reportStatus))
        .subcommand(abortCommand(abort, reportAbort))
        .build()

    private fun probeCommand(
        startProbe: (MaceClipResearchProbeRequest) -> MaceClipResearchProbeStartResult,
        report: (MaceClipResearchProbeStartResult) -> Unit,
    ) = CommandBuilder
        .begin("probe")
        .hub()
        .requiresIngame()
        .subcommand(moveCommand(startProbe, report))
        .subcommand(attackCommand(startProbe, report))
        .build()

    private fun moveCommand(
        startProbe: (MaceClipResearchProbeRequest) -> MaceClipResearchProbeStartResult,
        report: (MaceClipResearchProbeStartResult) -> Unit,
    ) = CommandBuilder
        .begin("move")
        .requiresIngame()
        .parameter(MaceClipCommandParameters.distance())
        .let(MaceClipCommandParameters::addShared)
        .handler {
            report(
                startProbe(
                    MaceClipResearchProbeRequest.Move(
                        distance = args[0] as Double,
                        primingPackets = args[1] as Int,
                        packetShape = args[2] as MaceClipResearchPacketShape,
                        clearance = args[3] as Double,
                        phaseDelayTicks = args[4] as Int,
                        terminalHoldTicks = args[5] as Int,
                    )
                )
            )
        }
        .build()

    private fun attackCommand(
        startProbe: (MaceClipResearchProbeRequest) -> MaceClipResearchProbeStartResult,
        report: (MaceClipResearchProbeStartResult) -> Unit,
    ) = CommandBuilder
        .begin("attack")
        .requiresIngame()
        .let(MaceClipCommandParameters::addShared)
        .handler {
            report(
                startProbe(
                    MaceClipResearchProbeRequest.Attack(
                        primingPackets = args[0] as Int,
                        packetShape = args[1] as MaceClipResearchPacketShape,
                        clearance = args[2] as Double,
                        phaseDelayTicks = args[3] as Int,
                        terminalHoldTicks = args[4] as Int,
                    )
                )
            )
        }
        .build()

    private fun statusCommand(
        status: () -> MaceClipResearchStatus,
        report: (MaceClipResearchStatus) -> Unit,
    ) = CommandBuilder
        .begin("status")
        .requiresIngame()
        .handler { report(status()) }
        .build()

    private fun abortCommand(
        abort: () -> MaceClipResearchAbortResult,
        report: (MaceClipResearchAbortResult) -> Unit,
    ) = CommandBuilder
        .begin("abort")
        .requiresIngame()
        .handler { report(abort()) }
        .build()
}
