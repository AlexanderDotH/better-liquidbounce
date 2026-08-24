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

import baritone.api.Settings
import baritone.api.utils.SettingsUtil
import java.util.function.BiConsumer
import java.util.function.Consumer

sealed interface BaritoneAdapterMessage {
    data class Log(val message: String) : BaritoneAdapterMessage
    data class Notification(val message: String, val error: Boolean) : BaritoneAdapterMessage
    data class Toast(val title: String, val message: String) : BaritoneAdapterMessage
}

fun interface BaritoneMessageSink {
    fun accept(message: BaritoneAdapterMessage)

    companion object {
        val NONE = BaritoneMessageSink { }
    }
}

/** The single authoritative bridge to Baritone's setting reflection and string codecs. */
class OfficialBaritoneSettings(
    private val upstream: Settings,
    private val messageSink: BaritoneMessageSink,
) : NativeBaritoneSettings {

    init {
        resetAll()
        installMessageSinks()
    }

    override fun settings(): List<NativeBaritoneSetting> = upstream.allSettings.asSequence()
        .filterNot(Settings.Setting<*>::isJavaOnly)
        .map(::toNativeSetting)
        .sortedBy { it.name.lowercase() }
        .toList()

    override fun setting(normalizedName: String): NativeBaritoneSetting? = find(normalizedName)?.let(::toNativeSetting)

    override fun update(normalizedName: String, value: String): Result<NativeBaritoneSetting> = runCatching {
        require(normalizedName !in LOCKED_SETTINGS) { "Baritone setting '$normalizedName' is managed by LiquidBounce" }
        val setting = requireNotNull(find(normalizedName)) { "Unknown Baritone setting: $normalizedName" }
        require(!setting.isJavaOnly) { "Baritone setting '$normalizedName' is Java-only" }
        SettingsUtil.parseAndApply(upstream, setting.name.lowercase(), value)
        enforceLockedSettings()
        toNativeSetting(setting)
    }

    override fun reset(normalizedName: String): Result<NativeBaritoneSetting> = runCatching {
        require(normalizedName !in LOCKED_SETTINGS) { "Baritone setting '$normalizedName' is managed by LiquidBounce" }
        val setting = requireNotNull(find(normalizedName)) { "Unknown Baritone setting: $normalizedName" }
        require(!setting.isJavaOnly) { "Baritone setting '$normalizedName' is Java-only" }
        setting.reset()
        enforceLockedSettings()
        toNativeSetting(setting)
    }

    override fun resetAll(): List<NativeBaritoneSetting> {
        upstream.allSettings.asSequence()
            .filterNot(Settings.Setting<*>::isJavaOnly)
            .filterNot { it.name.lowercase() in LOCKED_SETTINGS }
            .forEach(Settings.Setting<*>::reset)
        enforceLockedSettings()
        return settings()
    }

    private fun find(normalizedName: String): Settings.Setting<*>? = upstream.byLowerName[normalizedName.lowercase()]

    private fun toNativeSetting(setting: Settings.Setting<*>): NativeBaritoneSetting = NativeBaritoneSetting(
        name = setting.name,
        type = SettingsUtil.settingTypeToString(setting),
        value = SettingsUtil.settingValueToString(setting),
        defaultValue = if (setting.name.lowercase() in LOCKED_SETTINGS) {
            "false"
        } else {
            SettingsUtil.settingDefaultToString(setting)
        },
        locked = setting.name.lowercase() in LOCKED_SETTINGS,
        options = setting.value.javaClass.takeIf(Class<*>::isEnum)?.enumConstants
            ?.map { (it as Enum<*>).name }
            .orEmpty(),
    )

    private fun enforceLockedSettings() {
        upstream.chatControl.value = false
        upstream.chatControlAnyway.value = false
        upstream.prefixControl.value = false
    }

    private fun installMessageSinks() {
        upstream.logger.value = Consumer { message ->
            runCatching { messageSink.accept(BaritoneAdapterMessage.Log(message.string)) }
        }
        upstream.notifier.value = BiConsumer { message, error ->
            runCatching { messageSink.accept(BaritoneAdapterMessage.Notification(message, error)) }
        }
        upstream.toaster.value = BiConsumer { title, message ->
            runCatching { messageSink.accept(BaritoneAdapterMessage.Toast(title.string, message.string)) }
        }
    }

    companion object {
        val LOCKED_SETTINGS: Set<String> = setOf("chatcontrol", "chatcontrolanyway", "prefixcontrol")
    }
}
