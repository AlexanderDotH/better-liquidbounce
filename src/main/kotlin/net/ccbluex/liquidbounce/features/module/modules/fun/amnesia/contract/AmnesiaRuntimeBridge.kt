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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.VelocityMode
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelActionState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal interface AmnesiaRuntimeHook {
    fun findTarget(): RemotePlayer?
    fun auxiliaryVisualPosition(entity: LivingEntity, partialTicks: Float): Vec3?
    fun actionContributions(entity: LivingEntity): AmnesiaActionContributions
    fun visualEffects(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: Vec3,
        velocityPositionActive: Boolean,
    ): AmnesiaVisualEffects
    fun delayPlayerModelRunning(): Boolean
    fun fakeKillAuraRunning(): Boolean
    fun fakeVelocityRunning(): Boolean
    fun fakeVelocityMode(): VelocityMode
    fun clearScaffoldRenderState()
}

internal data class AmnesiaActionContributions(
    val criticals: PlayerModelActionState? = null,
    val jesus: PlayerModelActionState? = null,
    val scaffold: PlayerModelActionState? = null,
    val bhop: PlayerModelActionState? = null,
    val fakeSneak: Boolean = false,
)

internal data class AmnesiaVisualEffects(
    val spinbot: PlayerModelVisualTransform? = null,
    val jesus: PlayerModelVisualTransform? = null,
    val bhop: PlayerModelVisualTransform? = null,
    val criticals: PlayerModelVisualTransform? = null,
    val scaffold: PlayerModelVisualTransform? = null,
    val criticalsHasRotation: Boolean = false,
    val bhopHasRotation: Boolean = false,
)

internal object AmnesiaRuntimeBridge : AmnesiaRuntimeHook {
    private object DisabledRuntime : AmnesiaRuntimeHook {
        override fun findTarget(): RemotePlayer? = null
        override fun auxiliaryVisualPosition(entity: LivingEntity, partialTicks: Float): Vec3? = null
        override fun actionContributions(entity: LivingEntity) = AmnesiaActionContributions()
        override fun visualEffects(
            entity: LivingEntity,
            partialTicks: Float,
            basePosition: Vec3,
            velocityPositionActive: Boolean,
        ) = AmnesiaVisualEffects()
        override fun delayPlayerModelRunning() = false
        override fun fakeKillAuraRunning() = false
        override fun fakeVelocityRunning() = false
        override fun fakeVelocityMode() = VelocityMode.FREEZE
        override fun clearScaffoldRenderState() = Unit
    }

    private var provider: AmnesiaRuntimeHook = DisabledRuntime

    fun install(provider: AmnesiaRuntimeHook) {
        this.provider = provider
    }

    override fun findTarget() = provider.findTarget()
    override fun auxiliaryVisualPosition(entity: LivingEntity, partialTicks: Float) =
        provider.auxiliaryVisualPosition(entity, partialTicks)
    override fun actionContributions(entity: LivingEntity) = provider.actionContributions(entity)
    override fun visualEffects(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: Vec3,
        velocityPositionActive: Boolean,
    ) = provider.visualEffects(entity, partialTicks, basePosition, velocityPositionActive)
    override fun delayPlayerModelRunning() = provider.delayPlayerModelRunning()
    override fun fakeKillAuraRunning() = provider.fakeKillAuraRunning()
    override fun fakeVelocityRunning() = provider.fakeVelocityRunning()
    override fun fakeVelocityMode() = provider.fakeVelocityMode()
    override fun clearScaffoldRenderState() = provider.clearScaffoldRenderState()

    internal fun <T> withProviderForTest(provider: AmnesiaRuntimeHook, block: () -> T): T {
        val previous = this.provider
        this.provider = provider
        return try {
            block()
        } finally {
            this.provider = previous
        }
    }
}
