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
package net.ccbluex.liquidbounce.features.module.modules.player.offhand

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.KeyEvent
import net.ccbluex.liquidbounce.event.events.RefreshArrayListEvent
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.inventory.OffhandReservationManager
import net.ccbluex.liquidbounce.features.inventory.PlayerInventoryConstraints
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.client.usesViaFabricPlus
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot

/**
 * Offhand module
 *
 * Manages your offhand.
 */
object ModuleOffhand : ClientModule("Offhand", ModuleCategories.PLAYER, aliases = listOf("AutoTotem")) {

    private val inventoryConstraints = tree(PlayerInventoryConstraints())
    private val switchMode by enumChoice(
        "SwitchMode",
        default = if (!usesViaFabricPlus) HandSwitchMode.SWITCH else HandSwitchMode.AUTOMATIC
    )
    internal val switchDelay by int("SwitchDelay", 0, 0..500, "ms")
    private val cycleSlots by key("Cycle", InputConstants.KEY_H)

    internal object Gapple : ToggleableValueGroup(this, "Gapple", true) {
        object WhileHoldingSword : ToggleableValueGroup(this, "WhileHoldingSword", true) {
            val onlyWhileKa by boolean("OnlyWhileKillAura", true)
        }

        val gappleBind by key("GappleBind")

        init {
            tree(WhileHoldingSword)
        }
    }

    internal object Crystal : ToggleableValueGroup(this, "Crystal", true) {
        val onlyWhileCa by boolean("OnlyWhileCrystalAura", false)
        val whenNoTotems by boolean("WhenNoTotems", true)
        val crystalBind by key("CrystalBind")
    }

    internal object Strength : ToggleableValueGroup(this, "StrengthPotion", false) {
        val onlyWhileHoldingSword by boolean("OnlyWhileHoldingSword", true)
        val onlyWhileKa by boolean("OnlyWhileKillAura", true)
        val strengthBind by key("StrengthBind")
    }

    internal object Block : ToggleableValueGroup(this, "Block", false) {
        val whileScaffold by boolean("WhileScaffold", true)
        val whileEagle by boolean("WhileEagle", true)
    }

    init {
        treeAll(
            Totem,
            Crystal,
            Gapple,
            Strength,
            Block,
        )
    }

    private val chronometer = Chronometer()
    internal var activeMode = EquipmentMode.NONE
    private var lastMode: EquipmentMode? = null
    private var lastTagMode = EquipmentMode.NONE
    internal var staticMode = EquipmentMode.NONE
    internal var previousEquipment: PreviousEquipment? = null

    override val tag: String
        get() = activeMode.modeName

    override fun onEnabled() {
        staticMode = when {
            Crystal.enabled && EquipmentMode.CRYSTAL.canCycleTo() -> EquipmentMode.CRYSTAL
            Gapple.enabled -> EquipmentMode.GAPPLE
            Totem.enabled && !Totem.Health.enabled -> EquipmentMode.TOTEM
            else -> EquipmentMode.NONE
        }
    }

    @Suppress("unused")
    val keyHandler = handler<KeyEvent> {
        if (it.action != InputConstants.PRESS) {
            return@handler
        }

        when (it.key.value) {
            Gapple.gappleBind.value -> EquipmentMode.GAPPLE.onBindPress()
            Crystal.crystalBind.value -> EquipmentMode.CRYSTAL.onBindPress()
            Strength.strengthBind.value -> {
                // since we can't cycle to strength, its status has to be checked here
                if (Strength.enabled) {
                    EquipmentMode.STRENGTH.onBindPress()
                }
            }

            cycleSlots.value -> {
                val entries = EquipmentMode.entries
                val startIndex = staticMode.ordinal
                var index = (startIndex + 1) % entries.size

                while (index != startIndex) {
                    val mode = entries[index]
                    if (mode.canCycleTo()) {
                        staticMode = mode
                        return@handler
                    }

                    index = (index + 1) % entries.size
                }
            }
        }
    }

    @Suppress("unused")
    private val autoTotemHandler = handler<ScheduleInventoryActionEvent>(priority = 100) {
        if (!canScheduleInventoryActions()) {
            return@handler
        }

        activeMode = EquipmentMode.entries.firstOrNull(EquipmentMode::shouldEquip) ?: staticMode
        if (activeMode == EquipmentMode.NONE && Totem.Health.switchBack && lastMode == EquipmentMode.TOTEM) {
            activeMode = EquipmentMode.BACK
        }

        if (activeMode != lastTagMode) {
            EventManager.callEvent(RefreshArrayListEvent)
            lastTagMode = activeMode
        }

        if (activeMode != lastMode && lastMode == EquipmentMode.TOTEM) {
            if (!Totem.switchBackStarted) {
                Totem.switchBack.reset()
            }

            Totem.switchBackStarted = true
            if (!Totem.switchBack.hasElapsed(Totem.switchBackDelay.toLong())) {
                return@handler
            }
        }

        Totem.switchBackStarted = false

        if (!chronometer.hasElapsed(activeMode.getDelay().toLong())) {
            return@handler
        }

        val slot = activeMode.getSlot() ?: return@handler
        lastMode = activeMode

        // the item is already located in Off-hand slot
        if (slot == HotbarItemSlot.OFFHAND) {
            return@handler
        }

        if (Totem.Health.switchBack) {
            previousEquipment = PreviousEquipment(slot.itemStack.item, slot)
        }

        val actions = switchMode.performSwitch(slot)
        if (actions.isEmpty()) {
            chronometer.reset()
            return@handler
        }

        if (activeMode != EquipmentMode.TOTEM || !Totem.send(actions)) {
            it.schedule(inventoryConstraints, actions)
        }

        chronometer.reset()
    }

    fun isOperating() = running && activeMode != EquipmentMode.NONE

    internal fun canScheduleInventoryActions() = !OffhandReservationManager.isReservedByOther(this)

}
