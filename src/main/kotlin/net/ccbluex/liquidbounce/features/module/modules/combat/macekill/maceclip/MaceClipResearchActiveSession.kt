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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.minecraft.world.phys.Vec3

internal data class ActiveMaceClipResearchPhase(
    val phase: MaceClipResearchPhase,
    val startedTick: Int,
    val startPosition: Vec3,
    var completedTick: Int? = null,
    var endPosition: Vec3? = null,
)

internal data class ActiveMaceClipResearchSession(
    val id: String,
    val start: MaceClipResearchStart,
    val startedAtEpochMs: Long,
    val startedAtMonotonicNanos: Long,
    val phases: MutableList<ActiveMaceClipResearchPhase> = mutableListOf(),
    val packets: MutableList<MaceClipResearchPacketEvidence> = mutableListOf(),
    val corrections: MutableList<MaceClipResearchCorrectionEvidence> = mutableListOf(),
    var currentPhase: MaceClipResearchPhase? = null,
    var abortRequested: Boolean = false,
    var lastAuthoritativeCorrection: Vec3? = null,
    var targetHealthAfter: Double? = start.target?.health,
    var damageEventObserved: Boolean = false,
    var damageEventAmount: Double? = null,
    var deathObserved: Boolean = false,
    var strikeAttempts: Int = 0,
    var committedAttacks: Int = 0,
)
