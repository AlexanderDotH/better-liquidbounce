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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.DirectPacketRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.SpearKillKineticDamageRequirements
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.estimateSpearKillKineticDamage
import net.minecraft.world.item.component.KineticWeapon

internal fun SpearKillModuleState.spearKillKineticDamageRequirements(
    kineticWeapon: KineticWeapon,
): SpearKillKineticDamageRequirements? {
    val condition = kineticWeapon.damageConditions.orElse(null) ?: return null
    return SpearKillKineticDamageRequirements(
        minimumAttackerSpeed = condition.minSpeed.toDouble(),
        minimumRelativeSpeed = condition.minRelativeSpeed.toDouble(),
        damageMultiplier = kineticWeapon.damageMultiplier.toDouble(),
    )
}
internal fun SpearKillModuleState.hasSpearKillKineticDamageRequirements(
    plan: DirectPacketRoutePlan,
    requirements: SpearKillKineticDamageRequirements,
): Boolean {
    val terminalMovement = plan.route.outboundMovements.lastOrNull() ?: return false
    return estimateSpearKillKineticDamage(
        deliveredMovement = terminalMovement,
        targetMovement = plan.targetSnapshot.velocity,
        lookDirection = terminalMovement,
        requirements = requirements,
    ).meetsRequirements
}
