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

package net.ccbluex.liquidbounce.utils.render

import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.features.module.modules.render.nametags.ModuleNametags
import net.ccbluex.liquidbounce.utils.render.setPlayerRotation
import net.ccbluex.liquidbounce.utils.render.setPosition
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.world.entity.LivingEntity

object PlayerModelDelayHook {

    @JvmStatic
    fun applyDelayedTransform(entity: LivingEntity, state: LivingEntityRenderState) {
        if (!ModuleAmnesia.isAmnesiaTarget(entity)) {
            return
        }

        ModuleAmnesia.getVisualTransform(entity)?.let { transform ->
            transform.position?.let { visualPos ->
                state.setPosition(visualPos)
                if (ModuleNametags.running && PlayerModelNametagHook.shouldSuppressVanillaNameDisplay(state)) {
                    state.nameTag = null
                    state.scoreText = null
                }
            }
            state.setPlayerRotation(transform.bodyYaw, transform.headYaw, transform.pitch)
            state.nameTagAttachment = entity.attachments[EntityAttachment.NAME_TAG, 0, transform.bodyYaw]
            if (transform.freezeWalkAnimation) {
                state.walkAnimationPos = 0f
                state.walkAnimationSpeed = 0f
                if (state is HumanoidRenderState) {
                    state.speedValue = 0f
                }
            }
        }
    }

    @JvmStatic
    fun applyFakeSneak(entity: LivingEntity, state: HumanoidRenderState) {
        if (ModuleAmnesia.shouldFakeSneak(entity)) {
            state.isCrouching = true
        }
    }
}
