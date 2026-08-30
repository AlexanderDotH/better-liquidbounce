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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.fireball

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationEnd
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.fireball.runtime.FlyFireballRuntime
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.fireball.techniques.FlyFireballCustomTechnique
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.fireball.techniques.FlyFireballLegitTechnique
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.fireball.trigger.FlyFireballInstantTrigger
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.fireball.trigger.FlyFireballOnEdgeTrigger

internal object FlyFireball : Mode("Fireball"), FlyAutomationProfile {


    val technique = modes("Technique", FlyFireballLegitTechnique,
        arrayOf(FlyFireballLegitTechnique, FlyFireballCustomTechnique))

    val trigger = modes("Trigger", FlyFireballInstantTrigger,
        arrayOf(FlyFireballInstantTrigger, FlyFireballOnEdgeTrigger))

    // Silent fireball selection
    val slotResetDelay by intRange("SlotResetDelay", 4..6, 0..40, "ticks")

    init {
        FlyFireballRuntime.bind(this) { slotResetDelay }
    }

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = false,
        landing = false,
        kind = FlyAutomationKind.BURST,
        resource = "Fire Charge",
    )

    override fun automationReadiness(): FlyAutomationReadiness = when {
        !FlyFireballRuntime.hasFireball() -> FlyAutomationReadiness.Unavailable("No fire charge is available")
        FlyFireballRuntime.wasTriggered -> FlyAutomationReadiness.Ready
        else -> FlyAutomationReadiness.Arming("Waiting for the configured fireball trigger")
    }

    override fun consumeAutomaticEnd(): FlyAutomationEnd? = FlyFireballRuntime.consumeAutomaticEnd()

    override fun enable() {
        FlyFireballRuntime.reset()
        super.enable()
    }

}
