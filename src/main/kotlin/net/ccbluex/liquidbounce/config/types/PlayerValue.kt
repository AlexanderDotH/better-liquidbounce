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

package net.ccbluex.liquidbounce.config.types

import com.google.gson.annotations.SerializedName
import net.ccbluex.liquidbounce.config.gson.stategies.Exclude

class PlayerValue(
    name: String,
    aliases: List<String> = emptyList(),
    defaultValue: String,
) : Value<String>(name, aliases, defaultValue, ValueType.PLAYER) {

    /**
     * Registry endpoint for the ClickGUI player picker.
     */
    @Exclude
    @SerializedName("registry")
    val registry: String = "world_players"
}
