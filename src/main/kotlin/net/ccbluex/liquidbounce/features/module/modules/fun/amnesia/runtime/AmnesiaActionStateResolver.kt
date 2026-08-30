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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.runtime

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaRuntimeBridge
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelActionState
import net.minecraft.world.entity.LivingEntity

internal object AmnesiaActionStateResolver {

    fun resolve(entity: LivingEntity): PlayerModelActionState? {
        val contributions = AmnesiaRuntimeBridge.actionContributions(entity)
        val criticals = contributions.criticals
        val jesus = contributions.jesus
        val scaffold = contributions.scaffold
        val bhop = contributions.bhop
        val crouching = contributions.fakeSneak || criticals?.crouching == true || scaffold?.crouching == true
        val groundPose = bhop?.groundPose == true ||
            jesus?.groundPose == true ||
            criticals?.groundPose == true ||
            scaffold?.groundPose == true
        val swingProgress = criticals?.swingProgress ?: scaffold?.swingProgress
        val armPose = criticals?.armPose ?: scaffold?.armPose

        if (!crouching && !groundPose && swingProgress == null && armPose == null) {
            return null
        }

        return PlayerModelActionState(
            crouching = crouching,
            groundPose = groundPose,
            swingProgress = swingProgress,
            armPose = armPose,
        )
    }
}
