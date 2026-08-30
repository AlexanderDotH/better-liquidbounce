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
package net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.client.clientLogger
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.math.firstHit
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.ccbluex.liquidbounce.features.range.RangeValueGroup
import kotlin.math.max

/**
 * Allows adjusting your attack range and scan range.
 */
object KillAuraRange : RangeValueGroup("Range", 1f, 3f), MinecraftShortcuts {

    private val logger = clientLogger("KillAuraRange")

    internal val scanRange
        get() = maxOf(interactionRange, interactionThroughWallsRange) + currentScanRangeAddition

    private var scanRangeIncrease by floatRange(
        "ScanRangeIncrease",
        2.0f..3.0f,
        0.0f..7.0f,
        "blocks"
    ).onChanged { range ->
        currentScanRangeAddition = range.random()
    }
    private var currentScanRangeAddition: Float = scanRangeIncrease.random()

    fun update() {
        currentScanRangeAddition = scanRangeIncrease.random()
    }

    /**
     * Migrates the old values from the config.
     *
     * todo: remove this when no one uses the format anymore
     */
    fun migrateFromValues(map: Map<String, List<JsonObject>>) {
        if (!map.containsKey("WallRange") || !map.containsKey("ScanExtraRange")) {
            // This cannot be an old format.
            return
        }

        this.maxRangeIncrease = max(0f, withDummy("Range", map["Range"]!!.single(), 4.2f) - 3f)
        this.throughWallsRange = withDummy("WallRange", map["WallRange"]!!.single(), 3f)
        this.scanRangeIncrease = withDummy("ScanExtraRange", map["ScanExtraRange"]!!.single(), 2f..3f)
        reportKillAuraRangeMigration { message -> logger.info(message) }
    }

    private fun <T : Any> withDummy(name: String, jsonObject: JsonObject, value: T): T {
        val dummy = Value(name, defaultValue = value, valueType = ValueType.INVALID)
        ConfigSystem.deserializeValue(dummy, jsonObject)
        return dummy.get()
    }

}

internal fun reportKillAuraRangeMigration(report: (String) -> Unit) {
    report("KillAura Range Config migrated from old format.")
}

/** Checks whether a target can threaten the local player while AutoBlock is available. */
internal object KillAuraBlockDanger : ToggleableValueGroup(KillAuraAutoBlock, "OnlyWhenInDanger", false) {
    private val tolerance by float("Tolerance", 0.3f, 0f..1f, "blocks")
    private val forceActiveRange by floatRange("ForceActiveRange", 0f..1f, 0f..6f)

    fun isInDanger(): Boolean = enabled && ModuleKillAura.targetTracker.targets().any { target ->
        val interactionRange = ModuleKillAura.range.interactionRange.toDouble()
        if (player.squaredBoxedDistanceTo(target) > interactionRange.sq()) return@any false

        val eyes = target.eyePosition
        val lookEnd = eyes.add(target.rotation.directionVector.scale(interactionRange))
        val toleratedBox = player.box.inflate(tolerance.toDouble())
        val hitPosition = toleratedBox.firstHit(eyes, lookEnd) ?: return@any false
        val distance = eyes.distanceTo(hitPosition)

        distance in forceActiveRange ||
            distance <= ModuleKillAura.range.interactionThroughWallsRange ||
            distance <= interactionRange && hasLineOfSight(eyes, hitPosition, target)
    }
}
