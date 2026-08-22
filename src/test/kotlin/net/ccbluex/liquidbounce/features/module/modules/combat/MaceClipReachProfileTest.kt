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

package net.ccbluex.liquidbounce.features.module.modules.combat

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
