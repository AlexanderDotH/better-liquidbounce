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
package net.ccbluex.liquidbounce.render.playermodel

import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.PlayerSkin

internal interface AmnesiaPlayerModelProvider {
    fun isRunning(): Boolean = false
    fun findTarget(): RemotePlayer? = null
    fun isTarget(entity: LivingEntity): Boolean = false
    fun shouldFakeSneak(entity: LivingEntity): Boolean = false
    fun actionState(entity: LivingEntity): PlayerModelActionState? = null
    fun spoofedName(player: Player): String? = null
    fun spoofedDisplayName(player: Player, original: Component): Component? = null
    fun spoofedSkin(player: AbstractClientPlayer): PlayerSkin? = null
    fun visualTransform(entity: LivingEntity): PlayerModelVisualTransform? = null
}

internal object AmnesiaPlayerModelBridge : AmnesiaPlayerModelProvider {
    private object DisabledProvider : AmnesiaPlayerModelProvider

    @Volatile
    private var provider: AmnesiaPlayerModelProvider = DisabledProvider

    @Synchronized
    fun install(provider: AmnesiaPlayerModelProvider) {
        check(this.provider === DisabledProvider) { "Amnesia player model provider is already installed" }
        this.provider = provider
    }

    override fun isRunning() = provider.isRunning()
    override fun findTarget() = provider.findTarget()
    override fun isTarget(entity: LivingEntity) = provider.isTarget(entity)
    override fun shouldFakeSneak(entity: LivingEntity) = provider.shouldFakeSneak(entity)
    override fun actionState(entity: LivingEntity) = provider.actionState(entity)
    override fun spoofedName(player: Player) = provider.spoofedName(player)
    override fun spoofedDisplayName(player: Player, original: Component) =
        provider.spoofedDisplayName(player, original)
    override fun spoofedSkin(player: AbstractClientPlayer) = provider.spoofedSkin(player)
    override fun visualTransform(entity: LivingEntity) = provider.visualTransform(entity)

    @Synchronized
    internal fun <T> withProviderForTest(provider: AmnesiaPlayerModelProvider?, block: () -> T): T {
        val previous = this.provider
        this.provider = provider ?: DisabledProvider
        return try {
            block()
        } finally {
            this.provider = previous
        }
    }
}
