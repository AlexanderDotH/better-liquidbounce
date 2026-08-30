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

package net.ccbluex.liquidbounce.config

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import java.util.Locale

fun interface ValueGroupPreDeserializeHook {
    fun prepare(valueGroup: ValueGroup, valuesByName: Map<String, List<JsonObject>>)
}

object ValueGroupDeserializationRegistry {

    private val sequence = ValueGroupDeserializationSequence()

    fun register(
        id: String,
        valueGroupName: String,
        order: Int = 0,
        hook: ValueGroupPreDeserializeHook,
    ) {
        sequence.register(id, valueGroupName, order, hook)
    }

    internal fun applyAll(valueGroup: ValueGroup, valuesByName: Map<String, List<JsonObject>>) {
        sequence.applyAll(valueGroup, valuesByName)
    }
}

internal class ValueGroupDeserializationSequence {

    private val lock = Any()
    private val hooksByKey = mutableMapOf<String, RegisteredValueGroupHook>()

    fun register(
        id: String,
        valueGroupName: String,
        order: Int,
        hook: ValueGroupPreDeserializeHook,
    ) {
        require(id.isNotBlank()) { "Value-group hook id must not be blank" }
        val targetName = normalizedName(valueGroupName)
        synchronized(lock) {
            val key = "$targetName:$id"
            check(key !in hooksByKey) { "Value-group hook '$id' is already registered for '$valueGroupName'" }
            hooksByKey[key] = RegisteredValueGroupHook(targetName, id, order, hook)
        }
    }

    fun applyAll(valueGroup: ValueGroup, valuesByName: Map<String, List<JsonObject>>) {
        snapshot(valueGroup.name).forEach { it.hook.prepare(valueGroup, valuesByName) }
    }

    private fun snapshot(valueGroupName: String): List<RegisteredValueGroupHook> = synchronized(lock) {
        val targetName = normalizedName(valueGroupName)
        hooksByKey.values
            .filter { it.valueGroupName == targetName }
            .sortedWith(compareBy(RegisteredValueGroupHook::order, RegisteredValueGroupHook::id))
    }

    private fun normalizedName(name: String): String {
        require(name.isNotBlank()) { "Value-group name must not be blank" }
        return name.trim().lowercase(Locale.ROOT)
    }
}

private data class RegisteredValueGroupHook(
    val valueGroupName: String,
    val id: String,
    val order: Int,
    val hook: ValueGroupPreDeserializeHook,
)
