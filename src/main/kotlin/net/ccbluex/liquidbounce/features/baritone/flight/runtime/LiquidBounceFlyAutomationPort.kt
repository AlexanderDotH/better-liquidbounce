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
package net.ccbluex.liquidbounce.features.baritone.flight.runtime

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFlyOwnership
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomation
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationAcquireResult
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationLease
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationLeaseValidation
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationOwnership
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlySteeringIntent
import net.minecraft.world.phys.Vec3
import net.minecraft.client.Minecraft

/** Maps the generation-tagged Fly facade to the runtime's infrastructure-neutral port. */
@Suppress("TooManyFunctions")
object LiquidBounceFlyAutomationPort : BaritoneFlyAutomationPort {
    private var upstreamLease: FlyAutomationLease? = null

    override fun acquire(): BaritoneFlyAcquireResult = when (val acquired = FlyAutomation.acquire()) {
        is FlyAutomationAcquireResult.Acquired -> {
            upstreamLease = acquired.lease
            BaritoneFlyAcquireResult.Acquired(acquired.lease.toRuntimeLease())
        }
        is FlyAutomationAcquireResult.Rejected -> BaritoneFlyAcquireResult.Rejected(acquired.reason)
    }

    override fun validate(lease: BaritoneFlyLease): Boolean {
        val upstream = upstream(lease) ?: return false
        return FlyAutomation.validate(upstream) is FlyAutomationLeaseValidation.Valid
    }

    override fun readiness(lease: BaritoneFlyLease): BaritoneFlyReadiness {
        val profile = upstream(lease)?.let(FlyAutomation::profile)
            ?: return BaritoneFlyReadiness.Unavailable("Fly automation profile is unavailable")
        return when (val readiness = profile.automationReadiness()) {
            FlyAutomationReadiness.Ready -> BaritoneFlyReadiness.Ready
            is FlyAutomationReadiness.Arming -> BaritoneFlyReadiness.Arming(readiness.reason)
            is FlyAutomationReadiness.Unavailable -> BaritoneFlyReadiness.Unavailable(readiness.reason)
        }
    }

    override fun capabilities(lease: BaritoneFlyLease): BaritoneFlyCapabilities {
        val capabilities = upstream(lease)?.let(FlyAutomation::profile)?.automationCapabilities
            ?: return BaritoneFlyCapabilities(horizontal = false, ascend = false, descend = false, landing = false)
        return BaritoneFlyCapabilities(
            horizontal = capabilities.horizontal,
            ascend = capabilities.ascend,
            descend = capabilities.descend,
            landing = capabilities.landing,
            reliableSpeed = capabilities.reliableSpeed.thenCurrentSpeed(),
        )
    }

    override fun automaticEnd(lease: BaritoneFlyLease): String? = upstream(lease)
        ?.let(FlyAutomation::consumeAutomaticEnd)
        ?.reason

    override fun steer(lease: BaritoneFlyLease, steering: BaritoneFlySteering) {
        val upstream = upstream(lease) ?: return
        FlyAutomation.applySteering(
            upstream,
            FlySteeringIntent(
                Vec3(steering.direction.x, steering.direction.y, steering.direction.z),
                steering.sprint,
            ),
        )
    }

    override fun clearSteering(lease: BaritoneFlyLease) {
        upstream(lease)?.let(FlyAutomation::clearSteering)
    }

    override fun suspend(lease: BaritoneFlyLease): Boolean =
        upstream(lease)?.let(FlyAutomation::temporarilySuspend) ?: false

    override fun resume(lease: BaritoneFlyLease): Boolean =
        upstream(lease)?.let(FlyAutomation::resume) ?: false

    override fun release(lease: BaritoneFlyLease) {
        val upstream = upstream(lease) ?: return
        FlyAutomation.release(upstream)
        upstreamLease = null
    }

    private fun upstream(lease: BaritoneFlyLease): FlyAutomationLease? =
        upstreamLease?.takeIf { it.generation == lease.generation && it.modeName == lease.modeName }

    private fun FlyAutomationLease.toRuntimeLease() = BaritoneFlyLease(
        generation = generation,
        modeName = modeName,
        ownership = when (ownership) {
            FlyAutomationOwnership.BARITONE -> BaritoneFlyOwnership.BARITONE
            FlyAutomationOwnership.USER -> BaritoneFlyOwnership.USER
        },
    )

    private fun Boolean.thenCurrentSpeed(): Double? {
        if (!this) return null
        val speed = Minecraft.getInstance().player?.deltaMovement?.length() ?: return null
        return speed.takeIf { it.isFinite() && it > MIN_RELIABLE_SPEED }
    }

    private const val MIN_RELIABLE_SPEED = 1.0e-4
}
