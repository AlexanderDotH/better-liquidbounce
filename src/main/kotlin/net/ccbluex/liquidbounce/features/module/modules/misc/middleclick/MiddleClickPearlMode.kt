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
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.minecraft.world.item.Items

internal object MiddleClickPearlMode : Mode("Pearl") {

    private val slotResetDelay by int("SlotResetDelay", 1, 0..10, "ticks")
    private val stopOnSubmit by floatRange("StopOnSubmit", 85F..90F, 60F..90F, "Pitch")
    private var wasPressed = false

    @Suppress("unused")
    private val repeatable = handler<GameTickEvent> {
        if (mc.gui.screen() != null || player.xRot in stopOnSubmit) {
            reset()
            return@handler
        }

        if (mc.options.keyPickItem.isDown) press() else release()
    }

    @Suppress("unused")
    private val worldHandler = handler<WorldChangeEvent> { reset() }

    override fun disable() = reset()

    fun cancelPick(): Boolean = MiddleClickActionRuntimeBridge.isActive(this) &&
        Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL) != null

    private fun press() {
        val slot = Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL) ?: return
        SilentHotbar.selectSlotSilently(this, slot, slotResetDelay)
        wasPressed = true
    }

    private fun release() {
        if (!wasPressed) return
        Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL)?.let { useHotbarSlotOrOffhand(it, slotResetDelay) }
        reset()
    }

    private fun reset() {
        wasPressed = false
        SilentHotbar.resetSlot(this)
    }
}
