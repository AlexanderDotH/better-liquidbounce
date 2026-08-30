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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.megacraft

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.flyAutomationSneak
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.flyAutomationMoving
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.revive.requestReviveFlyTimer
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.revive.setReviveFlySpeed
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.revive.stopReviveFlySpeed
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Revive Megacraft fly port.
 */
internal object FlyMegacraft : Mode("Megacraft"), FlyAutomationProfile {


    private var lastBoost = TimeSource.Monotonic.markNow()

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = true,
        landing = true,
        kind = FlyAutomationKind.CONTINUOUS,
    )

    override fun automationReadiness(): FlyAutomationReadiness = FlyAutomationReadiness.Ready

    override fun enable() {
        lastBoost = TimeSource.Monotonic.markNow()
        super.enable()
    }

    override fun disable() {
        player.stopReviveFlySpeed()
        super.disable()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.fallDistance >= 0f) {
            requestReviveFlyTimer(0.3f)

            if (lastBoost.elapsedNow() >= boostDelay) {
                player.deltaMovement.y = 0.5
                lastBoost = TimeSource.Monotonic.markNow()
            }

            if (player.flyAutomationMoving()) {
                player.setReviveFlySpeed(2.0)
            }
        }

        if (flyAutomationSneak(mc.options.keyShift.isDown)) {
            player.setPos(player.x, player.y - 1.0, player.z)
        }
    }

    private val boostDelay = 380.milliseconds

}
