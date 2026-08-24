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
package net.ccbluex.liquidbounce.features.baritone.adapter

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.config.types.Value
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

data class NativeBaritoneSetting(
    val name: String,
    val type: String,
    val value: String,
    val defaultValue: String,
    val locked: Boolean,
    val options: List<String> = emptyList(),
)

/** Narrow port around Baritone's reflective settings collection. */
interface NativeBaritoneSettings {
    fun settings(): List<NativeBaritoneSetting>
    fun setting(normalizedName: String): NativeBaritoneSetting?
    fun update(normalizedName: String, value: String): Result<NativeBaritoneSetting>
    fun reset(normalizedName: String): Result<NativeBaritoneSetting>
    fun resetAll(): List<NativeBaritoneSetting>
}

/**
 * The authoritative LiquidBounce config root for Baritone settings.
 *
 * All upstream values use Baritone's own canonical string codec. This keeps complex list/map/registry types round-trip
 * safe while still making `baritone.json` participate in LiquidBounce backup and restore.
 */
@Suppress("TooManyFunctions")
class BaritoneSettingsConfig private constructor(
    private val backend: NativeBaritoneSettings,
    private val nativeSettingsFile: Path,
) : Config("Baritone") {

    private val valuesByLowerName = linkedMapOf<String, Value<String>>()
    private val unknownNames = linkedSetOf<String>()
    private val warningLog = CopyOnWriteArrayList<String>()
    private var synchronizing = false

    init {
        backend.resetAll().forEach(::addKnownSetting)
    }

    fun settings(): List<NativeBaritoneSetting> = backend.settings()

    fun setting(name: String): NativeBaritoneSetting? = backend.setting(name.lowercase())

    fun update(name: String, rawValue: String): Result<NativeBaritoneSetting> {
        val normalizedName = name.lowercase()
        return backend.update(normalizedName, rawValue).onSuccess { setting ->
            synchronize(setting)
            ConfigSystem.store(this)
        }
    }

    fun reset(name: String): Result<NativeBaritoneSetting> {
        val normalizedName = name.lowercase()
        return backend.reset(normalizedName).onSuccess { setting ->
            synchronize(setting)
            ConfigSystem.store(this)
        }
    }

    fun resetAllSettings(): List<NativeBaritoneSetting> = backend.resetAll().also { settings ->
        settings.forEach(::synchronize)
        ConfigSystem.store(this)
    }

    fun delete(name: String): Result<Unit> {
        val normalizedName = name.lowercase()
        if (normalizedName !in unknownNames) return reset(normalizedName).map { }

        val value = valuesByLowerName.remove(normalizedName)
            ?: return Result.failure(IllegalArgumentException("Unknown Baritone setting: $name"))
        inner.remove(value)
        unknownNames.remove(normalizedName)
        ConfigSystem.store(this)
        return Result.success(Unit)
    }

    fun migrationWarnings(): List<String> = warningLog.toList()

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        jsonObject.getAsJsonArray("value")?.forEach { element ->
            val storedValue = element.asJsonObject
            val name = storedValue.getAsJsonPrimitive("name")?.asString ?: return@forEach
            val normalizedName = name.lowercase()
            if (normalizedName in valuesByLowerName) return@forEach
            addUnknownSetting(name, storedValue["value"]?.asString.orEmpty())
        }
    }

    private fun importNativeSettings() {
        val parsed = NativeSettingsFileParser.parse(nativeSettingsFile)
        warningLog += parsed.warnings

        parsed.settings.forEach { line ->
            val known = valuesByLowerName[line.normalizedName]
            if (known == null) {
                addUnknownSetting(line.name, line.value).set(line.value)
                warningLog += "Preserved unknown native Baritone setting '${line.name}' from line ${line.lineNumber}"
                return@forEach
            }

            backend.update(line.normalizedName, line.value).onSuccess(::synchronize).onFailure { error ->
                warningLog += "Unable to import Baritone setting '${line.name}' on line ${line.lineNumber}: " +
                    (error.message ?: error::class.simpleName.orEmpty())
            }
        }
    }

    private fun addKnownSetting(setting: NativeBaritoneSetting) {
        val normalizedName = setting.name.lowercase()
        val value = text(setting.name, setting.defaultValue).onChange { requested ->
            if (synchronizing) return@onChange requested
            backend.update(normalizedName, requested).fold(
                onSuccess = NativeBaritoneSetting::value,
                onFailure = {
                    warningLog += "Rejected Baritone setting '${setting.name}': ${it.message.orEmpty()}"
                    backend.setting(normalizedName)?.value ?: setting.defaultValue
                },
            )
        }
        if (setting.locked) value.immutable()
        valuesByLowerName[normalizedName] = value
    }

    private fun addUnknownSetting(name: String, value: String): Value<String> {
        val normalizedName = name.lowercase()
        return valuesByLowerName.getOrPut(normalizedName) {
            unknownNames += normalizedName
            text(name, value)
        }
    }

    private fun synchronize(setting: NativeBaritoneSetting) {
        val value = valuesByLowerName[setting.name.lowercase()] ?: return
        synchronizing = true
        try {
            value.set(setting.value)
        } finally {
            synchronizing = false
        }
    }

    companion object {
        fun install(backend: NativeBaritoneSettings, nativeSettingsFile: Path): BaritoneSettingsConfig {
            val config = BaritoneSettingsConfig(backend, nativeSettingsFile)
            ConfigSystem.root(config)
            if (config.jsonFile.exists()) {
                ConfigSystem.load(config)
            } else {
                config.importNativeSettings()
                ConfigSystem.store(config)
            }
            return config
        }
    }
}
