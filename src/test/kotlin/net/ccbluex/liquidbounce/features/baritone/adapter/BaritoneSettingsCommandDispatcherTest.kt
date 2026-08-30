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

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCommandOutput
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BaritoneSettingsCommandDispatcherTest {

    private val access = FakeSettingsAccess()
    private val persistence = RecordingPersistence()
    private val dispatcher = BaritoneSettingsCommandDispatcher(access, persistence)

    @Test
    fun `non-settings commands remain available to the native command manager`() {
        assertNull(dispatcher.execute("goto 1 2 3"))
    }

    @Test
    fun `list aliases retain order and modified filtering`() {
        access.currentSettings = listOf(booleanSetting("unchanged", false, false), booleanSetting("changed", true, false))

        assertEquals(listOf("unchanged false", "changed true"), dispatcher.execute("settings").messages())
        assertEquals(listOf("changed true"), dispatcher.execute("set modified").messages())
    }

    @Test
    fun `save load and raw writes invoke exactly one matching dependency`() {
        assertEquals(listOf("Settings saved to baritone.json"), dispatcher.execute("settings save").messages())
        assertEquals(listOf("Settings loaded from baritone.json"), dispatcher.execute("setting load").messages())
        assertEquals(listOf("allowSprint true"), dispatcher.execute("set allowSprint true").messages())

        assertEquals(listOf("store", "load", "write:allowSprint=true"), persistence.events + access.events)
    }

    @Test
    fun `toggle and reset keep their existing result messages`() {
        access.currentSettings = listOf(booleanSetting("allowSprint", false, true))

        assertEquals(listOf("allowSprint true"), dispatcher.execute("settings toggle allowSprint").messages())
        assertEquals(listOf("Reset allowSprint to true"), dispatcher.execute("settings reset allowSprint").messages())
        assertEquals(listOf("update:allowSprint=true", "reset:allowSprint"), access.events)
    }

    private fun BaritoneResult<BaritoneCommandOutput>?.messages(): List<String> =
        (this as BaritoneResult.Success).value.messages

    private fun booleanSetting(name: String, value: Boolean, default: Boolean) = BaritoneSetting(
        name = BaritoneSettingName(name),
        type = BaritoneSettingType.BOOLEAN,
        value = BaritoneSettingValue.BooleanValue(value),
        defaultValue = BaritoneSettingValue.BooleanValue(default),
        description = "$name setting",
        mutable = true,
    )

    private class RecordingPersistence : BaritoneSettingsCommandPersistence {
        val events = mutableListOf<String>()
        override fun store() { events += "store" }
        override fun load() { events += "load" }
    }

    private class FakeSettingsAccess : BaritoneSettingsCommandAccess {
        var currentSettings = emptyList<BaritoneSetting>()
        val events = mutableListOf<String>()

        override fun settings() = currentSettings

        override fun setting(name: BaritoneSettingName) = currentSettings.firstOrNull { it.name == name }

        override fun updateSetting(
            name: BaritoneSettingName,
            value: BaritoneSettingValue,
        ): BaritoneResult<BaritoneSetting> {
            events += "update:${name.value}=${value.toUpstreamString()}"
            return BaritoneResult.Success(copySetting(setting(name)!!, value))
        }

        override fun resetSetting(name: BaritoneSettingName): BaritoneResult<BaritoneSetting> {
            events += "reset:${name.value}"
            val current = setting(name)!!
            return BaritoneResult.Success(copySetting(current, current.defaultValue))
        }

        override fun resetSettings(): BaritoneResult<List<BaritoneSetting>> = BaritoneResult.Success(currentSettings)

        override fun writeSetting(name: String, rawValue: String): BaritoneResult<BaritoneSetting> {
            events += "write:$name=$rawValue"
            return BaritoneResult.Success(booleanSetting(name, rawValue.toBooleanStrict(), false))
        }

        private fun copySetting(setting: BaritoneSetting, value: BaritoneSettingValue) = BaritoneSetting(
            name = setting.name,
            type = setting.type,
            value = value,
            defaultValue = setting.defaultValue,
            description = setting.description,
            mutable = setting.mutable,
            options = setting.options,
        )

        private fun booleanSetting(name: String, value: Boolean, default: Boolean) = BaritoneSetting(
            name = BaritoneSettingName(name),
            type = BaritoneSettingType.BOOLEAN,
            value = BaritoneSettingValue.BooleanValue(value),
            defaultValue = BaritoneSettingValue.BooleanValue(default),
            description = "$name setting",
            mutable = true,
        )
    }
}
