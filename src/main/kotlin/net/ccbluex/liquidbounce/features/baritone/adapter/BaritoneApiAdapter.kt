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
package net.ccbluex.liquidbounce.features.baritone.adapter

import baritone.api.BaritoneAPI
import baritone.api.event.events.PathEvent
import baritone.api.event.events.TickEvent
import baritone.api.event.listener.AbstractGameEventListener
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationPhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseCause
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseController
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightRuntimeConfig
import net.minecraft.client.Minecraft
import java.nio.file.Path

fun interface BaritoneConflictDetector {
    fun detect(): Collection<BaritonePauseCause>
}

/** Official Baritone 26.2 infrastructure adapter. No caller outside this package needs the third-party API. */
class BaritoneApiAdapter private constructor(
    private val context: BaritoneAdapterContext,
) : BaritoneFacade by context.facade, AbstractGameEventListener {

    init {
        context.baritone.pathingControlManager.registerProcess(context.pauseProcess)
        context.baritone.pathingControlManager.registerProcess(context.flightPauseProcess)
        context.baritone.gameEventHandler.registerEventListener(this)
    }

    override fun onTick(event: TickEvent) = context.locked { context.onAdapterTick(event) }

    override fun onPathEvent(event: PathEvent) = context.locked { context.onAdapterPathEvent(event) }

    companion object {
        @JvmStatic
        @JvmOverloads
        @Suppress("LongParameterList")
        fun create(
            conflictDetector: BaritoneConflictDetector = BaritoneConflictDetector { emptyList() },
            resumeDelayTicks: () -> Int = { BaritonePauseController.DEFAULT_RESUME_DELAY_TICKS },
            navigationMode: () -> BaritoneNavigationMode = { BaritoneNavigationMode.FLY },
            flightRuntimeConfig: () -> BaritoneFlightRuntimeConfig = { BaritoneFlightRuntimeConfig() },
            onAutomationStart: () -> Unit = {},
            messageSink: BaritoneMessageSink = BaritoneMessageSink.NONE,
            gameDirectory: Path = Minecraft.getInstance().gameDirectory.toPath(),
            currentTimeMillis: () -> Long = System::currentTimeMillis,
        ): BaritoneFacade {
            val result = runCatching {
                Class.forName("baritone.api.BaritoneAPI", false, BaritoneApiAdapter::class.java.classLoader)
                BaritoneAPI.getProvider().primaryBaritone
            }
            return result.fold(
                onSuccess = {
                    BaritoneApiAdapter(
                        BaritoneAdapterContext(
                            it,
                            conflictDetector,
                            resumeDelayTicks,
                            navigationMode,
                            flightRuntimeConfig,
                            onAutomationStart,
                            gameDirectory,
                            messageSink,
                            currentTimeMillis,
                        ),
                    )
                },
                onFailure = {
                    UnavailableBaritoneFacade("Baritone API failed to initialize: ${it.message.orEmpty()}")
                },
            )
        }
    }
}

internal fun navigationBaritonePhase(phase: BaritoneNavigationPhase): BaritonePhase? = when (phase) {
    BaritoneNavigationPhase.WAITING_FOR_PATH,
    BaritoneNavigationPhase.PLANNING,
    BaritoneNavigationPhase.ARMING -> BaritonePhase.CALCULATING
    BaritoneNavigationPhase.FLYING -> BaritonePhase.PATHING
    BaritoneNavigationPhase.WAITING_FOR_USER -> BaritonePhase.PAUSED
    BaritoneNavigationPhase.IDLE,
    BaritoneNavigationPhase.WALK_FALLBACK -> null
}

internal fun preservesResultAfterCancellation(phase: BaritonePhase): Boolean =
    phase == BaritonePhase.FAILED || phase == BaritonePhase.ARRIVED
