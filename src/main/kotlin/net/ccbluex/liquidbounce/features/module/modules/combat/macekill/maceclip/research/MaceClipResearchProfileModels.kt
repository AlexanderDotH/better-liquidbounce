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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research




internal data class MaceClipResearchPlugin(
    val name: String,
    val version: String,
    val sha256: String,
)

internal data class MaceClipResearchProfile(
    val id: String,
    val validation: MaceClipResearchValidation,
    val minecraftVersion: String,
    val protocolVersion: Int,
    val paperBuildId: Int,
    val paperDownloadUrl: String,
    val paperSha256: String,
    val javaVersion: Int,
    val plugins: List<MaceClipResearchPlugin>,
)

internal object MaceClipResearchProfiles {
    val PAPER_26_2_BUILD_112 = MaceClipResearchProfile(
        id = "paper-26.2-build-112-unvalidated",
        validation = MaceClipResearchValidation.UNVALIDATED,
        minecraftVersion = "26.2",
        protocolVersion = 776,
        paperBuildId = 112,
        paperDownloadUrl = "https://fill-data.papermc.io/v1/objects/" +
            "bd3a58cf96874e5ea6643f5f6fe9b4f5bf9e34b795fa078c2f0ee8b98b2f907e/paper-26.2-112.jar",
        paperSha256 = "bd3a58cf96874e5ea6643f5f6fe9b4f5bf9e34b795fa078c2f0ee8b98b2f907e",
        javaVersion = 25,
        plugins = listOf(
            MaceClipResearchPlugin(
                name = "MaceKillLabObserver",
                version = "0.1.0",
                sha256 = "b84faf38c6db14618a71bc31409be3e36e52832bb92aed472e8bca517a25076c",
            )
        ),
    )
}
