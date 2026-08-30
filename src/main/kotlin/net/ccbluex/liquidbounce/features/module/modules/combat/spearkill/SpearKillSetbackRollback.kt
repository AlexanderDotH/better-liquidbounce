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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.RemoteKillSetbackRegistry
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.player.Player
import java.util.concurrent.atomic.AtomicReference

internal class SpearKillSetbackCallbacks<P : Any, T : Any>(
    val beforeCorrection: (P, T) -> Unit,
    val afterCorrection: (P, T) -> Unit,
)

/** Installs exactly one SpearKill callback owner while leaving the uninstalled boundary neutral. */
internal class SpearKillSetbackCallbackBinding<P : Any, T : Any> {

    private val callbacks = AtomicReference<SpearKillSetbackCallbacks<P, T>?>(null)

    fun install(callbacks: SpearKillSetbackCallbacks<P, T>) {
        check(this.callbacks.compareAndSet(null, callbacks)) {
            "SpearKill setback callbacks are already installed"
        }
    }

    fun beforeCorrection(packet: P, player: T) {
        callbacks.get()?.beforeCorrection?.invoke(packet, player)
    }

    fun afterCorrection(packet: P, player: T) {
        callbacks.get()?.afterCorrection?.invoke(packet, player)
    }
}

object SpearKillSetbackHook {

    private val callbackBinding =
        SpearKillSetbackCallbackBinding<ClientboundPlayerPositionPacket, Player>()

    internal fun install(
        callbacks: SpearKillSetbackCallbacks<ClientboundPlayerPositionPacket, Player>,
    ) {
        callbackBinding.install(callbacks)
    }

    @JvmStatic
    fun beforeCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        callbackBinding.beforeCorrection(packet, player)
        RemoteKillSetbackRegistry.beforeCorrection(packet, player)
    }

    @JvmStatic
    fun afterCorrection(packet: ClientboundPlayerPositionPacket, player: Player) {
        callbackBinding.afterCorrection(packet, player)
        RemoteKillSetbackRegistry.afterCorrection(packet, player)
    }
}
