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
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleVClip
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipDirection
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipMiddleClickInput
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.minecraft.world.item.Items

internal class MiddleClickSmartFriendClicker(owner: Mode) : ToggleableValueGroup(owner, "FriendClicker", true) {
    val pickUpRange by float("PickUpRange", 3.0f, 1f..100f)
}

internal class MiddleClickSmartAmnesiaTarget(owner: Mode) : ToggleableValueGroup(owner, "AmnesiaTarget", true) {
    val pickUpRange by float("PickUpRange", 3.0f, 1f..100f)
}

internal class MiddleClickSmartNukerBlock(owner: Mode) : ToggleableValueGroup(owner, "NukerBlock", true)

internal class MiddleClickSmartPearl(owner: Mode) : ToggleableValueGroup(owner, "Pearl", true) {
    private val slotResetDelay by int("SlotResetDelay", 1, 0..10, "ticks")
    private val stopOnSubmit by floatRange("StopOnSubmit", 85F..90F, 60F..90F, "Pitch")
    private val controller = MiddleClickPearlController()

    fun press(): Boolean = controller.press {
        if (player.xRot in stopOnSubmit) return@press false
        val slot = Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL) ?: return@press false
        SilentHotbar.selectSlotSilently(this, slot, slotResetDelay)
    }

    fun release(): Boolean = controller.release {
        Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL)?.let { useHotbarSlotOrOffhand(it, slotResetDelay) }
    }

    fun cancelPick(): Boolean = controller.cancelsVanillaPick

    fun reset() {
        controller.reset()
        SilentHotbar.resetSlot(this)
    }
}

internal class MiddleClickSmartVClipLock(owner: Mode) : ToggleableValueGroup(owner, "VClipLock", true) {
    private val input = VClipMiddleClickInput()

    val isHeld: Boolean
        get() = input.isHeld

    fun press(): Boolean {
        if (!enabled || !ModuleVClip.running) return false
        input.press()
        return true
    }

    fun release() = input.release()

    fun resolveDirection(jumpPressed: Boolean, shiftPressed: Boolean, repeatDelayTicks: Int): VClipDirection? =
        input.resolveDirection(jumpPressed, shiftPressed, repeatDelayTicks)

    fun reset() = input.reset()

    override fun onDisabled() = reset()
}
