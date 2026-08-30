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
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.features.module.modules.misc.middleclick.MiddleClickAmnesiaTargetMode
import net.ccbluex.liquidbounce.features.module.modules.misc.middleclick.MiddleClickActionRuntime
import net.ccbluex.liquidbounce.features.module.modules.misc.middleclick.MiddleClickActionRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.misc.middleclick.MiddleClickFriendClickerMode
import net.ccbluex.liquidbounce.features.module.modules.misc.middleclick.MiddleClickNukerBlockMode
import net.ccbluex.liquidbounce.features.module.modules.misc.middleclick.MiddleClickPearlMode
import net.ccbluex.liquidbounce.features.module.modules.misc.middleclick.MiddleClickSmartMode
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleVClip
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipDirection
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult

/**
 * MiddleClickAction module
 *
 * Allows you to perform actions with middle clicks.
 */
object ModuleMiddleClickAction : ClientModule(
    "MiddleClickAction",
    ModuleCategories.MISC,
    aliases = listOf("FriendClicker", "MiddleClickPearl")
) {

    init {
        doNotIncludeAlways()
    }

    internal val configuredMode = modes(
        this,
        "Mode",
        MiddleClickFriendClickerMode,
        arrayOf(
            MiddleClickFriendClickerMode,
            MiddleClickPearlMode,
            MiddleClickAmnesiaTargetMode,
            MiddleClickNukerBlockMode,
            MiddleClickSmartMode,
        ),
    )

    init {
        MiddleClickActionRuntimeBridge.install(object : MiddleClickActionRuntime {
            override fun isActive(mode: net.ccbluex.liquidbounce.config.types.group.Mode) = isModeActive(mode)
            override fun findPlayerInCrosshair(pickUpRange: Float) =
                this@ModuleMiddleClickAction.findPlayerInCrosshair(pickUpRange)
            override fun toggleFriend(entity: Player) = this@ModuleMiddleClickAction.toggleFriend(entity)
            override fun setAmnesiaTarget(entity: Player) = this@ModuleMiddleClickAction.setAmnesiaTarget(entity)
            override fun selectNukerBlock(hitResult: BlockHitResult) =
                this@ModuleMiddleClickAction.selectNukerBlock(hitResult)
        })
    }

    @JvmStatic
    fun cancelPick(): Boolean = MiddleClickPearlMode.cancelPick() ||
        MiddleClickNukerBlockMode.cancelPick() || MiddleClickSmartMode.cancelPick()

    internal fun isSmartVClipLockActive(): Boolean {
        return running && configuredMode.activeMode === MiddleClickSmartMode &&
            MiddleClickSmartMode.vClipLock.enabled && ModuleVClip.running
    }

    internal fun isSmartVClipModifierHeld(): Boolean {
        return isSmartVClipLockActive() && MiddleClickSmartMode.vClipLock.isHeld
    }

    internal fun resolveSmartVClipDirection(
        jumpPressed: Boolean,
        shiftPressed: Boolean,
        repeatDelayTicks: Int,
    ): VClipDirection? {
        if (!isSmartVClipLockActive()) return null
        return MiddleClickSmartMode.vClipLock.resolveDirection(jumpPressed, shiftPressed, repeatDelayTicks)
    }

    internal fun resetSmartVClipLock() = MiddleClickSmartMode.vClipLock.reset()

    override fun onDisabled() {
        MiddleClickPearlMode.disable()
        MiddleClickSmartMode.reset()
    }

    private fun isModeActive(mode: net.ccbluex.liquidbounce.config.types.group.Mode): Boolean =
        running && configuredMode.activeMode === mode

    internal fun findPlayerInCrosshair(pickUpRange: Float): Player? {
        val rotation = player.rotation
        val entity = (findEntityInCrosshair(pickUpRange.toDouble(), rotation) { it is Player }
            ?: return null).entity as Player

        return entity.takeIf {
            isLookingAtEntity(
                toEntity = it,
                rotation = rotation,
                range = pickUpRange.toDouble(),
                throughWallsRange = 0.0,
            ) != null
        }
    }

    internal fun toggleFriend(entity: Player) {
        val name = entity.scoreboardName
        val friend = FriendManager.Friend(name, null)

        if (FriendManager.isFriend(name)) {
            FriendManager.friends.remove(friend)
            notification("FriendClicker", message("removedFriend", name), NotificationEvent.Severity.INFO)
            return
        }

        FriendManager.friends.add(friend)
        notification("FriendClicker", message("addedFriend", name), NotificationEvent.Severity.INFO)
    }

    internal fun setAmnesiaTarget(entity: Player): Boolean {
        val name = entity.gameProfile.name ?: return false
        ModuleAmnesia.setTargetName(name)
        notification(
            "MiddleClickAction",
            message("amnesiaTargetSet", name),
            NotificationEvent.Severity.INFO,
        )
        return true
    }

    internal fun selectNukerBlock(hitResult: BlockHitResult): Boolean {
        val block = ModuleNuker.selectBlock(hitResult.blockPos) ?: return false
        notification(
            "MiddleClickAction",
            message("nukerBlockSet", block.name.string),
            NotificationEvent.Severity.INFO,
        )
        return true
    }

}
