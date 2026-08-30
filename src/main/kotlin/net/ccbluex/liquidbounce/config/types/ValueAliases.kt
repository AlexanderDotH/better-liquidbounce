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

import java.util.function.Consumer
import java.util.function.UnaryOperator

typealias ValueListener<T> = UnaryOperator<T>
typealias ValueChangedListener<T> = Consumer<T>

/**
 * Orders values by name without case sensitivity.
 */
@JvmField
val VALUE_NAME_ORDER: Comparator<in Value<*>> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
