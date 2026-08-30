/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
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

package net.ccbluex.liquidbounce.features.module.modules.render.hats.runtime

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFreeLook
import net.ccbluex.liquidbounce.features.module.modules.render.hats.config.HatsHeightOffset
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentRotation
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import org.joml.Quaternionf

private val ROTATION = Quaternionf()

abstract class HatsMode(name: String, final override val parent: ModeValueGroup<*>) : Mode(name) {

    // --- Settings ---
    private val followRotation by boolean("FollowRotation", false)

    private class EquipOffset : ValueGroup("EquipmentOffset") {
        val equipmentOffset by float("ArmorOffset", 0.1f, 0f..1f)
    }

    private val equipOffset = tree(EquipOffset())

    private val hurtMarked by boolean("ShowDamage", true)

    private class FriendsOptions : ValueGroup("FriendsOptions") {
        val friendView by boolean("ViewOnFriend", true)
        val distance by int("Distance", 64, 8..512, "blocks")
    }

    private val friendsOptions = tree(FriendsOptions())

    protected val showInFirstPerson by boolean("FirstPersonView", true)

    // --- Render ---
    protected abstract fun WorldRenderEnvironment.drawHat(isHurt: Boolean)

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val player = mc.player ?: return@handler
        event.renderEnvironment {
            for (entity in world.players()) {
                drawConfiguredHat(entity, player, event.partialTicks)
            }
        }
    }

    private fun WorldRenderEnvironment.drawConfiguredHat(entity: Player, localPlayer: Player, partialTicks: Float) {
        if (!shouldRenderHat(entity, localPlayer)) {
            return
        }
        val isHurt = entity.hurtTime > 0 && hurtMarked
        val position = entity.interpolateCurrentPosition(partialTicks)
        val rotation = entity.interpolateCurrentRotation(partialTicks)
        val height = HatsHeightOffset.current()
        val armorOffset = if (!entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty) {
            equipOffset.equipmentOffset
        } else {
            0.0F
        }
        withPositionRelativeToCamera(position.add(0.0, entity.eyeHeight.toDouble(), 0.0)) {
            poseStack.withPush {
                if (followRotation) mulPose(rotation.toQuaternion(ROTATION))
                translate(0F, entity.bbHeight - entity.eyeHeight + height + armorOffset, 0F)
                drawHat(isHurt)
            }
        }
    }

    private fun shouldRenderHat(entity: Player, localPlayer: Player): Boolean {
        if (entity == localPlayer) {
            return !mc.options.cameraType.isFirstPerson || showInFirstPerson || ModuleFreeLook.enabled
        }
        return localPlayer.distanceTo(entity) <= friendsOptions.distance &&
            FriendManager.isFriend(entity) &&
            friendsOptions.friendView
    }

    protected inline fun WorldRenderEnvironment.withHatRotation(
        angle: Float,
        block: WorldRenderEnvironment.() -> Unit,
    ) {
        poseStack.withPush {
            if (!Mth.equal(angle, 0f)) mulPose(Quaternionf().rotationY(angle))
            block()
        }
    }

    protected fun getRotationAngle(speed: Float): Float {
        return if (Mth.equal(speed, 0f)) {
            0f
        } else {
            (System.currentTimeMillis() % 360000) * 0.001F * speed
        }
    }

}
