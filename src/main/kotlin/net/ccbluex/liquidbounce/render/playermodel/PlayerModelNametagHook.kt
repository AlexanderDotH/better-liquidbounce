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

package net.ccbluex.liquidbounce.render.playermodel

import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.render.entity
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityAttachment
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

object PlayerModelNametagHook {

    private const val MIN_VISUAL_OFFSET_SQ = 1.0E-4

    @JvmStatic
    fun shouldSuppressVanillaNameDisplay(state: EntityRenderState): Boolean {
        if (!PlayerModelNametagStateBridge.isRunning()) {
            return false
        }

        val entity = state.entity as? LivingEntity ?: return false
        if (!AmnesiaPlayerModelBridge.isTarget(entity)) {
            return false
        }

        return hasVisualPositionOffset(entity)
    }

    @JvmStatic
    fun getNametagAnchorPosition(entity: Entity, partialTicks: Float): Vec3 {
        if (entity is LivingEntity && AmnesiaPlayerModelBridge.isTarget(entity)) {
            AmnesiaPlayerModelBridge.visualTransform(entity)?.let { transform ->
                transform.position?.let { visualPos ->
                    return visualPos.add(getNametagAttachment(entity, transform.bodyYaw))
                }
            }
        }

        return entity.interpolatePlayerModelPosition(partialTicks)
            .add(getNametagAttachment(entity, entity.getYRot(partialTicks)))
    }

    private fun hasVisualPositionOffset(entity: LivingEntity): Boolean {
        val visualPos = AmnesiaPlayerModelBridge.visualTransform(entity)?.position ?: return false
        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        return entity.interpolatePlayerModelPosition(partialTicks).distanceToSqr(visualPos) > MIN_VISUAL_OFFSET_SQ
    }

    private fun getNametagAttachment(entity: Entity, yaw: Float): Vec3 =
        entity.attachments[EntityAttachment.NAME_TAG, 0, yaw]
}
