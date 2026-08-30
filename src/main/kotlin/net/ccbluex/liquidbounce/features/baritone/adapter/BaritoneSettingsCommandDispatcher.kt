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

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCommandOutput
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue

internal interface BaritoneSettingsCommandAccess {
    fun settings(): List<BaritoneSetting>
    fun setting(name: BaritoneSettingName): BaritoneSetting?
    fun updateSetting(name: BaritoneSettingName, value: BaritoneSettingValue): BaritoneResult<BaritoneSetting>
    fun resetSetting(name: BaritoneSettingName): BaritoneResult<BaritoneSetting>
    fun resetSettings(): BaritoneResult<List<BaritoneSetting>>
    fun writeSetting(name: String, rawValue: String): BaritoneResult<BaritoneSetting>
}

internal interface BaritoneSettingsCommandPersistence {
    fun store()
    fun load()
}

internal class LiquidBounceBaritoneSettingsPersistence(
    private val settingsConfig: BaritoneSettingsConfig,
) : BaritoneSettingsCommandPersistence {
    override fun store() = ConfigSystem.store(settingsConfig)
    override fun load() = ConfigSystem.load(settingsConfig)
}

internal class BaritoneSettingsCommandDispatcher(
    private val access: BaritoneSettingsCommandAccess,
    private val persistence: BaritoneSettingsCommandPersistence,
) {
    fun execute(command: String): BaritoneResult<BaritoneCommandOutput>? =
        when (val request = parseBaritoneSettingsCommand(command)) {
            null -> null
            BaritoneSettingsCommandRequest.ListAll -> listSettings(modifiedOnly = false)
            BaritoneSettingsCommandRequest.ListModified -> listSettings(modifiedOnly = true)
            BaritoneSettingsCommandRequest.Save -> persistenceResult(persistence::store, "Settings saved to baritone.json")
            BaritoneSettingsCommandRequest.Load -> persistenceResult(persistence::load, "Settings loaded from baritone.json")
            is BaritoneSettingsCommandRequest.Reset -> reset(request.name)
            is BaritoneSettingsCommandRequest.Toggle -> toggle(request.name)
            is BaritoneSettingsCommandRequest.ReadOrWrite -> readOrWrite(request.name, request.rawValue)
        }

    private fun listSettings(modifiedOnly: Boolean): BaritoneResult<BaritoneCommandOutput> {
        val settings = access.settings()
        val selected = if (modifiedOnly) settings.filter { it.value != it.defaultValue } else settings
        return output(selected.map { "${it.name.value} ${it.value.toUpstreamString()}" })
    }

    private fun reset(name: String?): BaritoneResult<BaritoneCommandOutput> {
        if (name == null) return failure(BaritoneErrorCode.INVALID_FIELD, "Specify a setting or 'all'", "command")
        if (name.equals("all", ignoreCase = true)) {
            return access.resetSettings().mapSuccess { BaritoneCommandOutput(listOf("All Baritone settings were reset")) }
        }
        return access.resetSetting(BaritoneSettingName(name)).mapSuccess {
            BaritoneCommandOutput(listOf("Reset ${it.name.value} to ${it.value.toUpstreamString()}"))
        }
    }

    private fun toggle(name: String?): BaritoneResult<BaritoneCommandOutput> {
        if (name == null) return failure(BaritoneErrorCode.INVALID_FIELD, "Specify a Boolean setting", "command")
        val settingName = BaritoneSettingName(name)
        val setting = access.setting(settingName)
            ?: return failure(BaritoneErrorCode.NOT_FOUND, "Unknown Baritone setting: $name", name)
        if (setting.type != BaritoneSettingType.BOOLEAN) {
            return failure(BaritoneErrorCode.INVALID_FIELD, "Setting '$name' is not Boolean", name)
        }
        val toggled = !(setting.value as BaritoneSettingValue.BooleanValue).value
        return access.updateSetting(settingName, BaritoneSettingValue.BooleanValue(toggled)).mapSuccess {
            BaritoneCommandOutput(listOf("${it.name.value} ${it.value.toUpstreamString()}"))
        }
    }

    private fun readOrWrite(name: String, rawValue: String?): BaritoneResult<BaritoneCommandOutput> {
        if (rawValue != null) {
            return access.writeSetting(name, rawValue).mapSuccess {
                BaritoneCommandOutput(listOf("${it.name.value} ${it.value.toUpstreamString()}"))
            }
        }
        val setting = access.setting(BaritoneSettingName(name))
            ?: return failure(BaritoneErrorCode.NOT_FOUND, "Unknown Baritone setting: $name", name)
        return output(listOf("${setting.name.value} ${setting.value.toUpstreamString()}"))
    }

    private fun persistenceResult(operation: () -> Unit, message: String): BaritoneResult<BaritoneCommandOutput> {
        operation()
        return output(listOf(message))
    }

    private fun output(messages: List<String>) = BaritoneResult.Success(BaritoneCommandOutput(messages))

    private fun <T> failure(code: BaritoneErrorCode, message: String, field: String? = null): BaritoneResult<T> =
        BaritoneResult.Failure(BaritoneError(code, message, field))
}

private inline fun <T, R> BaritoneResult<T>.mapSuccess(transform: (T) -> R): BaritoneResult<R> = when (this) {
    is BaritoneResult.Success -> BaritoneResult.Success(transform(value))
    is BaritoneResult.Failure -> this
}
