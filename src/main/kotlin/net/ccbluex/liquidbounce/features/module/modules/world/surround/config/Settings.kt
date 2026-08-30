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
package net.ccbluex.liquidbounce.features.module.modules.world.surround.config

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandCenter

internal enum class SurroundDisableCondition(override val tag: String) : Tagged {
    Y_CHANGE("YChange"),
    XZ_MOVE("XZMove"),
    XZ_SPEED("XZSpeed"),
}

internal enum class SurroundFeature(override val tag: String) : Tagged {
    /** Runs [CommandCenter] when the module is enabled. */
    CENTER("Center"),

    /** Extends when entities block placement spots. */
    EXTEND("Extend"),

    /** Avoids building a larger surround while the player is already in a completed 1x1 hole. */
    NO_WASTE("NoWaste"),

    /** Places blocks below the surround so enemies cannot mine away the player's floor. */
    DOWN("Down"),
}
