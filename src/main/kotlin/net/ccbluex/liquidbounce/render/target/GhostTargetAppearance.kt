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

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.ccbluex.liquidbounce.common.clientResource
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.render.AnchorPoint
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawSquareTexture
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.clientStartDurationMs
import net.ccbluex.liquidbounce.utils.entity.lastRenderPos
import net.ccbluex.liquidbounce.utils.math.minus
import net.ccbluex.liquidbounce.utils.render.asTexture
import net.ccbluex.liquidbounce.utils.render.readNativeImage
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

internal class GhostTargetAppearance(override val parent: ModeValueGroup<*>) : WorldTargetAppearance("Ghost") {
    private val color by color("Color", Color4b.BLUE)
    private val size by float("Size", 0.5f, 0.4f..0.7f)
    private val length by int("Length", 25, 15..40)

    override fun WorldRenderEnvironment.render(entity: Entity, partialTicks: Float) {
        poseStack.pushPose()
        val interpolated = entity.lastRenderPos().lerp(entity.position(), partialTicks.toDouble()).add(0.2, 1.25, 0.0)
        poseStack.translate(interpolated - camera.position())
        drawParticle({ sin, cos -> Vec3(sin, cos, -cos) }, { sin, cos -> Vec3(-sin, -cos, cos) })
        drawParticle({ sin, cos -> Vec3(-sin, sin, -cos) }, { sin, cos -> Vec3(sin, -sin, cos) })
        drawParticle({ sin, cos -> Vec3(-sin, -sin, cos) }, { sin, cos -> Vec3(sin, sin, -cos) })
        poseStack.popPose()
    }

    private inline fun WorldRenderEnvironment.drawParticle(
        translationsBefore: PoseStack.(Double, Double) -> Vec3,
        translateAfter: PoseStack.(Double, Double) -> Vec3,
    ) {
        val radius = 0.67
        val distance = 10.0 + length * 0.2
        for (index in 0..<length) {
            val angle = 0.15f * (clientStartDurationMs - index * distance) / 30
            val sin = sin(angle) * radius
            val cos = cos(angle) * radius
            orientParticle(translationsBefore(poseStack, sin, cos))
            val alpha = Mth.clamp(color.a - index * 15, 0, color.a)
            drawSquareTexture(ghostTexture, size, color.alpha(alpha).argb, AnchorPoint.CENTER_LEFT)
            restoreOrientation(translateAfter(poseStack, sin, cos))
        }
    }

    private fun WorldRenderEnvironment.orientParticle(translation: Vec3) = with(poseStack) {
        translate(translation)
        translate(-size / 2.0, -size / 2.0, 0.0)
        mulPose(Axis.YP.rotationDegrees(-camera.yRot()))
        mulPose(Axis.XP.rotationDegrees(camera.xRot()))
        translate(size / 2.0, size / 2.0, 0.0)
    }

    private fun WorldRenderEnvironment.restoreOrientation(translation: Vec3) = with(poseStack) {
        translate(-size / 2.0, -size / 2.0, 0.0)
        mulPose(Axis.XP.rotationDegrees(-camera.xRot()))
        mulPose(Axis.YP.rotationDegrees(camera.yRot()))
        translate(size / 2.0, size / 2.0, 0.0)
        translate(translation)
    }
}

private val ghostTexture by lazy {
    clientResource("particles/glow.png").readNativeImage().asTexture { "TargetRenderer Ghost" }
}
