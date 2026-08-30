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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.AStarPathBuilder
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.render.target.TargetRenderer
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Optional Reach feature that delegates execution to a stateful runtime.
 */
class ReachHit(
    parent: EventListener?,
    includeTargetRenderer: Boolean = Minecraft.getInstance() != null,
) : ToggleableValueGroup(parent, "Hit", enabled = false), AStarPathBuilder {

    internal val modeConfiguration = ReachHitModeConfiguration(this)
    internal val modeChoice = tree(modeConfiguration.choice)
    internal val maxRange by float("MaxRange", 100f, 10f..150f)
    internal val minRange by float("MinRange", 3.0f, 0f..6f)
    internal val attackRange by float("AttackRange", 4.2f, 3f..5f)
    internal val tracers by boolean("Tracers", false)
    internal val runtime = ReachHitRuntime(this)

    override val allowDiagonal: Boolean
        get() = modeConfiguration.aStar.diagonal

    var desyncPlayerPosition: Vec3?
        get() = runtime.desyncPlayerPosition
        private set(value) {
            runtime.desyncPlayerPosition = value
        }

    var hoverTarget: LivingEntity?
        get() = runtime.hoverTarget
        private set(value) {
            runtime.hoverTarget = value
        }

    init {
        if (includeTargetRenderer) {
            tree(TargetRenderer(this) { hoverTarget })
        }
    }

    @Suppress("unused")
    private val hoverHandler = handler<GameTickEvent> {
        runtime.updateHover()
    }

    @Suppress("unused")
    private val attackKeyHandler = handler<KeybindIsPressedEvent> { event ->
        runtime.interceptAttackKey(event)
    }

    @Suppress("unused")
    private val attackHandler = tickHandler {
        runtime.tickAttack()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> {
        runtime.handlePacket(it.packet)
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        runtime.renderTracer(event)
    }

    override fun onDisabled() {
        runtime.disable()
        super.onDisabled()
    }

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacyReachHitConfig(jsonObject)
    }

    internal val maximumTargetRange: Float
        get() = maxRange

    internal fun isTargetInConfiguredRange(target: LivingEntity): Boolean =
        isWithinReachHitTargetRange(
            distanceSquared = player.squaredBoxedDistanceTo(target),
            minRange = minRange,
            maxRange = maxRange,
        )

    internal suspend fun tryAttack(
        target: LivingEntity,
        rotation: Rotation,
        keepSprint: Boolean,
        automatedByKillAura: Boolean = false,
    ): Boolean = runtime.tryAttack(target, rotation, keepSprint, automatedByKillAura)
}
