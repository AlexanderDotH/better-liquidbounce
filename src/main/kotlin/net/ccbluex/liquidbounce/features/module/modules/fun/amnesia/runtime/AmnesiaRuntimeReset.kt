/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.runtime

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelDelayState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeBhopState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeCriticalsState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeJesusState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeScaffoldState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeSpinbotState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeVelocityState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelHysteriaState

internal object AmnesiaRuntimeReset {

    fun reset() {
        PlayerModelDelayState.reset()
        PlayerModelHysteriaState.reset()
        PlayerModelFakeScaffoldState.reset()
        PlayerModelFakeCriticalsState.reset()
        PlayerModelFakeJesusState.reset()
        PlayerModelFakeSpinbotState.reset()
        PlayerModelFakeBhopState.reset()
        AmnesiaRuntimeBridge.clearScaffoldRenderState()
        PlayerModelFakeVelocityState.reset()
    }
}
