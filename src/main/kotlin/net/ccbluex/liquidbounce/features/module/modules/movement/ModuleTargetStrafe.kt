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
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAimbot
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.config.TargetStrafePlannerSettings
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.config.TargetStrafeRequirement
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafeEnvironment
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafePlannerDispatcher
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafeRenderState
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.contract.TargetStrafeRuntime
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.cubecraft.CubeCraftTargetStrafeMode
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.planner.TargetStrafePlanner
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.render.TargetStrafeVisuals
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.runtime.TargetStrafeFixedSpeedMode
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.runtime.TargetStrafeInputMode
import net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.runtime.TargetStrafeMotionMode
import net.ccbluex.liquidbounce.features.combat.runtime.TargetSelector
import net.minecraft.world.entity.LivingEntity

/** Handles strafing around a locked target. */
object ModuleTargetStrafe : ClientModule("TargetStrafe", ModuleCategories.MOVEMENT) {

    internal val renderState = TargetStrafeRenderState()
    internal val modes = choices(
        "Mode",
        TargetStrafeMotionMode,
        arrayOf(
            TargetStrafeMotionMode,
            TargetStrafeFixedSpeedMode,
            TargetStrafeInputMode,
            CubeCraftTargetStrafeMode,
        ),
    ).apply { tagBy(this) }

    private val range = float("Range", 2.95f, 0.0f..8.0f)
    private val targetSelector = TargetSelector(range = range)
    private val followRangeValue = float("FollowRange", 4f, 0.0f..10.0f).onChange {
        it.coerceAtLeast(targetSelector.maxRange)
    }
    private val requirements by multiEnumChoice<TargetStrafeRequirement>("Requirements")

    internal val followRange get() = followRangeValue.get()
    internal val orbitRange get() = targetSelector.maxRange
    internal val requirementsMet get() = requirements.all { it.meets() }

    init {
        TargetStrafeEnvironment.bindKillAuraRunning { ModuleKillAura.running }
        TargetStrafePlannerDispatcher.bind(TargetStrafePlanner)
        TargetStrafeRuntime.bind(
            renderState = renderState,
            followRangeProvider = { followRange },
            orbitRangeProvider = { orbitRange },
            requirementsMetProvider = { requirementsMet },
            firstTargetProvider = ::firstTarget,
        )
        range.onChanged { updatedRange ->
            if (followRange < updatedRange) followRangeValue.set(updatedRange)
        }
        tree(TargetStrafePlannerSettings)
        tree(TargetStrafeVisuals)
    }

    internal fun firstTarget(): LivingEntity? = ModuleKillAura.targetTracker.target
        ?: ModuleAimbot.targetTracker.target
        ?: targetSelector.targets().firstOrNull()
}
