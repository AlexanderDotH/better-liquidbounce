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
package net.ccbluex.liquidbounce.features.module.modules.render

import com.mojang.blaze3d.vertex.PoseStack
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.GameRenderEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.playermodel.PlayerModelRenderStateApplier
import net.ccbluex.liquidbounce.features.module.modules.render.playermodel.ServerPlayerModelStateTracker
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.drawLine
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.math.toVec3f
import net.ccbluex.liquidbounce.utils.render.isCustom
import net.ccbluex.liquidbounce.utils.render.scaleLightCoords
import net.minecraft.client.CameraType
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.world.phys.AABB

/**
 * Renders the last local-player state that reached the connection send path.
 */
object ModulePlayerModel : ClientModule(
    "PlayerModel",
    ModuleCategories.RENDER,
    aliases = listOf("Rotations"),
) {

    private val bodyPart by multiEnumChoice("BodyPart", BodyPart.entries)
    private val smooth by float("Smooth", 0.0f, 0.0f..0.3f)
    private val vectorLine by color("VectorLine", Color4b.WHITE.with(a = 0))
    private val vectorDot by color("VectorDot", Color4b(0x00, 0x80, 0xFF, 0x00))

    private val display by enumChoice("Display", Display.REPLACE)
    private val states by multiEnumChoice("States", State.entries)

    val outlineColor by color("OutlineColor", Color4b(36, 32, 147, 160))
        .doNotIncludeWhen { display != Display.GHOST }
    val lightPercent by int("LightPercent", 60, 0..100, "%")
        .doNotIncludeWhen { display != Display.GHOST }
    val showInFirstPerson by boolean("ShowInFirstPerson", false)
        .doNotIncludeWhen { display != Display.GHOST }

    @Suppress("unused")
    enum class BodyPart(override val tag: String) : Tagged {
        HEAD("Head"),
        BODY("Body"),
    }

    enum class Display(override val tag: String) : Tagged {
        REPLACE("Replace"),
        GHOST("Ghost"),
    }

    enum class State(override val tag: String) : Tagged {
        POSITION("Position"),
        ROTATION("Rotation"),
        POSE("Pose"),
        MOVEMENT("Movement"),
        HELD_ITEM("HeldItem"),
        ACTIONS("Actions"),
    }

    val displayMode: Display
        get() = display

    fun isPartAllowed(part: BodyPart) = part in bodyPart

    fun isStateEnabled(state: State) = state in states

    var modelRotation: Rotation? = null
        get() = if (running) field else null
        private set

    var prevModelRotation: Rotation? = null
        private set

    fun interpolatedModelRotation(partialTicks: Float): Rotation? {
        val current = modelRotation ?: return null
        return (prevModelRotation ?: current).interpolateTo(current, partialTicks)
    }

    @Suppress("unused")
    private val modelUpdater = handler<GameTickEvent>(priority = EventPriorityConvention.READ_FINAL_STATE) {
        val snapshot = ServerPlayerModelStateTracker.snapshot
        val current = snapshot.rotation

        if (!snapshot.isInitialized || current == null) {
            prevModelRotation = modelRotation
            modelRotation = null
            return@handler
        }

        val previous = modelRotation ?: snapshot.previousRotation ?: current
        val next = if (smooth > 0f) {
            previous.interpolateTo(current, 1f - smooth)
        } else {
            current
        }

        prevModelRotation = modelRotation ?: next
        modelRotation = next
    }

    @Suppress("unused")
    private val vectorRenderHandler = handler<WorldRenderEvent> { event ->
        val currentRotation = modelRotation ?: return@handler
        val previousRotation = prevModelRotation ?: currentRotation
        val drawVectorLine = vectorLine.a > 0
        val drawVectorDot = vectorDot.a > 0

        if (!drawVectorLine && !drawVectorDot) {
            return@handler
        }

        val interpolatedRotationVec = previousRotation.directionVector
            .lerp(currentRotation.directionVector, event.partialTicks.toDouble())
            .toVec3f()
        val eyeVector = Vec3f.eyeVector(event.camera)

        event.renderEnvironment {
            val vector = eyeVector.fma(100f, interpolatedRotationVec)
            if (drawVectorLine) {
                drawLine(eyeVector, vector, vectorLine.argb)
            }
            if (drawVectorDot) {
                drawBox(AABB.ofSize(vector.toVec3d(), 2.5, 2.5, 2.5), vectorDot)
            }
        }
    }

    private val ghostPoseStack = PoseStack()

    @Suppress("unused")
    private val ghostRenderHandler = handler<GameRenderEvent> {
        if (display != Display.GHOST || !showInFirstPerson && mc.options.cameraType == CameraType.FIRST_PERSON) {
            return@handler
        }

        val player = mc.player ?: return@handler
        if (!ServerPlayerModelStateTracker.snapshot.isInitialized) {
            return@handler
        }

        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val renderer = mc.entityRenderDispatcher.getRenderer(player)
        val state = renderer.createRenderState(player, partialTicks) as? AvatarRenderState ?: return@handler

        state.isCustom = true
        state.nameTag = null
        state.scoreText = null
        state.nameTagAttachment = null
        if (!outlineColor.isTransparent) {
            state.outlineColor = outlineColor.argb
        }
        state.scaleLightCoords(lightPercent * 0.01f)
        PlayerModelRenderStateApplier.apply(player, state, partialTicks)

        val cameraState = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
        mc.entityRenderDispatcher.submit(
            state,
            cameraState,
            state.x - cameraState.pos.x,
            state.y - cameraState.pos.y,
            state.z - cameraState.pos.z,
            ghostPoseStack,
            mc.levelRenderer.submitNodeStorage,
        )
    }

    override fun onDisabled() {
        modelRotation = null
        prevModelRotation = null
        super.onDisabled()
    }
}
