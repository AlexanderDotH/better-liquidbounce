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
package net.ccbluex.liquidbounce.features.module.modules.render.playermodel

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation

internal enum class PlayerModelState {
    POSITION,
    ROTATION,
    POSE,
    MOVEMENT,
    HELD_ITEM,
    ACTIONS,
}

internal enum class PlayerModelPart {
    HEAD,
    BODY,
}

internal interface PlayerModelSettingsHook {
    fun replacementEnabled(): Boolean
    fun stateEnabled(state: PlayerModelState): Boolean
    fun partAllowed(part: PlayerModelPart): Boolean
    fun interpolatedRotation(partialTicks: Float): Rotation?
}

internal object PlayerModelSettingsBridge : PlayerModelSettingsHook {
    private object DisabledSettings : PlayerModelSettingsHook {
        override fun replacementEnabled() = false
        override fun stateEnabled(state: PlayerModelState) = false
        override fun partAllowed(part: PlayerModelPart) = false
        override fun interpolatedRotation(partialTicks: Float): Rotation? = null
    }

    private var provider: PlayerModelSettingsHook = DisabledSettings

    fun install(provider: PlayerModelSettingsHook) {
        this.provider = provider
    }

    override fun replacementEnabled() = provider.replacementEnabled()
    override fun stateEnabled(state: PlayerModelState) = provider.stateEnabled(state)
    override fun partAllowed(part: PlayerModelPart) = provider.partAllowed(part)
    override fun interpolatedRotation(partialTicks: Float) = provider.interpolatedRotation(partialTicks)

    internal fun <T> withProviderForTest(provider: PlayerModelSettingsHook, block: () -> T): T {
        val previous = this.provider
        this.provider = provider
        return try {
            block()
        } finally {
            this.provider = previous
        }
    }
}
