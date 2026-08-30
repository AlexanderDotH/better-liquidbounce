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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research

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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal data class MaceKillLocalCorrectionState(
    val expectedPosition: Vec3,
    val routeOrigin: Vec3,
    val researchPhase: MaceClipResearchPhase?,
)

internal data class MaceKillResearchPacketContext(
    val sequence: Int,
    val phase: MaceClipResearchPhase,
    val position: Vec3,
    val outbound: Boolean?,
)

internal data class MaceKillResearchExecution(
    val sessionId: String,
    val descriptor: MaceClipResearchExecutionDescriptor,
    val target: LivingEntity?,
    val startedTick: Int,
    val deadlineTick: Int,
    var nextPacketSequence: Int = 0,
    var primingResolved: Int = 0,
    var outboundDelivered: Int = 0,
    var returnDelivered: Int = 0,
    var abortRequested: Boolean = false,
    var exactReturnDelivered: Boolean = false,
    var completionDeadlineTick: Int? = null,
    var lastTargetHealth: Double? = null,
)
