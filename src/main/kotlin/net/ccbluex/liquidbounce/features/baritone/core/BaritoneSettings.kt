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

package net.ccbluex.liquidbounce.features.baritone.core

@JvmInline
value class BaritoneSettingName(val value: String) {
    init {
        require(value.isNotBlank()) { "Setting names cannot be blank" }
        require(value == value.trim()) { "Setting names cannot have surrounding whitespace" }
    }
}

enum class BaritoneSettingType {
    BOOLEAN,
    INTEGER,
    LONG,
    DECIMAL,
    STRING,
    ENUM,
    STRING_LIST,
}

sealed interface BaritoneSettingValue {

    val type: BaritoneSettingType

    data class BooleanValue(val value: Boolean) : BaritoneSettingValue {
        override val type = BaritoneSettingType.BOOLEAN
    }

    data class IntegerValue(val value: Int) : BaritoneSettingValue {
        override val type = BaritoneSettingType.INTEGER
    }

    data class LongValue(val value: Long) : BaritoneSettingValue {
        override val type = BaritoneSettingType.LONG
    }

    data class DecimalValue(val value: Double) : BaritoneSettingValue {
        override val type = BaritoneSettingType.DECIMAL

        init {
            require(value.isFinite()) { "Decimal setting values must be finite" }
        }
    }

    data class TextValue(val value: String) : BaritoneSettingValue {
        override val type = BaritoneSettingType.STRING
    }

    data class EnumValue(val value: String) : BaritoneSettingValue {
        override val type = BaritoneSettingType.ENUM

        init {
            require(value.isNotBlank()) { "Enum setting values cannot be blank" }
        }
    }

    class StringListValue(values: Collection<String>) : BaritoneSettingValue {
        override val type = BaritoneSettingType.STRING_LIST
        val values: List<String> = immutableListCopy(values)

        override fun equals(other: Any?): Boolean = other is StringListValue && values == other.values

        override fun hashCode(): Int = values.hashCode()

        override fun toString(): String = "StringListValue(values=$values)"
    }
}

@Suppress("LongParameterList")
class BaritoneSetting(
    val name: BaritoneSettingName,
    val type: BaritoneSettingType,
    val value: BaritoneSettingValue,
    val defaultValue: BaritoneSettingValue,
    val description: String,
    val mutable: Boolean,
    options: Collection<String> = emptyList(),
) {
    val options: List<String> = immutableListCopy(options)

    init {
        require(value.type == type) { "Setting value type ${value.type} does not match $type" }
        require(defaultValue.type == type) { "Default value type ${defaultValue.type} does not match $type" }
        require(description.isNotBlank()) { "Setting descriptions cannot be blank" }
        require(type == BaritoneSettingType.ENUM || this.options.isEmpty()) { "Only enum settings can expose options" }
        require(type != BaritoneSettingType.ENUM || enumValuesAreKnown()) { "Enum values must be listed as options" }
    }

    private fun enumValuesAreKnown(): Boolean {
        val current = (value as BaritoneSettingValue.EnumValue).value
        val default = (defaultValue as BaritoneSettingValue.EnumValue).value
        return options.isNotEmpty() && current in options && default in options
    }

    override fun equals(other: Any?): Boolean = other is BaritoneSetting &&
        name == other.name && type == other.type && value == other.value && defaultValue == other.defaultValue &&
        description == other.description && mutable == other.mutable && options == other.options

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + defaultValue.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + mutable.hashCode()
        return 31 * result + options.hashCode()
    }

    override fun toString(): String =
        "BaritoneSetting(name=$name, type=$type, value=$value, defaultValue=$defaultValue, mutable=$mutable)"
}
