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
package net.ccbluex.liquidbounce.features.command.commands.ingame

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.features.command.Parameter.Verificator.Result
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.module.modules.combat.MACE_CLIP_MAXIMUM_CLEARANCE
import net.ccbluex.liquidbounce.features.module.modules.combat.MACE_CLIP_MAXIMUM_DISTANCE
import net.ccbluex.liquidbounce.features.module.modules.combat.MACE_CLIP_MAXIMUM_PHASE_DELAY_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.MACE_CLIP_MAXIMUM_PRIMING_PACKETS
import net.ccbluex.liquidbounce.features.module.modules.combat.MACE_CLIP_MAXIMUM_TERMINAL_HOLD_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.MACE_CLIP_MINIMUM_CLEARANCE
import net.ccbluex.liquidbounce.features.module.modules.combat.MaceClipResearchAbortResult
import net.ccbluex.liquidbounce.features.module.modules.combat.MaceClipResearchControlRegistry
import net.ccbluex.liquidbounce.features.module.modules.combat.MaceClipResearchPacketShape
import net.ccbluex.liquidbounce.features.module.modules.combat.MaceClipResearchProbeRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.MaceClipResearchProbeStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.MaceClipResearchStatus
import net.ccbluex.liquidbounce.lang.LanguageManager
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.regular
import java.util.Locale

private val MACE_CLIP_DISTANCE_VERIFIER = Parameter.Verificator<Double> { sourceText ->
    sourceText.toFiniteDoubleInRange(
        minimum = 0.0,
        maximum = MACE_CLIP_MAXIMUM_DISTANCE,
        lowerExclusive = true,
        finiteErrorKey = "finiteDistance",
        rangeErrorKey = "distanceRange",
    )
}

private val MACE_CLIP_CLEARANCE_VERIFIER = Parameter.Verificator<Double> { sourceText ->
    sourceText.toFiniteDoubleInRange(
        MACE_CLIP_MINIMUM_CLEARANCE,
        MACE_CLIP_MAXIMUM_CLEARANCE,
        lowerExclusive = false,
        finiteErrorKey = "finiteClearance",
        rangeErrorKey = "clearanceRange",
    )
}

private val MACE_CLIP_PACKET_SHAPE_VERIFIER = Parameter.Verificator<MaceClipResearchPacketShape> { sourceText ->
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

private object MaceClipCommandParameters {

    fun addShared(builder: CommandBuilder) = builder
        .parameter(primingPackets())
        .parameter(packetShape())
        .parameter(clearance())
        .parameter(phaseDelay())
        .parameter(terminalHold())

    fun distance() = ParameterBuilder
        .begin<Double>("distance")
        .verifiedBy(MACE_CLIP_DISTANCE_VERIFIER)
        .required()
        .build()

    private fun primingPackets() = ParameterBuilder
        .begin<Int>("primingPackets")
        .verifiedBy(ParameterBuilder.intRange(0, MACE_CLIP_MAXIMUM_PRIMING_PACKETS))
        .required()
        .build()

    private fun packetShape() = ParameterBuilder
        .begin<MaceClipResearchPacketShape>("packetShape")
        .verifiedBy(MACE_CLIP_PACKET_SHAPE_VERIFIER)
        .autocompletedFrom { listOf("position", "positionrotation") }
        .required()
        .build()

    private fun clearance() = ParameterBuilder
        .begin<Double>("clearance")
        .verifiedBy(MACE_CLIP_CLEARANCE_VERIFIER)
        .required()
        .build()

    private fun phaseDelay() = ParameterBuilder
        .begin<Int>("phaseDelayTicks")
        .verifiedBy(ParameterBuilder.intRange(0, MACE_CLIP_MAXIMUM_PHASE_DELAY_TICKS))
        .required()
        .build()

    private fun terminalHold() = ParameterBuilder
        .begin<Int>("terminalHoldTicks")
        .verifiedBy(ParameterBuilder.intRange(0, MACE_CLIP_MAXIMUM_TERMINAL_HOLD_TICKS))
        .required()
        .build()
}

private object MaceClipCommandReporter {

    fun reportStart(result: MaceClipResearchProbeStartResult) {
        val key = when (result) {
            MaceClipResearchProbeStartResult.STARTED -> "started"
            MaceClipResearchProbeStartResult.ACTIVE_PROBE -> "activeProbe"
            MaceClipResearchProbeStartResult.ACTIVE_REMOTE_KILL_SESSION -> "activeRemoteKillSession"
            MaceClipResearchProbeStartResult.UNSAFE_CONTEXT -> "unsafeContext"
            MaceClipResearchProbeStartResult.INVALID_CONTEXT -> "invalidContext"
            MaceClipResearchProbeStartResult.NO_TARGET -> "noTarget"
            MaceClipResearchProbeStartResult.ROUTE_REJECTED -> "routeRejected"
            MaceClipResearchProbeStartResult.LOGGING_UNAVAILABLE -> "loggingUnavailable"
        }
        report(key)
    }

    fun reportStatus(status: MaceClipResearchStatus) {
        when (status) {
            MaceClipResearchStatus.Idle -> report("statusIdle")
            is MaceClipResearchStatus.Active -> report(
                "statusActive",
                status.sessionId,
                status.probe.name,
                status.phase?.name ?: "PREPARING",
                status.profileId,
                status.abortRequested,
            )
        }
    }

    fun reportAbort(result: MaceClipResearchAbortResult) = report(
        when (result) {
            MaceClipResearchAbortResult.ABORT_REQUESTED -> "abortRequested"
            MaceClipResearchAbortResult.IDLE -> "abortIdle"
        }
    )

    private fun report(key: String, vararg args: Any) {
        chat(regular(translation("liquidbounce.command.maceclip.result.$key", args = args)))
    }
}

private fun String.toFiniteDoubleInRange(
    minimum: Double,
    maximum: Double,
    lowerExclusive: Boolean,
    finiteErrorKey: String,
    rangeErrorKey: String,
): Result<out Double> {
    val value = toDoubleOrNull()
    val belowMinimum = value != null && if (lowerExclusive) value <= minimum else value < minimum
    return when {
        value == null || !value.isFinite() -> Result.Error(validationMessage(finiteErrorKey, this))
        belowMinimum || value > maximum -> Result.Error(validationMessage(rangeErrorKey, minimum, maximum))
        else -> Result.Ok(value)
    }
}

private fun validationMessage(key: String, vararg args: Any): String {
    val translationKey = "liquidbounce.command.maceclip.validation.$key"
    val language = runCatching(LanguageManager::getLanguage).getOrNull()
        ?: LanguageManager.getCommonLanguage()
    val format = language?.getOrDefault(translationKey, translationKey) ?: translationKey

    return runCatching { String.format(Locale.ROOT, format, *args) }.getOrDefault(format)
}

private fun parseMaceClipPacketShape(sourceText: String): MaceClipResearchPacketShape? =
    when (sourceText.lowercase().filter(Char::isLetterOrDigit)) {
        "pos", "position" -> MaceClipResearchPacketShape.POSITION
        "posrot", "positionrotation" -> MaceClipResearchPacketShape.POSITION_ROTATION
        else -> null
    }
