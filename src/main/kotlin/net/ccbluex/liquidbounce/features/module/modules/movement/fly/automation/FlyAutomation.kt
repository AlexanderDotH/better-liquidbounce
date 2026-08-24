/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation

import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.client.Minecraft

/** Narrow Fly-facing facade used by Baritone without exposing Fly mode internals. */
@Suppress("TooManyFunctions")
internal object FlyAutomation {

    private val controller = FlyAutomationController(ModuleFlyRuntime)

    val selectedModeName: String
        get() = ModuleFlyRuntime.selectedModeName

    val enabled: Boolean
        get() = ModuleFlyRuntime.enabled

    val running: Boolean
        get() = ModuleFly.running

    internal val runtimeSuspended: Boolean
        get() = controller.runtimeSuspended

    fun acquire(): FlyAutomationAcquireResult = controller.acquire()

    fun validate(lease: FlyAutomationLease): FlyAutomationLeaseValidation = controller.validate(lease)

    fun temporarilySuspend(lease: FlyAutomationLease): Boolean = controller.temporarilySuspend(lease)

    fun resume(lease: FlyAutomationLease): Boolean = controller.resume(lease)

    fun release(lease: FlyAutomationLease) = controller.release(lease)

    fun applySteering(lease: FlyAutomationLease, intent: FlySteeringIntent): Boolean {
        return controller.applySteering(lease, intent)
    }

    fun clearSteering(lease: FlyAutomationLease) = controller.clearSteering(lease)

    fun profile(lease: FlyAutomationLease): FlyAutomationProfile? = controller.profile(lease)

    fun consumeAutomaticEnd(lease: FlyAutomationLease): FlyAutomationEnd? {
        return controller.consumeAutomaticEnd(lease)
    }

    internal fun markAutomaticEnd(reason: String) = controller.markAutomaticEnd(reason)

    internal fun activeIntent(): FlySteeringIntent? = controller.activeIntent()

    internal fun onModuleStateChanged(enabled: Boolean) = controller.onModuleStateChanged(enabled)

    internal fun onSelectedModeChanged(modeName: String) = controller.onSelectedModeChanged(modeName)

    private object ModuleFlyRuntime : FlyAutomationRuntime {
        override val enabled: Boolean
            get() = ModuleFly.enabled

        override val selectedModeName: String
            get() = ModuleFly.modes.activeMode.name

        override val selectedProfile: FlyAutomationProfile?
            get() = ModuleFly.modes.activeMode as? FlyAutomationProfile

        override fun setModuleEnabled(enabled: Boolean) {
            ModuleFly.enabled = enabled
        }

        override fun enableSelectedMode() {
            ModuleFly.modes.activeMode.enable()
        }

        override fun disableSelectedMode() {
            ModuleFly.modes.activeMode.disable()
        }
    }
}

/** Input view for Fly modes. With no valid lease it is a no-op over physical input. */
internal object FlyAutomationInput {

    fun directional(physical: DirectionalInput): DirectionalInput {
        val intent = FlyAutomation.activeIntent() ?: return physical
        val minecraft = Minecraft.getInstance()
        val userInput = DirectionalInput(minecraft.options)
        val yaw = minecraft.player?.yRot ?: 0f
        return FlyAutomationInputResolver.directional(intent, userInput, yaw)
    }

    fun jump(physical: Boolean): Boolean {
        val intent = FlyAutomation.activeIntent() ?: return physical
        val options = Minecraft.getInstance().options
        if (options.keyJump.isDown || options.keyShift.isDown) return options.keyJump.isDown
        return FlyAutomationInputResolver.jump(intent, physical = false)
    }

    fun sneak(physical: Boolean): Boolean {
        val intent = FlyAutomation.activeIntent() ?: return physical
        val options = Minecraft.getInstance().options
        if (options.keyJump.isDown || options.keyShift.isDown) return options.keyShift.isDown
        return FlyAutomationInputResolver.sneak(intent, physical = false)
    }

    fun sprint(physical: Boolean): Boolean = FlyAutomationInputResolver.sprint(FlyAutomation.activeIntent(), physical)

    fun desiredYaw(physical: Float): Float {
        val intent = FlyAutomation.activeIntent() ?: return physical
        val userInput = DirectionalInput(Minecraft.getInstance().options)
        return FlyAutomationInputResolver.desiredYaw(intent, userInput) ?: physical
    }

    fun desiredYaw(physical: Float, @Suppress("UNUSED_PARAMETER") physicalInput: DirectionalInput): Float {
        val intent = FlyAutomation.activeIntent() ?: return physical
        val userInput = DirectionalInput(Minecraft.getInstance().options)
        return FlyAutomationInputResolver.desiredYaw(intent, userInput) ?: physical
    }
}
