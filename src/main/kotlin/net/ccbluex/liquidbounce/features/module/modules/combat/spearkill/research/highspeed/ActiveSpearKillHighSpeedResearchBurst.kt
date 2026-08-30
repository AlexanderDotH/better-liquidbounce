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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed


import net.minecraft.world.phys.Vec3

internal data class ActiveSpearKillHighSpeedResearchBurst(
    val id: String,
    val start: SpearKillHighSpeedResearchBurstStart,
    val startedAtEpochMs: Long,
    val startedAtMonotonicNanos: Long,
    var primingPacketsSent: Int = 0,
    var primingPacketsDelivered: Int = 0,
    var noFallPacketsSent: Int = 0,
    var finalPacketSent: Boolean = false,
    var finalPacketDelivered: Boolean = false,
    var finalPacketTick: Int? = null,
    var blinkQueued: Boolean = false,
    var tickEndPacketsSuppressed: Int = 0,
    var tickEndBoundariesObserved: Int = 0,
    var correction: SpearKillHighSpeedResearchCorrection? = null,
    var targetHealthAfter: Double? = start.target?.health,
    var damageEventObserved: Boolean = false,
    var targetDeathObserved: Boolean = false,
    var deliveryFailed: Boolean = false,
    var observedLocalPosition: Vec3? = null,
)
