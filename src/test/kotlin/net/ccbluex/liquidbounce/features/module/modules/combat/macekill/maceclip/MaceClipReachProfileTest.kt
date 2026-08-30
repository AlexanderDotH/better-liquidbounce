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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip
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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceClipReachProfileTest {

    @Test
    fun `reference constants stay explicit-only until a server profile is proven`() {
        val profile = MaceClipReachProfile.REFERENCE_UNVALIDATED

        assertTrue(profile.permits(MaceClipReachUse.EXPERIMENTAL))
        assertTrue(profile.permits(MaceClipReachUse.RESEARCH))
        assertFalse(profile.permits(MaceClipReachUse.NORMAL))
        assertTrue(profile.validation == MaceClipReachProfileValidation.UNVALIDATED)
        assertTrue(profile.proof == null)
    }

    @Test
    fun `dynamic experimental profile preserves unvalidated diagnostics and configured limits`() {
        val parameters = RESEARCH_PARAMETERS.copy(
            primingPacketCount = 18,
            clearanceHeight = 50.0,
            maxMovementPackets = 256,
        )

        val profile = MaceClipReachProfile.experimental(parameters)

        assertTrue(profile.id.contains("experimental"))
        assertTrue(profile.validation == MaceClipReachProfileValidation.UNVALIDATED)
        assertTrue(profile.proof == null)
        assertTrue(profile.parameters == parameters)
        assertTrue(profile.permits(MaceClipReachUse.EXPERIMENTAL))
        assertTrue(profile.permits(MaceClipReachUse.RESEARCH))
        assertFalse(profile.permits(MaceClipReachUse.NORMAL))
    }

    @Test
    fun `validated label without complete proof cannot enable normal runtime`() {
        val profile = MaceClipReachProfile(
            id = "incomplete-paper-profile",
            validation = MaceClipReachProfileValidation.VALIDATED,
            parameters = RESEARCH_PARAMETERS,
            proof = MaceClipReachValidationProof(
                minecraftVersion = "26.2",
                paperBuildId = "",
                paperJarSha256 = SHA256,
                javaVersion = "25",
                protocolVersion = 107,
                pluginSetSha256 = SHA256,
                evidenceSha256 = SHA256,
            ),
        )

        assertFalse(profile.permits(MaceClipReachUse.NORMAL))
        assertTrue(profile.permits(MaceClipReachUse.EXPERIMENTAL))
        assertTrue(profile.permits(MaceClipReachUse.RESEARCH))
    }

    @Test
    fun `complete pinned proof enables the validated profile for normal runtime`() {
        val profile = validatedProfile()

        assertTrue(profile.permits(MaceClipReachUse.NORMAL))
        assertTrue(profile.permits(MaceClipReachUse.EXPERIMENTAL))
        assertTrue(profile.permits(MaceClipReachUse.RESEARCH))
    }

    companion object {
        internal val RESEARCH_PARAMETERS = MaceClipReachResearchParameters(
            primingPacketCount = 9,
            clearanceHeight = 99.0,
            maxTargetDistance = 500.0,
            maxMovementPackets = 128,
            timeoutTicks = 40,
        )

        internal const val SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        internal fun validatedProfile(
            parameters: MaceClipReachResearchParameters = RESEARCH_PARAMETERS,
        ) = MaceClipReachProfile(
            id = "paper-26_2-controlled-lab",
            validation = MaceClipReachProfileValidation.VALIDATED,
            parameters = parameters,
            proof = MaceClipReachValidationProof(
                minecraftVersion = "26.2",
                paperBuildId = "paper-build-1",
                paperJarSha256 = SHA256,
                javaVersion = "25",
                protocolVersion = 107,
                pluginSetSha256 = SHA256,
                evidenceSha256 = SHA256,
            ),
        )
    }
}
