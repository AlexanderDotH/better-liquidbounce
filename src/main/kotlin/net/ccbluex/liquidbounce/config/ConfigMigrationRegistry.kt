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
import java.util.Locale

fun interface ConfigMigration {
    fun migrate(root: JsonObject)
}

object ConfigMigrationOrder {
    const val FIGHT_BOT = 100
    const val MACE_KILL = 200
    const val REACH = 300
}

data class ConfigMigrationTarget private constructor(val id: String) {

    companion object {
        @JvmField
        val MODULES = ConfigMigrationTarget("modules")

        @JvmStatic
        fun named(name: String): ConfigMigrationTarget {
            require(name.isNotBlank()) { "Config migration target must not be blank" }
            return ConfigMigrationTarget(name.trim().lowercase(Locale.ROOT))
        }
    }
}

/**
 * Composition boundary for feature-owned migrations that must run before module config deserialization.
 */
object ConfigMigrationRegistry {

    private val sequence = ConfigMigrationSequence()

    fun register(id: String, order: Int, migration: ConfigMigration) {
        register(ConfigMigrationTarget.MODULES, id, order, migration)
    }

    fun register(target: ConfigMigrationTarget, id: String, order: Int, migration: ConfigMigration) {
        sequence.register(target, id, order, migration)
    }

    fun applyAll(root: JsonObject) {
        applyAll(ConfigMigrationTarget.MODULES, root)
    }

    fun applyAll(target: ConfigMigrationTarget, root: JsonObject) {
        sequence.applyAll(target, root)
    }
}

internal class ConfigMigrationSequence {

    private val lock = Any()
    private val migrationsById = mutableMapOf<String, RegisteredConfigMigration>()

    fun register(id: String, order: Int, migration: ConfigMigration) {
        register(ConfigMigrationTarget.MODULES, id, order, migration)
    }

    fun register(target: ConfigMigrationTarget, id: String, order: Int, migration: ConfigMigration) {
        require(id.isNotBlank()) { "Config migration id must not be blank" }
        synchronized(lock) {
            val key = registrationKey(target, id)
            check(key !in migrationsById) { "Config migration '$id' is already registered for '${target.id}'" }
            migrationsById[key] = RegisteredConfigMigration(target, id, order, migration)
        }
    }

    fun applyAll(root: JsonObject) {
        applyAll(ConfigMigrationTarget.MODULES, root)
    }

    fun applyAll(target: ConfigMigrationTarget, root: JsonObject) {
        snapshot(target).forEach { it.migration.migrate(root) }
    }

    private fun snapshot(target: ConfigMigrationTarget): List<RegisteredConfigMigration> = synchronized(lock) {
        migrationsById.values
            .filter { it.target == target }
            .sortedWith(compareBy(RegisteredConfigMigration::order, RegisteredConfigMigration::id))
    }

    private fun registrationKey(target: ConfigMigrationTarget, id: String) = "${target.id}:$id"
}

private data class RegisteredConfigMigration(
    val target: ConfigMigrationTarget,
    val id: String,
    val order: Int,
    val migration: ConfigMigration,
)
