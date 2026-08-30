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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.fireball.runtime

import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationEnd
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.minecraft.world.item.Items

internal object FlyFireballRuntime {

    private var pendingAutomaticEnd: FlyAutomationEnd? = null
    private lateinit var slotResetDelayProvider: () -> IntRange
    private lateinit var requester: EventListener

    var wasTriggered = false

    fun bind(requester: EventListener, slotResetDelayProvider: () -> IntRange) {
        this.requester = requester
        this.slotResetDelayProvider = slotResetDelayProvider
    }

    fun reset() {
        pendingAutomaticEnd = null
        wasTriggered = false
    }

    fun consumeAutomaticEnd(): FlyAutomationEnd? = pendingAutomaticEnd.also {
        pendingAutomaticEnd = null
    }

    fun markAutomaticEnd() {
        pendingAutomaticEnd = FlyAutomationEnd("Fireball launch completed")
    }

    fun hasFireball(): Boolean = findFireballSlot() != null

    fun throwFireball() {
        with(requester) {
            useHotbarSlotOrOffhand(
                findFireballSlot() ?: return,
                ticksUntilReset = slotResetDelayProvider().random(),
            )
        }
    }

    private fun findFireballSlot(): HotbarItemSlot? = Slots.OffhandWithHotbar.findSlot(Items.FIRE_CHARGE)

}
