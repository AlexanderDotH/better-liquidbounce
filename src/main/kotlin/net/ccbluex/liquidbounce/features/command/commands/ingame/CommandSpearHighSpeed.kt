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
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillHighSpeedResearchFinalPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillHighSpeedResearchPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillHighSpeedResearchProbeRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.SpearKillHighSpeedResearchProbeStartResult
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.regular

private const val MAXIMUM_RESEARCH_DISTANCE = 200.0
private const val MAXIMUM_RESEARCH_PRIMING_PACKETS = 18

private val DISTANCE_VERIFIER = Parameter.Verificator<Double> { sourceText ->
    val distance = sourceText.toDoubleOrNull()
    when {
        distance == null || !distance.isFinite() -> Result.Error("'$sourceText' is not a finite distance")
        distance <= 0.0 || distance > MAXIMUM_RESEARCH_DISTANCE -> Result.Error(
            "The distance must be greater than 0 and at most $MAXIMUM_RESEARCH_DISTANCE"
        )
        else -> Result.Ok(distance)
    }
}

private val PRIMING_PACKET_TYPE_VERIFIER = Parameter.Verificator<SpearKillHighSpeedResearchPacketType> { sourceText ->
    parsePrimingPacketType(sourceText)?.let { Result.Ok(it) }
        ?: Result.Error("Priming packet type must be position, positionrotation, rotation, or statusonly")
}

private val FINAL_PACKET_TYPE_VERIFIER =
    Parameter.Verificator<SpearKillHighSpeedResearchFinalPacketType> { sourceText ->
        parseFinalPacketType(sourceText)?.let { Result.Ok(it) }
            ?: Result.Error("Final packet type must be pos or posrot")
    }

/** Runs one explicitly requested high-speed movement probe without an automatic sweep. */
object CommandSpearHighSpeed : Command.Factory {

    override fun createCommand(): Command = createCommand(
        startProbe = ModuleSpearKill::startHighSpeedResearchProbe,
        reportResult = ::reportResult,
    )

    internal fun createCommand(
        startProbe: (SpearKillHighSpeedResearchProbeRequest) -> SpearKillHighSpeedResearchProbeStartResult,
        reportResult: (SpearKillHighSpeedResearchProbeStartResult) -> Unit,
    ): Command = CommandBuilder
        .begin("spearhighspeed")
        .hub()
        .subcommand(moveCommand(startProbe, reportResult))
        .subcommand(attackCommand(startProbe, reportResult))
        .build()

    private fun moveCommand(
        startProbe: (SpearKillHighSpeedResearchProbeRequest) -> SpearKillHighSpeedResearchProbeStartResult,
        reportResult: (SpearKillHighSpeedResearchProbeStartResult) -> Unit,
    ) = CommandBuilder
        .begin("move")
        .parameter(distanceParameter())
        .parameter(primingPacketsParameter())
        .parameter(primingPacketTypeParameter())
        .parameter(finalPacketTypeParameter())
        .requiresIngame()
        .handler {
            val request = SpearKillHighSpeedResearchProbeRequest.Move(
                distance = args[0] as Double,
                primingPackets = args[1] as Int,
                primingPacketType = args[2] as SpearKillHighSpeedResearchPacketType,
                finalPacketType = args.getOrNull(3) as? SpearKillHighSpeedResearchFinalPacketType
                    ?: SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION,
            )
            reportResult(startProbe(request))
        }
        .build()

    private fun attackCommand(
        startProbe: (SpearKillHighSpeedResearchProbeRequest) -> SpearKillHighSpeedResearchProbeStartResult,
        reportResult: (SpearKillHighSpeedResearchProbeStartResult) -> Unit,
    ) = CommandBuilder
        .begin("attack")
        .parameter(primingPacketsParameter())
        .parameter(primingPacketTypeParameter())
        .parameter(finalPacketTypeParameter())
        .requiresIngame()
        .handler {
            val request = SpearKillHighSpeedResearchProbeRequest.Attack(
                primingPackets = args[0] as Int,
                primingPacketType = args[1] as SpearKillHighSpeedResearchPacketType,
                finalPacketType = args.getOrNull(2) as? SpearKillHighSpeedResearchFinalPacketType
                    ?: SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION,
            )
            reportResult(startProbe(request))
        }
        .build()

    private fun distanceParameter() = ParameterBuilder
        .begin<Double>("distance")
        .verifiedBy(DISTANCE_VERIFIER)
        .required()
        .build()

    private fun primingPacketsParameter() = ParameterBuilder
        .begin<Int>("primingPackets")
        .verifiedBy(ParameterBuilder.intRange(0, MAXIMUM_RESEARCH_PRIMING_PACKETS))
        .required()
        .build()

    private fun primingPacketTypeParameter() = ParameterBuilder
        .begin<SpearKillHighSpeedResearchPacketType>("primingType")
        .verifiedBy(PRIMING_PACKET_TYPE_VERIFIER)
        .autocompletedFrom { listOf("position", "positionrotation", "rotation", "statusonly") }
        .required()
        .build()

    private fun finalPacketTypeParameter() = ParameterBuilder
        .begin<SpearKillHighSpeedResearchFinalPacketType>("finalType")
        .verifiedBy(FINAL_PACKET_TYPE_VERIFIER)
        .autocompletedFrom { listOf("pos", "posrot") }
        .optional(SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION)
        .build()

    private fun reportResult(result: SpearKillHighSpeedResearchProbeStartResult) {
        val message = when (result) {
            SpearKillHighSpeedResearchProbeStartResult.STARTED -> "Probe started."
            SpearKillHighSpeedResearchProbeStartResult.ACTIVE_SESSION -> "Refused: a SpearKill session is active."
            SpearKillHighSpeedResearchProbeStartResult.INVALID_CONTEXT -> "Refused: the current context is invalid."
            SpearKillHighSpeedResearchProbeStartResult.NO_TARGET -> "Refused: no valid target is available."
            SpearKillHighSpeedResearchProbeStartResult.ROUTE_REJECTED -> "Refused: the probe route is invalid."
        }
        chat(regular("[SpearHighSpeed] $message"))
    }
}

private fun parsePrimingPacketType(sourceText: String): SpearKillHighSpeedResearchPacketType? =
    when (sourceText.normalizedPacketType()) {
        "pos", "position" -> SpearKillHighSpeedResearchPacketType.POSITION
        "posrot", "positionrotation" -> SpearKillHighSpeedResearchPacketType.POSITION_ROTATION
        "rot", "rotation" -> SpearKillHighSpeedResearchPacketType.ROTATION
        "status", "statusonly" -> SpearKillHighSpeedResearchPacketType.STATUS_ONLY
        else -> null
    }

private fun parseFinalPacketType(sourceText: String): SpearKillHighSpeedResearchFinalPacketType? =
    when (sourceText.normalizedPacketType()) {
        "pos", "position" -> SpearKillHighSpeedResearchFinalPacketType.POSITION
        "posrot", "positionrotation" -> SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION
        else -> null
    }

private fun String.normalizedPacketType() = lowercase().replace("_", "").replace("-", "")
