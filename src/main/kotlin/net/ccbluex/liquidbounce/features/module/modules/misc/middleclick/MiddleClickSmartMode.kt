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
package net.ccbluex.liquidbounce.features.module.modules.misc.middleclick

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleVClip
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

internal object MiddleClickSmartMode : Mode("Smart") {

    private val friendClicker = MiddleClickSmartFriendClicker(this)
    private val pearl = MiddleClickSmartPearl(this)
    private val amnesiaTarget = MiddleClickSmartAmnesiaTarget(this)
    private val nukerBlock = MiddleClickSmartNukerBlock(this)
    internal val vClipLock = MiddleClickSmartVClipLock(this)
    private var cancelsVanillaPick = false

    init {
        tree(friendClicker)
        tree(pearl)
        tree(amnesiaTarget)
        tree(nukerBlock)
        tree(vClipLock)
    }

    @Suppress("unused")
    private val middleClickHandler = handler<MouseButtonEvent> { event ->
        if (!event.isMiddleButton) return@handler
        if (event.screen != null) {
            reset()
            return@handler
        }
        when {
            event.isPressed -> cancelsVanillaPick = handlePress()
            event.isReleased -> handleRelease()
        }
    }

    @Suppress("unused")
    private val screenHandler = handler<GameTickEvent> { if (mc.gui.screen() != null) reset() }

    @Suppress("unused")
    private val worldHandler = handler<WorldChangeEvent> { reset() }

    fun cancelPick(): Boolean {
        if (!MiddleClickActionRuntimeBridge.isActive(this)) return false
        val shouldCancel = cancelsVanillaPick || pearl.cancelPick()
        cancelsVanillaPick = false
        return shouldCancel
    }

    fun reset() {
        cancelsVanillaPick = false
        pearl.reset()
        vClipLock.reset()
    }

    override fun disable() = reset()

    private fun handlePress(): Boolean {
        val amnesiaPlayer = acquireAmnesiaPlayer()
        val friendPlayer = acquireFriendPlayer()
        val action = MiddleClickSmartResolver.resolve(smartInput(amnesiaPlayer, friendPlayer))
        return execute(action, amnesiaPlayer, friendPlayer)
    }

    private fun smartInput(amnesiaPlayer: Player?, friendPlayer: Player?) = MiddleClickSmartInput(
        target = classifyTarget(amnesiaPlayer, friendPlayer),
        options = configuredOptions(),
        friendTargetAcquired = friendPlayer != null,
        amnesiaRunning = ModuleAmnesia.running,
        amnesiaTargetAcquired = amnesiaPlayer != null,
        nukerRunning = ModuleNuker.running,
        vClipRunning = ModuleVClip.running,
    )

    private fun handleRelease() {
        vClipLock.release()
        pearl.release()
    }

    private fun acquireAmnesiaPlayer(): Player? = if (amnesiaTarget.enabled && ModuleAmnesia.running) {
        MiddleClickActionRuntimeBridge.findPlayerInCrosshair(amnesiaTarget.pickUpRange)
    } else {
        null
    }

    private fun acquireFriendPlayer(): Player? = if (friendClicker.enabled) {
        MiddleClickActionRuntimeBridge.findPlayerInCrosshair(friendClicker.pickUpRange)
    } else {
        null
    }

    private fun classifyTarget(amnesiaPlayer: Player?, friendPlayer: Player?): MiddleClickSmartTarget {
        if (amnesiaPlayer != null || friendPlayer != null) return MiddleClickSmartTarget.PLAYER
        return when (mc.hitResult?.type) {
            HitResult.Type.ENTITY -> MiddleClickSmartTarget.PLAYER
            HitResult.Type.BLOCK -> MiddleClickSmartTarget.BLOCK
            else -> MiddleClickSmartTarget.AIR
        }
    }

    private fun configuredOptions() = MiddleClickSmartOptions(
        friendClicker = friendClicker.enabled,
        pearl = pearl.enabled,
        amnesiaTarget = amnesiaTarget.enabled,
        nukerBlock = nukerBlock.enabled,
        vClipLock = vClipLock.enabled,
    )

    private fun execute(action: MiddleClickSmartAction, amnesiaPlayer: Player?, friendPlayer: Player?): Boolean =
        when (action) {
            MiddleClickSmartAction.FRIEND_CLICKER ->
                friendPlayer?.let(MiddleClickActionRuntimeBridge::toggleFriend) != null
            MiddleClickSmartAction.PEARL -> pearl.press()
            MiddleClickSmartAction.AMNESIA_TARGET ->
                amnesiaPlayer?.let(MiddleClickActionRuntimeBridge::setAmnesiaTarget) == true
            MiddleClickSmartAction.NUKER_BLOCK -> selectCurrentNukerBlock()
            MiddleClickSmartAction.VCLIP_HOLD -> vClipLock.press()
            MiddleClickSmartAction.NONE -> false
        }

    private fun selectCurrentNukerBlock(): Boolean {
        if (!ModuleNuker.running) return false
        val hitResult = mc.hitResult as? BlockHitResult ?: return false
        if (hitResult.type != HitResult.Type.BLOCK) return false
        return MiddleClickActionRuntimeBridge.selectNukerBlock(hitResult)
    }
}
