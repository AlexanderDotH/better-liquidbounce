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

package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerPresentation
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerCommandOperations
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.SeedCrackerRuntime
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.features.chat.notification

/**
 * Collects client-visible seed evidence and delegates cracking to [SeedCrackerRuntime].
 *
 * This module intentionally owns presentation and settings only. Observation, persistence and
 * background solving stay behind the runtime boundary so no Minecraft objects escape to solvers.
 */
object ModuleSeedCracker : ClientModule("SeedCracker", ModuleCategories.WORLD), SeedCrackerCommandOperations {

    internal val structures by boolean("Structures", true).onChanged {
        updateRuntimeSettings()
    }

    internal val netherBedrock by boolean("NetherBedrock", true).onChanged {
        updateRuntimeSettings()
    }

    internal val autoAcceptStrongEvidence by boolean("AutoAcceptStrongEvidence", true).onChanged {
        updateRuntimeSettings()
    }

    internal val persistProgress by boolean("PersistProgress", true).onChanged {
        updateRuntimeSettings()
    }

    internal val chatGuidance by boolean("ChatGuidance", true)
    internal val notifications by boolean("Notifications", true)
    internal val solverThreads by int(
        "SolverThreads",
        defaultSeedCrackerSolverThreads(Runtime.getRuntime().availableProcessors()),
        1..8,
        "threads",
    ).onChanged {
        updateRuntimeSettings()
    }

    override fun onEnabled() {
        SeedCrackerRuntime.onEnabled(
            structuresEnabled = structures,
            netherBedrockEnabled = netherBedrock,
            autoAcceptStrongEvidence = autoAcceptStrongEvidence,
            persistProgress = persistProgress,
            workerLimit = solverThreads,
        )
        publishPendingPresentation()
    }

    override fun onDisabled() {
        SeedCrackerRuntime.onDisabled(persistProgress)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        SeedCrackerRuntime.onWorldChanged()
        publishPendingPresentation()
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        SeedCrackerRuntime.onTick()
        publishPendingPresentation()
    }

    private fun updateRuntimeSettings() {
        if (!running) return

        SeedCrackerRuntime.updateSettings(
            structuresEnabled = structures,
            netherBedrockEnabled = netherBedrock,
            autoAcceptStrongEvidence = autoAcceptStrongEvidence,
            persistProgress = persistProgress,
            workerLimit = solverThreads,
        )
    }

    private fun publishPendingPresentation() {
        val presentation = SeedCrackerRuntime.consumePresentation() ?: return
        publish(presentation)
    }

    private fun publish(presentation: SeedCrackerPresentation) {
        if (notifications) {
            notification(name, presentation.message, presentation.severity)
        }
        if (chatGuidance) {
            chat(presentation.message, this)
        }
    }

}

internal fun defaultSeedCrackerSolverThreads(processors: Int): Int =
    (processors / 2).coerceAtLeast(1).coerceAtMost(4)
