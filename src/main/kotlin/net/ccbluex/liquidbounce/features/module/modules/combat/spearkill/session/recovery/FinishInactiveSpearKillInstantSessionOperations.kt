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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.requestSpearKillAttemptCompletion

/** Covers a zero-confirmation abort, where no delivery event owns the final session cleanup. */
internal fun SpearKillModuleState.finishInactiveSpearKillInstantSession() {
    if (packetBootSession.active || packetSessionOrigin == null) return

    finishSpearKillFallSafety(player.position(), allowPacket = true)
    packetSessionOrigin = null
    packetSessionSettings = null
    requestSpearKillAttemptCompletion()
    synchronizeSpearKillServerSneak()
}
