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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.rejectFightBotSpearUse(target: LivingEntity): SpearKillFightBotState {
    clearFightBotSpearUse(SpearKillFightBotTerminal.Rejection)
    fightBotSpearTarget = target
    fightBotSpearState = SpearKillFightBotState.Rejected
    return fightBotSpearState
}
