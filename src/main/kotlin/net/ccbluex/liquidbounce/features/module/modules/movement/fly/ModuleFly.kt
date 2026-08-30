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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerStrideEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomation
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationInput
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationModulePort
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.contract.FlyState
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyVanilla
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.registry.builtInFlyModes
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.runtime.FlyModuleControl
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FINAL_DECISION
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket

/**
 * Fly module
 *
 * Allows you to fly.
 */

object ModuleFly : ClientModule("Fly", ModuleCategories.MOVEMENT, aliases = listOf("Glide", "Jetpack")) {

    override val running: Boolean
        get() = super.running && !FlyAutomation.runtimeSuspended

    init {
        FlyModuleControl.bind(this) { modes.activeMode }
        FlyAutomation.bind(ModuleFlyAutomationPort)
        FlyState.bind(enabledProvider = { enabled }, runningProvider = { running })
    }

    private object ModuleFlyAutomationPort : FlyAutomationModulePort {
        override val enabled: Boolean
            get() = FlyModuleControl.enabled

        override val running: Boolean
            get() = FlyModuleControl.running

        override val selectedModeName: String
            get() = FlyModuleControl.activeMode.name

        override val selectedProfile: FlyAutomationProfile?
            get() = FlyModuleControl.activeMode as? FlyAutomationProfile

        override fun setModuleEnabled(enabled: Boolean) {
            FlyModuleControl.setEnabled(enabled)
        }

        override fun enableSelectedMode() {
            FlyModuleControl.activeMode.enable()
        }

        override fun disableSelectedMode() {
            FlyModuleControl.activeMode.disable()
        }
    }

    internal val modes = choices<Mode>(
        "Mode", FlyVanilla, builtInFlyModes()
    ).apply {
        tagBy(this)
        onChanged { FlyAutomation.onSelectedModeChanged(activeMode.name) }
    }

    private object Visuals : ToggleableValueGroup(this, "Visuals", true) {

        private val stride by boolean("Stride", true)

        @Suppress("unused")
        val strideHandler = handler<PlayerStrideEvent> { event ->
            if (stride) {
                event.strideForce = 0.1.coerceAtMost(player.deltaMovement.horizontalDistance()).toFloat()
            }

        }

    }

    init {
        tree(Visuals)
    }

    private val disableOnSetback by boolean("DisableOnSetback", false)

    private var wasFlyingAllowed = false

    override fun onEnabled() {
        wasFlyingAllowed = player.abilities.mayfly
        player.abilities.mayfly = false
        FlyAutomation.onModuleStateChanged(enabled = true)
    }

    override fun onDisabled() {
        FlyAutomation.onModuleStateChanged(enabled = false)
        player.abilities.mayfly = wasFlyingAllowed
    }

    @Suppress("unused")
    private val automationInputHandler = handler<MovementInputEvent>(priority = FINAL_DECISION) { event ->
        if (FlyAutomation.activeIntent() == null) return@handler

        val physical = DirectionalInput(mc.options)
        event.directionalInput = FlyAutomationInput.directional(physical)
        event.jump = FlyAutomationInput.jump(mc.options.keyJump.isDown)
        event.sneak = FlyAutomationInput.sneak(mc.options.keyShift.isDown)
    }

    @Suppress("unused")
    private val automationSprintHandler = handler<SprintEvent>(priority = FINAL_DECISION) { event ->
        if (FlyAutomation.activeIntent() == null) return@handler
        event.sprint = FlyAutomationInput.sprint(mc.options.keySprint.isDown)
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        // Setback detection
        if (disableOnSetback && event.packet is ClientboundPlayerPositionPacket) {
            chat(markAsError(message("setbackDetected")))
            FlyAutomation.markAutomaticEnd("Fly disabled after a server setback")
            enabled = false
        }

        if (event.packet is ClientboundPlayerAbilitiesPacket) {
            wasFlyingAllowed = event.packet.canFly()
        }
    }

}
