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

package net.ccbluex.liquidbounce.features.module.modules.render.wings.runtime

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFreeLook
import net.ccbluex.liquidbounce.features.module.modules.render.ModulePlayerModel
import net.ccbluex.liquidbounce.features.module.modules.render.wings.config.WingsPosition
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.render.withPush
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth.rotLerp
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.phys.Vec3

internal fun shouldUseModelWingsRotation(
    isLocalPlayer: Boolean,
    playerModelRunning: Boolean,
    displayMode: ModulePlayerModel.Display,
    rotationStateEnabled: Boolean,
    bodyPartAllowed: Boolean,
) = isLocalPlayer && playerModelRunning && displayMode == ModulePlayerModel.Display.REPLACE &&
    rotationStateEnabled && bodyPartAllowed

internal fun resolveWingsBodyRotation(
    vanillaBodyRotation: Float,
    modelBodyRotation: Float?,
    useModelRotation: Boolean,
) = if (useModelRotation) modelBodyRotation ?: vanillaBodyRotation else vanillaBodyRotation

internal fun shouldRenderWingsForEntity(
    isLocalPlayer: Boolean,
    cameraFirstPerson: Boolean,
    showInFirstPerson: Boolean,
    freeLookEnabled: Boolean,
    eligibleFriend: Boolean,
) = if (isLocalPlayer) {
    !cameraFirstPerson || showInFirstPerson || freeLookEnabled
} else {
    eligibleFriend
}

abstract class WingsMode(name: String, final override val parent: ModeValueGroup<*>) : Mode(name) {

    val showDamage by boolean("ShowDamage", true)
    val showInFirstPerson by boolean("ShowInFirstPerson", false)

    private class FriendsOptions : ValueGroup("FriendsOptions") {
        val friendView by boolean("ViewOnFriend", true)
        val distance by int("Distance", 64, 8..512, "blocks")
    }

    protected abstract fun WorldRenderEnvironment.drawWings(isHurt: Boolean, bodyRot: Float, shifting: Float)

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val player = mc.player ?: return@handler

        event.renderEnvironment {
            for (entity in world.players()) {
                drawEntityWings(event, player, entity)
            }
        }
    }

    private fun WorldRenderEnvironment.drawEntityWings(
        event: WorldRenderEvent,
        player: LocalPlayer,
        entity: AbstractClientPlayer,
    ) {
        val isMe = entity == player
        val isFriend = FriendsOptions().friendView && FriendManager.isFriend(entity) &&
            player.distanceTo(entity) <= FriendsOptions().distance
        if (!shouldRenderWingsForEntity(
                isMe,
                mc.options.cameraType.isFirstPerson,
                showInFirstPerson,
                ModuleFreeLook.enabled,
                isFriend,
            )) return

        val bodyRot = resolveBodyRotation(event, entity, isMe)
        val look = Vec3.directionFromRotation(0f, bodyRot)
        val equipmentOffset = if (entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty) {
            0.0
        } else {
            WingsPosition.equipmentOffset.toDouble()
        }
        val shifting = if (player.isShiftKeyDown) 0.1f else 0f
        val behind = WingsPosition.behindScale.toDouble() + equipmentOffset
        val pos = entity.getPosition(event.partialTicks).subtract(look.scale(behind))

        withPositionRelativeToCamera(
            pos.add(0.0, (entity.bbHeight / 2.0) + WingsPosition.wingsHeight - shifting, 0.0)
        ) {
            poseStack.withPush {
                drawWings(entity.hurtTime != 0 && showDamage, bodyRot, shifting)
            }
        }
    }

    private fun resolveBodyRotation(
        event: WorldRenderEvent,
        entity: AbstractClientPlayer,
        isLocalPlayer: Boolean,
    ): Float {
        val vanillaRotation = rotLerp(event.partialTicks, entity.yBodyRotO, entity.yBodyRot)
        val useModelRotation = shouldUseModelWingsRotation(
            isLocalPlayer,
            ModulePlayerModel.running,
            ModulePlayerModel.displayMode,
            ModulePlayerModel.isStateEnabled(ModulePlayerModel.State.ROTATION),
            ModulePlayerModel.isPartAllowed(ModulePlayerModel.BodyPart.BODY),
        )
        return resolveWingsBodyRotation(
            vanillaRotation,
            ModulePlayerModel.interpolatedModelRotation(event.partialTicks)?.yaw,
            useModelRotation,
        )
    }
}
