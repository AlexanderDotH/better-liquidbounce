/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.config.gson.serializer

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.config.types.list.MultiChoiceListValue
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.lang.LanguageManager
import net.minecraft.network.chat.Component
import java.util.Locale

internal data class ValueInteropDescriptions(
    val description: String,
    val extendedDescription: String,
)

/** Resolves hand-written help first and derives stable help from the setting contract as a fallback. */
internal object ValueInteropDescriptionResolver {

    internal val requiredTranslationKeys: Set<String>
        get() = buildSet {
            DescriptionKind.entries.forEach { kind ->
                add(kind.shortTemplate)
                add(kind.extendedTemplate)
            }
            add(RANGE_TEMPLATE)
            add(CHOICES_TEMPLATE)
            add(CHOICE_COUNT_TEMPLATE)
        }

    fun resolve(value: Value<*>): ValueInteropDescriptions {
        val generated = generatedDescriptions(value)
        return ValueInteropDescriptions(
            description = value.manualDescription() ?: generated.description,
            extendedDescription = value.manualExtendedDescription() ?: generated.extendedDescription,
        )
    }

    private fun generatedDescriptions(value: Value<*>): ValueInteropDescriptions {
        val kind = DescriptionKind.of(value)
        val setting = humanizeSettingIdentifier(value.name)
        val context = value.contextLabel()
        val details = value.contractDetails()
        val extended = buildString {
            append(localize(kind.extendedTemplate, kind.extendedFallback, setting, context))
            if (details != null) {
                append(' ')
                append(details)
            }
        }

        return ValueInteropDescriptions(
            description = localize(kind.shortTemplate, kind.shortFallback, setting, context),
            extendedDescription = extended,
        )
    }

    private fun Value<*>.manualDescription(): String? {
        val unresolvedKeys = setOfNotNull(descriptionKey, key?.let { "$it.description" })
        return runCatching { description.get()?.descriptionText() }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it !in unresolvedKeys }
    }

    private fun Value<*>.manualExtendedDescription(): String? {
        val keys = buildList {
            key?.let { add("$it.extendedDescription") }
            descriptionKey
                ?.removeSuffix(".description")
                ?.let { add("$it.extendedDescription") }
        }.distinct()
        return keys.firstNotNullOfOrNull(::localizedTranslation)
    }

    private fun Value<*>.contextLabel(): String {
        val path = key?.split('.')?.filter(String::isNotBlank).orEmpty()
        if (path.size < 2) {
            return "LiquidBounce"
        }

        val domainIndex = path.indexOf("module").takeIf { it >= 0 } ?: 0
        val context = path.drop(domainIndex + 1).dropLast(1)
        return context.joinToString(" → ", transform = ::humanizeSettingIdentifier).ifBlank { "LiquidBounce" }
    }

    private fun Value<*>.contractDetails(): String? = when (this) {
        is RangedValue<*> -> rangeDetails()
        is ChoiceListValue<*> -> choiceDetails(choices)
        is MultiChoiceListValue<*> -> choiceDetails(choices)
        else -> null
    }

    private fun RangedValue<*>.rangeDetails(): String {
        val lower = formatDescriptionBound(range.start, suffix)
        val upper = formatDescriptionBound(range.endInclusive, suffix)
        return localize(
            template = RANGE_TEMPLATE,
            fallback = "The code accepts values from %s to %s.",
            lower,
            upper,
        )
    }

    private fun choiceDetails(choices: Set<out Tagged>): String {
        if (choices.size > MAX_LISTED_CHOICES) {
            return localize(
                template = CHOICE_COUNT_TEMPLATE,
                fallback = "The code exposes %s selectable values.",
                choices.size.toString(),
            )
        }

        return localize(
            template = CHOICES_TEMPLATE,
            fallback = "Code-defined choices: %s.",
            choices.joinToString { it.tag },
        )
    }

    private fun localizedTranslation(key: String): String? {
        if (!LanguageManager.hasFallbackTranslation(key)) {
            return null
        }
        val language = runCatching(LanguageManager::getLanguage).getOrNull()
            ?: LanguageManager.getCommonLanguage()
            ?: return null
        return language.getOrDefault(key, key).trim().takeIf(String::isNotEmpty)
    }

    private fun localize(template: String, fallback: String, vararg arguments: String): String {
        val format = localizedTranslation(template) ?: fallback
        return runCatching { String.format(Locale.ROOT, format, *arguments) }
            .getOrElse { String.format(Locale.ROOT, fallback, *arguments) }
    }

    private enum class DescriptionKind(
        val key: String,
        val shortFallback: String,
        val extendedFallback: String,
    ) {
        MODE(
            "mode",
            "Uses the %s implementation in %s.",
            "Activates the code path named %s in %s. Nested settings apply only to this implementation.",
        ),
        ACTION(
            "action",
            "Runs the %s action in %s.",
            "Runs the code-backed %s action once when pressed in %s.",
        ),
        BOOLEAN(
            "boolean",
            "Controls %s in %s.",
            "Controls the %s branch in %s. Enabled and disabled select the corresponding code paths.",
        ),
        NUMBER(
            "number",
            "Sets %s in %s.",
            "Sets the numeric %s value consumed by %s.",
        ),
        NUMBER_RANGE(
            "numberRange",
            "Sets the %s range in %s.",
            "Sets the lower and upper %s bounds consumed by %s.",
        ),
        TEXT(
            "text",
            "Sets %s in %s.",
            "Provides the %s text read by %s. Validation and matching follow that module's code.",
        ),
        PLAYER(
            "player",
            "Sets the %s player in %s.",
            "Provides the player name used for %s in %s. The module resolves it against available players.",
        ),
        SELECT(
            "select",
            "Selects %s in %s.",
            "Selects one of the code-supported %s values used by %s.",
        ),
        LIST(
            "list",
            "Selects the %s entries used by %s.",
            "Controls the collection of %s entries consumed by %s. " +
                "Only entries supported by the setting type are accepted.",
        ),
        BIND(
            "bind",
            "Sets the %s input binding in %s.",
            "Chooses the key or mouse input and activation behavior for %s in %s.",
        ),
        KEY(
            "key",
            "Sets the %s key in %s.",
            "Chooses the keyboard or mouse key read as %s by %s.",
        ),
        FILE(
            "file",
            "Selects %s in %s.",
            "Selects the file or folder read as %s by %s. The picker enforces the setting's declared file mode.",
        ),
        COLOR(
            "color",
            "Sets the %s color in %s.",
            "Sets the color, opacity, and available color channels used for %s in %s.",
        ),
        VECTOR(
            "vector",
            "Sets the %s coordinates in %s.",
            "Sets the coordinate components consumed as %s by %s.",
        ),
        CURVE(
            "curve",
            "Sets the %s curve in %s.",
            "Edits the control points and interpolation curve used as %s by %s.",
        ),
        CONFIGURABLE(
            "configurable",
            "Groups the %s options used by %s.",
            "Groups the code-backed settings for %s in %s. Expand it to configure that part of the module.",
        ),
        TOGGLEABLE(
            "toggleable",
            "Enables or disables %s in %s.",
            "Enables or disables the %s feature branch in %s. Its nested settings apply while that branch is enabled.",
        ),
        CHOICE(
            "choice",
            "Selects the %s implementation in %s.",
            "Selects exactly one code implementation for %s in %s. " +
                "The selected implementation exposes its own nested settings.",
        ),
        MERCHANT(
            "merchant",
            "Configures %s in %s.",
            "Configures the merchant-specific %s rules consumed by %s.",
        ),
        GENERIC(
            "generic",
            "Configures %s in %s.",
            "Configures the code-backed %s value used by %s.",
        );

        val shortTemplate: String
            get() = "$TEMPLATE_PREFIX.$key.short"
        val extendedTemplate: String
            get() = "$TEMPLATE_PREFIX.$key.extended"

        companion object {
            fun of(value: Value<*>): DescriptionKind {
                if (value is Mode) {
                    return MODE
                }
                if (value.get() is Collection<*> && value.valueType !in GROUP_VALUE_TYPES) {
                    return LIST
                }

                return when (value.valueType) {
                    ValueType.ACTION -> ACTION
                    ValueType.BOOLEAN -> BOOLEAN
                    ValueType.FLOAT, ValueType.INT -> NUMBER
                    ValueType.FLOAT_RANGE, ValueType.INT_RANGE -> NUMBER_RANGE
                    ValueType.TEXT -> TEXT
                    ValueType.PLAYER -> PLAYER
                    ValueType.BLOCK, ValueType.ITEM, ValueType.ENCHANTMENT,
                    ValueType.SOUND_EVENT, ValueType.MOB_EFFECT, ValueType.MENU,
                    ValueType.ENTITY_TYPE, ValueType.C2S_PACKET, ValueType.S2C_PACKET,
                    ValueType.CLIENT_MODULE, ValueType.CHOOSE, ValueType.FRIEND,
                    ValueType.PROXY, ValueType.ACCOUNT, ValueType.SUBSCRIBED_ITEM -> SELECT
                    ValueType.LIST, ValueType.MULTI_CHOOSE, ValueType.MUTABLE_LIST,
                    ValueType.NAMED_ITEM_LIST, ValueType.REGISTRY_LIST,
                    ValueType.REGISTRY_MUTABLE_LIST -> LIST
                    ValueType.BIND -> BIND
                    ValueType.KEY -> KEY
                    ValueType.FILE -> FILE
                    ValueType.COLOR -> COLOR
                    ValueType.VECTOR3_I, ValueType.VECTOR3_D, ValueType.VECTOR2_F -> VECTOR
                    ValueType.CURVE -> CURVE
                    ValueType.CONFIGURABLE -> CONFIGURABLE
                    ValueType.TOGGLEABLE -> TOGGLEABLE
                    ValueType.CHOICE -> CHOICE
                    ValueType.INVALID -> GENERIC
                }
            }
        }
    }

    private const val TEMPLATE_PREFIX = "liquidbounce.common.generatedDescription"
    private const val RANGE_TEMPLATE = "$TEMPLATE_PREFIX.range"
    private const val CHOICES_TEMPLATE = "$TEMPLATE_PREFIX.choices"
    private const val CHOICE_COUNT_TEMPLATE = "$TEMPLATE_PREFIX.choiceCount"
    private const val MAX_LISTED_CHOICES = 8
    private val GROUP_VALUE_TYPES = setOf(ValueType.CONFIGURABLE, ValueType.TOGGLEABLE, ValueType.CHOICE)
}

private val LOWER_TO_UPPER = Regex("([a-z\\d])([A-Z])")
private val ACRONYM_TO_WORD = Regex("([A-Z]+)([A-Z][a-z])")

private fun formatDescriptionBound(bound: Any?, suffix: String): String = "$bound$suffix".trim()

private fun humanizeSettingIdentifier(identifier: String): String = identifier
    .replace('_', ' ')
    .replace(LOWER_TO_UPPER, "$1 $2")
    .replace(ACRONYM_TO_WORD, "$1 $2")
    .trim()
    .replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase(Locale.ROOT) else character.toString()
    }

private fun Any.descriptionText(): String = when (this) {
    is Component -> string
    else -> toString()
}
