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

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.utils.client.mc
import java.io.File
import java.io.Reader

/**
 * Public facade for the hierarchical config system.
 */
object ConfigSystem {

    const val KEY_PREFIX = "liquidbounce"

    private const val CLIENT_DIRECTORY_NAME = "LiquidBounce"

    private var firstLaunch = false

    val isFirstLaunch: Boolean
        get() {
            rootFolder
            return firstLaunch
        }

    val rootFolder by lazy {
        File(mc.gameDirectory, CLIENT_DIRECTORY_NAME).apply {
            if (!exists()) {
                firstLaunch = true
                mkdir()
            }
        }
    }

    val userConfigsFolder by lazy {
        File(rootFolder, "configs").apply {
            if (!exists()) {
                mkdir()
            }
        }
    }

    internal val backupFolder by lazy {
        File(rootFolder, "backups").apply {
            if (!exists()) {
                mkdir()
            }
        }
    }

    val configs = ArrayList<Config>()

    fun findValueByKey(key: String): Value<*>? {
        ensureRootKeys()
        val normalizedKey = normalizeKeyInput(key)
        return configs.asSequence()
            .flatMap { it.collectValuesRecursively(normalizedKey) }
            .firstOrNull { it.key?.equals(normalizedKey, true) == true }
    }

    fun findValueGroupByKey(key: String): ValueGroup? {
        ensureRootKeys()
        val normalizedKey = normalizeKeyInput(key)
        return configs.asSequence()
            .flatMap { it.collectValueGroupsRecursively(normalizedKey) }
            .firstOrNull { it.key?.equals(normalizedKey, true) == true }
    }

    fun valueKeySequence(prefix: String): Sequence<String> = sequence {
        ensureRootKeys()
        for (valueGroup in configs) {
            for (value in valueGroup.collectValuesRecursively(prefix)) {
                value.key?.let { yield(it) }
            }
        }
    }

    fun valueGroupsKeySequence(prefix: String): Sequence<String> = sequence {
        ensureRootKeys()
        for (valueGroup in configs) {
            for (child in valueGroup.collectValueGroupsRecursively(prefix)) {
                child.key?.let { yield(it) }
            }
        }
    }

    fun root(name: String, tree: MutableCollection<out ValueGroup> = mutableListOf()): Config {
        @Suppress("UNCHECKED_CAST")
        return root(Config(name, value = tree as MutableCollection<Value<*>>))
    }

    fun root(config: Config): Config {
        config.walkInit()
        configs.add(config)
        return config
    }

    fun backup(fileName: String, groups: Iterable<Config> = configs) {
        ConfigFileStorage.backup(backupFolder, fileName, groups)
    }

    fun restore(fileName: String) {
        ConfigFileStorage.restore(backupFolder, rootFolder, fileName, configs)
    }

    fun loadAll() {
        configs.forEach(ConfigFileStorage::load)
    }

    fun load(config: Config) {
        ConfigFileStorage.load(config)
    }

    fun storeAll() {
        configs.forEach(ConfigFileStorage::store)
    }

    fun store(config: Config) {
        ConfigFileStorage.store(config)
    }

    fun serializeValueGroup(valueGroup: ValueGroup, gson: Gson = fileGson): JsonObject =
        ConfigValueGroupCodec.serialize(valueGroup, gson)

    fun deserializeValueGroup(valueGroup: ValueGroup, reader: Reader, gson: Gson = fileGson) {
        ConfigValueGroupCodec.deserialize(valueGroup, reader, gson)
    }

    fun deserializeValueGroup(valueGroup: ValueGroup, jsonElement: JsonElement) {
        ConfigValueGroupCodec.deserialize(valueGroup, jsonElement)
    }

    fun deserializeValue(value: Value<*>, jsonObject: JsonObject) {
        ConfigValueGroupCodec.deserializeValue(value, jsonObject)
    }

    private fun ensureRootKeys() {
        for (valueGroup in configs) {
            if (valueGroup.key == null) {
                valueGroup.walkKeyPath()
            }
        }
    }

    private fun normalizeKeyInput(key: String): String {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            return trimmed
        }

        val prefix = "$KEY_PREFIX."
        return if (trimmed.startsWith(prefix, ignoreCase = true)) trimmed else prefix + trimmed
    }
}
