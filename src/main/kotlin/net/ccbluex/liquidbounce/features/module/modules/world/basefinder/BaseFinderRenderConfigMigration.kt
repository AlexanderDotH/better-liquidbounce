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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Folds flat GlowBox ESP / label settings into Render.Mode.Glow and Render.Labels,
 * and promotes flat BoxMode enum + size settings into Render.BoxMode choices.
 */
internal fun migrateLegacyBaseFinderRenderConfig(jsonObject: JsonObject) {
    val storedValues = jsonObject["value"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val storedMode = firstNamedJsonObject(storedValues, "Mode")
    val storedBoxMode = firstNamedJsonObject(storedValues, "BoxMode")
    val hasModernMode = storedMode?.has("choices") == true
    val hasModernBoxMode = storedBoxMode?.has("choices") == true
    val hasLabelsGroup = firstNamedJsonObject(storedValues, "Labels") != null
    if (hasModernMode && hasLabelsGroup && hasModernBoxMode) return

    val buckets = partitionLegacyBaseFinderRenderValues(storedValues, hasModernBoxMode)
    appendMigratedBaseFinderRenderGroups(
        retainedValues = buckets.retainedValues,
        storedMode = storedMode,
        storedBoxMode = storedBoxMode,
        hasModernMode = hasModernMode,
        hasModernBoxMode = hasModernBoxMode,
        hasLabelsGroup = hasLabelsGroup,
        glowValues = buckets.glowValues,
        labelValues = buckets.labelValues,
        fixedBoxValues = buckets.fixedBoxValues,
        dynamicBoxValues = buckets.dynamicBoxValues,
    )
    jsonObject.add("value", buckets.retainedValues)
}

private fun firstNamedJsonObject(values: JsonArray, name: String): JsonObject? =
    values.filter { it.isJsonObject }
        .map { it.asJsonObject }
        .firstOrNull { it["name"]?.asString == name }

private data class LegacyBaseFinderRenderBuckets(
    val glowValues: JsonArray,
    val labelValues: JsonArray,
    val fixedBoxValues: JsonArray,
    val dynamicBoxValues: JsonArray,
    val retainedValues: JsonArray,
)

private fun partitionLegacyBaseFinderRenderValues(
    storedValues: JsonArray,
    hasModernBoxMode: Boolean,
): LegacyBaseFinderRenderBuckets {
    val glowValues = JsonArray()
    val labelValues = JsonArray()
    val fixedBoxValues = JsonArray()
    val dynamicBoxValues = JsonArray()
    val retainedValues = JsonArray()

    for (storedValue in storedValues) {
        if (!storedValue.isJsonObject) {
            retainedValues.add(storedValue.deepCopy())
            continue
        }
        val setting = storedValue.asJsonObject
        when (setting["name"]?.asString) {
            "Mode", "Pulse", "Labels", "BoxMode" -> Unit
            "BoxRadius", "BoxHeight" -> if (!hasModernBoxMode) fixedBoxValues.add(setting.deepCopy())
            "DynamicPadding" -> if (!hasModernBoxMode) dynamicBoxValues.add(setting.deepCopy())
            in BASE_FINDER_GLOW_STYLE_SETTING_NAMES -> glowValues.add(setting.deepCopy())
            in BASE_FINDER_LABEL_SETTING_NAMES -> labelValues.add(setting.deepCopy())
            else -> retainedValues.add(setting.deepCopy())
        }
    }
    return LegacyBaseFinderRenderBuckets(
        glowValues = glowValues,
        labelValues = labelValues,
        fixedBoxValues = fixedBoxValues,
        dynamicBoxValues = dynamicBoxValues,
        retainedValues = retainedValues,
    )
}

private fun appendMigratedBaseFinderRenderGroups(
    retainedValues: JsonArray,
    storedMode: JsonObject?,
    storedBoxMode: JsonObject?,
    hasModernMode: Boolean,
    hasModernBoxMode: Boolean,
    hasLabelsGroup: Boolean,
    glowValues: JsonArray,
    labelValues: JsonArray,
    fixedBoxValues: JsonArray,
    dynamicBoxValues: JsonArray,
) {
    retainedValues.add(
        if (hasModernMode) {
            storedMode!!.deepCopy()
        } else {
            baseFinderRenderModeValue(canonicalBaseFinderRenderModeName(storedMode), glowValues)
        },
    )
    retainedValues.add(
        if (hasModernBoxMode) {
            storedBoxMode!!.deepCopy()
        } else {
            baseFinderBoxModeValue(
                canonicalBaseFinderBoxModeName(
                    storedBoxMode?.get("active")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: storedBoxMode?.get("value")?.takeIf { it.isJsonPrimitive }?.asString,
                ),
                fixedBoxValues,
                dynamicBoxValues,
            )
        },
    )
    if (!hasLabelsGroup) {
        retainedValues.add(
            JsonObject().apply {
                addProperty("name", "Labels")
                add("value", labelValues)
            },
        )
    }
}

private fun canonicalBaseFinderRenderModeName(storedMode: JsonObject?): String = when {
    storedMode?.get("value")?.asString.equals("Box", ignoreCase = true) -> "Box"
    storedMode?.get("active")?.asString.equals("Box", ignoreCase = true) -> "Box"
    else -> "Glow"
}

private fun canonicalBaseFinderBoxModeName(storedName: String?): String = when {
    storedName?.equals("Dynamic", ignoreCase = true) == true ||
        storedName?.equals("Dynamic box", ignoreCase = true) == true -> "Dynamic"
    else -> "Fixed"
}

private fun baseFinderBoxModeValue(
    activeMode: String,
    fixedValues: JsonArray,
    dynamicValues: JsonArray,
) = JsonObject().apply {
    addProperty("name", "BoxMode")
    addProperty("active", activeMode)
    add("value", JsonArray())
    add(
        "choices",
        JsonObject().apply {
            add("Fixed", JsonObject().apply {
                addProperty("name", "Fixed")
                add("value", fixedValues)
            })
            add("Dynamic", JsonObject().apply {
                addProperty("name", "Dynamic")
                add("value", dynamicValues)
            })
        },
    )
}

private fun baseFinderRenderModeValue(activeMode: String, glowValues: JsonArray) =
    JsonObject().apply {
        addProperty("name", "Mode")
        addProperty("active", activeMode)
        add("value", JsonArray())
        add(
            "choices",
            JsonObject().apply {
                add("Glow", JsonObject().apply {
                    addProperty("name", "Glow")
                    add("value", glowValues)
                })
                add("Box", JsonObject().apply {
                    addProperty("name", "Box")
                    add("value", JsonArray())
                })
            },
        )
    }

private val BASE_FINDER_GLOW_STYLE_SETTING_NAMES = setOf(
    "Radius",
    "Softness",
    "Intensity",
    "CoreSize",
    "Opacity",
)

private val BASE_FINDER_LABEL_SETTING_NAMES = setOf(
    "ShowLabels",
    "MaxLabels",
    "LabelText",
    "LabelScale",
    "ShowEvidenceDetails",
    "MaxEvidenceDetails",
)
