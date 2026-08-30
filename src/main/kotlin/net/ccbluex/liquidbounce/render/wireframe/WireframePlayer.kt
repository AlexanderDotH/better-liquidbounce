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
@file:JvmName("WireframePlayerKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.wireframe

import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.minecraft.util.Mth
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf

class WireframePlayer {
    var pos: Vec3 = Vec3.ZERO
    var yRot: Float = 0F
    var xRot: Float = 0F
    var pose: Pose = Pose.STANDING
    var swimAmount: Float = 0F

    private val quaternion = Quaternionf()

    fun render(event: WorldRenderEvent, color: Color4b, outlineColor: Color4b, noDepthTest: Boolean = true) {
        event.renderEnvironment {
            render(color, outlineColor, noDepthTest)
        }
    }

    context(env: WorldRenderEnvironment)
    fun render(color: Color4b, outlineColor: Color4b, noDepthTest: Boolean = true) {
        env.withPositionRelativeToCamera(pos) {
            poseStack.withPush {
                val bodyYaw = -Mth.wrapDegrees(yRot)
                poseStack.mulPose(quaternion.identity().rotationY(bodyYaw.toRadians()))
                poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE)

                when (pose) {
                    Pose.CROUCHING -> renderCrouching(color, outlineColor, noDepthTest)
                    Pose.SWIMMING -> renderSwimming(color, outlineColor, noDepthTest = noDepthTest)
                    else -> renderStanding(color, outlineColor, noDepthTest)
                }
            }
        }
    }

    fun setRotation(rotation: Rotation) {
        this.xRot = rotation.xRot
        this.yRot = rotation.yRot
    }

    fun setPosRot(x: Double, y: Double, z: Double, yRot: Float, xRot: Float) {
        this.pos = Vec3(x, y, z)
        this.yRot = yRot
        this.xRot = xRot
    }

    private fun WorldRenderEnvironment.renderStanding(
        color: Color4b,
        outlineColor: Color4b,
        noDepthTest: Boolean = true,
    ) {
        renderPart(RENDER_LEFT_LEG, color, outlineColor, noDepthTest = noDepthTest)
        renderPart(RENDER_RIGHT_LEG, color, outlineColor, noDepthTest = noDepthTest)
        renderPart(RENDER_BODY, color, outlineColor, noDepthTest = noDepthTest)
        renderPart(RENDER_LEFT_ARM, color, outlineColor, noDepthTest = noDepthTest)
        renderPart(RENDER_RIGHT_ARM, color, outlineColor, noDepthTest = noDepthTest)
        renderPart(
            box = RENDER_HEAD,
            color = color,
            outlineColor = outlineColor,
            pivot = RENDER_HEAD.bottomCenter,
            xRot = xRot,
            noDepthTest = noDepthTest,
        )
    }

    private fun WorldRenderEnvironment.renderCrouching(
        color: Color4b,
        outlineColor: Color4b,
        noDepthTest: Boolean = true,
    ) {
        renderPart(CROUCH_LEFT_LEG, color, outlineColor, noDepthTest = noDepthTest)
        renderPart(CROUCH_RIGHT_LEG, color, outlineColor, noDepthTest = noDepthTest)
        renderPart(
            box = CROUCH_BODY,
            color = color,
            outlineColor = outlineColor,
            pivot = CROUCH_BODY.bottomCenter,
            xRot = CROUCH_BODY_ROTATION,
            noDepthTest = noDepthTest,
        )
        renderPart(
            box = CROUCH_LEFT_ARM,
            color = color,
            outlineColor = outlineColor,
            pivot = CROUCH_LEFT_ARM.bottomCenter,
            xRot = CROUCH_ARM_ROTATION,
            noDepthTest = noDepthTest,
        )
        renderPart(
            box = CROUCH_RIGHT_ARM,
            color = color,
            outlineColor = outlineColor,
            pivot = CROUCH_RIGHT_ARM.bottomCenter,
            xRot = CROUCH_ARM_ROTATION,
            noDepthTest = noDepthTest,
        )
        renderPart(
            box = CROUCH_HEAD,
            color = color,
            outlineColor = outlineColor,
            pivot = CROUCH_HEAD.bottomCenter,
            xRot = xRot,
            noDepthTest = noDepthTest,
        )
    }

    private fun WorldRenderEnvironment.renderSwimming(
        color: Color4b,
        outlineColor: Color4b,
        noDepthTest: Boolean = true,
    ) {
        val headRotation = swimmingWireframeHeadRotation(xRot, swimAmount)

        poseStack.withPush {
            poseStack.translate(RENDER_BODY.center.x, RENDER_BODY.center.y + SWIM_ROOT_Y_OFFSET, RENDER_BODY.center.z)
            poseStack.mulPose(quaternion.identity().rotationX(SWIM_PART_ROTATION.toRadians()))
            poseStack.translate(-RENDER_BODY.center.x, -RENDER_BODY.center.y, -RENDER_BODY.center.z)

            forEachSwimmingWireframePart(headRotation) { box, pivot, partXRot, partYRot, partZRot ->
                renderPart(
                    box = box,
                    color = color,
                    outlineColor = outlineColor,
                    pivot = pivot,
                    xRot = partXRot,
                    yRot = partYRot,
                    zRot = partZRot,
                    noDepthTest = noDepthTest,
                )
            }
        }
    }

    private fun WorldRenderEnvironment.renderPart(
        box: AABB,
        color: Color4b,
        outlineColor: Color4b,
        pivot: Vec3 = box.center,
        xRot: Float = 0f,
        yRot: Float = 0f,
        zRot: Float = 0f,
        noDepthTest: Boolean = true,
    ) {
        poseStack.withPush {
            if (xRot != 0f || yRot != 0f || zRot != 0f) {
                poseStack.translate(pivot.x, pivot.y, pivot.z)
                if (zRot != 0f) {
                    poseStack.mulPose(quaternion.identity().rotationZ(zRot.toRadians()))
                }
                if (yRot != 0f) {
                    poseStack.mulPose(quaternion.identity().rotationY(yRot.toRadians()))
                }
                if (xRot != 0f) {
                    poseStack.mulPose(quaternion.identity().rotationX(xRot.toRadians()))
                }
                poseStack.translate(-pivot.x, -pivot.y, -pivot.z)
            }

            drawBox(box, color, outlineColor, noDepthTest = noDepthTest)
        }
    }

}
