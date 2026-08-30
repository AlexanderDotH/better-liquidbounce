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

import net.ccbluex.fastutil.toEnumSet
import net.ccbluex.liquidbounce.common.clientResource
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.utils.TextureMode
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawTexQuad
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.utils.AnimatedValueGroup
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.ccbluex.liquidbounce.utils.render.asTexture
import net.ccbluex.liquidbounce.utils.render.readNativeImage
import net.minecraft.world.entity.Entity
import org.joml.Quaternionf
import org.joml.Vector2f

internal class ImageTargetAppearance(
    owner: ToggleableValueGroup,
    override val parent: ModeValueGroup<*>,
) : WorldTargetAppearance("Image") {

    private val textureMode = modes("Source", 0) {
        arrayOf(
            TextureMode.Custom(it),
            TextureMode.Builtin(it, PresetTexture.MARKER1, PresetTexture.entries.toEnumSet()),
        )
    }
    private val scale by vec2f("Scale", Vector2f(1f, 1f))
    private val color by color("ColorModulator", Color4b.WHITE)
    private val rotate = tree(object : AnimatedValueGroup("Rotate") {
        override val curve = curve("Curve") {
            "Progress" x 0f..1f
            "Degrees" y -180f..180f
            points(Vector2f(0f, 0f), Vector2f(1f, 0f))
        }
    })
    private val heightMode = modes(owner, "HeightMode") {
        arrayOf(
            TargetHeightMode.Feet(it),
            TargetHeightMode.Top(it),
            TargetHeightMode.Relative(it),
            TargetHeightMode.Health(it),
            TargetHeightMode.Animated(it),
        )
    }
    private val quaternion = Quaternionf()

    override fun WorldRenderEnvironment.render(entity: Entity, partialTicks: Float) {
        val texture = textureMode.activeMode.texture ?: return
        val height = heightMode.activeMode.getHeight(entity, partialTicks)
        withPositionRelativeToCamera(entity.targetPosition(height, partialTicks)) {
            poseStack.mulPose(camera.rotation())
            poseStack.mulPose(quaternion.scaling(1f).rotateLocalZ(rotate.current().toRadians()))
            poseStack.last().scale(scale.x(), scale.y(), 1f)
            drawTexQuad(texture, color.argb)
        }
    }

    private enum class PresetTexture(override val tag: String, val path: String) : TextureMode.Builtin.Preset {
        MARKER1("Marker1", "target_renderer/target.png"),
        MARKER2("Marker2", "target_renderer/target2.png");

        override val texture = clientResource(path).readNativeImage().asTexture { "TargetRenderer Image $tag" }
    }
}
