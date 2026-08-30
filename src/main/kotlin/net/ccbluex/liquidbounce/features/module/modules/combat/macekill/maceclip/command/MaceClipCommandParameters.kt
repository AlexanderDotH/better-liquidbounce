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
import net.ccbluex.liquidbounce.features.command.Parameter.Verificator.Result
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MACE_CLIP_MAXIMUM_PHASE_DELAY_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MACE_CLIP_MAXIMUM_PRIMING_PACKETS
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MACE_CLIP_MAXIMUM_TERMINAL_HOLD_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchAbortResult
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchPacketShape
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchProbeStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.MaceClipResearchStatus
import net.ccbluex.liquidbounce.features.language.LanguageManager
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.regular
import java.util.Locale

internal object MaceClipCommandParameters {

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

internal object MaceClipCommandReporter {

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

internal fun String.toFiniteDoubleInRange(
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

internal fun validationMessage(key: String, vararg args: Any): String {
    val translationKey = "liquidbounce.command.maceclip.validation.$key"
    val language = runCatching(LanguageManager::getLanguage).getOrNull()
        ?: LanguageManager.getCommonLanguage()
    val format = language?.getOrDefault(translationKey, translationKey) ?: translationKey

    return runCatching { String.format(Locale.ROOT, format, *args) }.getOrDefault(format)
}

internal fun parseMaceClipPacketShape(sourceText: String): MaceClipResearchPacketShape? =
    when (sourceText.lowercase().filter(Char::isLetterOrDigit)) {
        "pos", "position" -> MaceClipResearchPacketShape.POSITION
        "posrot", "positionrotation" -> MaceClipResearchPacketShape.POSITION_ROTATION
        else -> null
    }
