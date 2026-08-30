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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach



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
internal enum class MaceClipReachProfileValidation {
    UNVALIDATED,
    VALIDATED,
}

internal enum class MaceClipReachUse {
    NORMAL,
    EXPERIMENTAL,
    RESEARCH,
}

/** Pinned evidence required before an old ClipReach sequence is exposed as a supported route. */
internal data class MaceClipReachValidationProof(
    val minecraftVersion: String,
    val paperBuildId: String,
    val paperJarSha256: String,
    val javaVersion: String,
    val protocolVersion: Int,
    val pluginSetSha256: String,
    val evidenceSha256: String,
) {
    internal fun isComplete(): Boolean = hasPinnedRuntime() && hasPinnedArtifacts()

    private fun hasPinnedRuntime(): Boolean = minecraftVersion.isNotBlank() &&
        paperBuildId.isNotBlank() &&
        javaVersion.isNotBlank() &&
        protocolVersion > 0

    private fun hasPinnedArtifacts(): Boolean = paperJarSha256.isSha256() &&
        pluginSetSha256.isSha256() &&
        evidenceSha256.isSha256()
}

/** Configurable research inputs; none of these values imply server compatibility. */
internal data class MaceClipReachResearchParameters(
    val primingPacketCount: Int,
    val clearanceHeight: Double,
    val maxTargetDistance: Double,
    val maxMovementPackets: Int,
    val timeoutTicks: Int,
) {
    internal fun areValid(): Boolean = hasValidDistances() && maxMovementPackets > 0 && timeoutTicks > 0

    private fun hasValidDistances(): Boolean = primingPacketCount >= 0 &&
        clearanceHeight.isFinite() && clearanceHeight > 0.0 &&
        maxTargetDistance.isFinite() && maxTargetDistance > 0.0
}

internal data class MaceClipReachProfile(
    val id: String,
    val validation: MaceClipReachProfileValidation,
    val parameters: MaceClipReachResearchParameters,
    val proof: MaceClipReachValidationProof? = null,
) {
    fun permits(use: MaceClipReachUse): Boolean = when (use) {
        MaceClipReachUse.NORMAL ->
            validation == MaceClipReachProfileValidation.VALIDATED && proof?.isComplete() == true
        MaceClipReachUse.EXPERIMENTAL,
        MaceClipReachUse.RESEARCH,
        -> true
    }

    internal fun hasValidDefinition(): Boolean = id.isNotBlank() && parameters.areValid()

    companion object {
        fun experimental(parameters: MaceClipReachResearchParameters) = MaceClipReachProfile(
            id = EXPERIMENTAL_PROFILE_ID,
            validation = MaceClipReachProfileValidation.UNVALIDATED,
            parameters = parameters,
        )

        /** Historical priming/clearance inputs with local safety bounds, deliberately not promoted. */
        val REFERENCE_UNVALIDATED = MaceClipReachProfile(
            id = "liveoverflow-reference-unvalidated",
            validation = MaceClipReachProfileValidation.UNVALIDATED,
            parameters = MaceClipReachResearchParameters(
                primingPacketCount = REFERENCE_PRIMING_PACKET_COUNT,
                clearanceHeight = REFERENCE_CLEARANCE_HEIGHT,
                maxTargetDistance = REFERENCE_MAX_TARGET_DISTANCE,
                maxMovementPackets = REFERENCE_MAX_MOVEMENT_PACKETS,
                timeoutTicks = REFERENCE_TIMEOUT_TICKS,
            ),
        )
    }
}

private fun String.isSha256(): Boolean = length == SHA256_LENGTH && all { it in SHA256_CHARACTERS }

private const val REFERENCE_PRIMING_PACKET_COUNT = 9
private const val REFERENCE_CLEARANCE_HEIGHT = 99.0
private const val REFERENCE_MAX_TARGET_DISTANCE = 500.0
private const val REFERENCE_MAX_MOVEMENT_PACKETS = 128
private const val REFERENCE_TIMEOUT_TICKS = 40
private const val EXPERIMENTAL_PROFILE_ID = "macekill-instant-experimental-unvalidated"
private const val SHA256_LENGTH = 64
private const val SHA256_CHARACTERS = "0123456789abcdefABCDEF"
