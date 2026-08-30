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
package net.ccbluex.liquidbounce.render.target

import com.mojang.math.Axis
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawCustomMesh
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.toDegrees
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal class HeartTargetAppearance(override val parent: ModeValueGroup<*>) : WorldTargetAppearance("Hearts") {
    private val animation = TargetHeartAnimationState()
    private val color by color("Color", Color4b.WHITE.alpha(180))
    private val dynamicCount by boolean("DynamicCount", true)
    private val heartCount by int("HeartCount", 10, 1..32)
    private val yOffset by float("YOffset", 0.1f, -1f..3f)
    private val size by float("Size", 0.15f, 0.05f..1f).onChange {
        animation.layout.markDirty()
        it
    }
    private val orbit = tree(OrbitSettings())
    private val canBeCovered by boolean("CanBeCovered", false)

    override fun WorldRenderEnvironment.render(entity: Entity, partialTicks: Float) {
        val target = entity as? LivingEntity ?: return
        val slots = targetHeartSlots(target, dynamicCount, heartCount)
        animation.update(target, slots.size, size, orbit.squeezeStrength, orbit.squeezeSpeed)
        val nowSeconds = System.currentTimeMillis() / 1000.0
        val targetPosition = target.interpolateCurrentPosition(partialTicks)
        for (index in slots.indices) {
            drawHeartSlot(target, targetPosition, slots[index], animation.layout.placements[index], nowSeconds)
        }
    }

    private fun WorldRenderEnvironment.drawHeartSlot(
        target: LivingEntity,
        targetPosition: Vec3,
        slot: TargetHeartSlot,
        placement: TargetHeartPlacement,
        nowSeconds: Double,
    ) {
        val orbitAngle = (placement.baseOrbitAngle + nowSeconds * orbit.speed).toRadians()
        val orbitDistance = (orbit.radius - animation.squeezeStrength).coerceIn(0.05f, orbit.radius)
        val offset = Vec3(
            cos(orbitAngle) * orbitDistance,
            yOffset.toDouble() + target.bbHeight.toDouble() * placement.heightFactor,
            sin(orbitAngle) * orbitDistance,
        )
        val baseColor = when (slot.type) {
            TargetHeartType.HEALTH -> color
            TargetHeartType.ABSORPTION -> Color4b(255, 214, 72, color.a)
        }
        val renderColor = baseColor.interpolateTo(Color4b.RED.alpha(color.a), animation.flashStrength.toDouble())
        drawHeart(targetPosition.add(offset), targetPosition, renderColor, slot.fill)
    }

    private fun WorldRenderEnvironment.drawHeart(position: Vec3, targetPosition: Vec3, color: Color4b, fill: Float) {
        withPositionRelativeToCamera(position) {
            val direction = targetPosition.subtract(position)
            val targetYaw = atan2(direction.x, direction.z).toDegrees().toFloat()
            poseStack.mulPose(Axis.YP.rotationDegrees(targetYaw))
            drawHeartSdf(color.alpha((color.a * 0.25f).toInt()), size, fill = 1f)
            drawHeartSdf(color, size, fill)
        }
    }

    private fun WorldRenderEnvironment.drawHeartSdf(color: Color4b, size: Float, fill: Float) {
        val clampedFill = fill.coerceIn(0f, 1f)
        if (clampedFill <= 0f) return
        val halfWidth = size * 1.0938363f
        val right = -halfWidth + halfWidth * 2f * clampedFill
        drawCustomMesh(ClientRenderPipelines.heart(noDepthTest = !canBeCovered)) { pose ->
            addVertex(pose, -halfWidth, -size, 0f).setUv(0f, 0f).setColor(color.argb)
            addVertex(pose, -halfWidth, size, 0f).setUv(0f, 1f).setColor(color.argb)
            addVertex(pose, right, size, 0f).setUv(clampedFill, 1f).setColor(color.argb)
            addVertex(pose, right, -size, 0f).setUv(clampedFill, 0f).setColor(color.argb)
        }
    }

    private class OrbitSettings : ValueGroup("Orbit") {
        val radius by float("Radius", 0.5f, 0.1f..1f)
        val speed by float("Speed", 35f, -360f..360f, "deg/s")
        val squeezeStrength by float("SqueezeStrength", 0.25f, 0f..1f)
        val squeezeSpeed by int("SqueezeSpeed", 2, 1..4)
    }
}
