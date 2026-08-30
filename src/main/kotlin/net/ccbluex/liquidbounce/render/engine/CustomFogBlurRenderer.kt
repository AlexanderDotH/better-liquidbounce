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

package net.ccbluex.liquidbounce.render.engine

import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsDepthTexture
import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsDepthTextureProvider
import net.ccbluex.liquidbounce.render.engine.distanthorizons.DistantHorizonsFogDistanceMapping
import net.ccbluex.liquidbounce.render.engine.distanthorizons.distantHorizonsFogSaturationDistance

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.buffers.CachedUniform
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.render.engine.esp.IrisPipelineBypass
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.inGame
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix4f
import org.joml.Matrix4fc

/** Full-resolution, depth-aware fog blur shared by Vanilla and Distant Horizons terrain. */
object CustomFogBlurRenderer : EventListener {

    private val intermediateTarget = LazyRenderTargetHolder("LiquidBounce Fog Blur", useDepth = false)
    private val colorSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
    private val depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
    private val horizontalBlurData = createFogBlurData()
    private val verticalBlurData = createFogBlurData()

    private fun createFogBlurData() = CachedUniform<FogBlurUniform>(ClientUniformDefine.FOG_BLUR) { value ->
        putMat4f(value.inverseProjection)
        putMat4f(value.inverseViewRotation)
        putMat4f(value.dhInverseMvmProjection)
        putVec4(
            value.ranges.environmentalStart,
            value.ranges.environmentalEnd,
            value.ranges.renderDistanceStart,
            value.ranges.renderDistanceEnd,
        )
        putVec4(value.directionX, value.directionY, value.kernel.centerWeight, 0f)
        putVec4(
            MC_CLEAR_DEPTH,
            value.dhClearDepth ?: NO_DH_DEPTH,
            if (value.zeroToOneDepth) 1f else 0f,
            if (value.dhZeroToOneDepth) 1f else 0f,
        )
        putVec4(
            value.dhDistanceMapping.scale,
            value.dhDistanceMapping.offset,
            value.dhDistanceMapping.maxDistance,
            0f,
        )
        for (pair in value.kernel.pairs) {
            putVec4(pair.offset, pair.weight, 0f, 0f)
        }
    }

    @JvmStatic
    fun render(target: RenderTarget, cameraState: CameraRenderState, projectionMatrix: Matrix4fc) {
        if (!inGame || !CustomFogRenderBridge.activity().shouldRenderBlur ||
            target.width <= 0 || target.height <= 0
        ) return

        val sceneTexture = target.colorTextureView ?: return
        val depthTexture = target.depthTextureView ?: return
        val distantHorizonsDepth = DistantHorizonsDepthTextureProvider.resolve(target.width, target.height)
        val dhDepthTexture = distantHorizonsDepth?.textureView ?: depthTexture
        val fogData = cameraState.fogData.also(CustomFogRenderBridge::modifyFogData)
        val frame = FogBlurFrame.snapshot(cameraState, projectionMatrix, fogData, distantHorizonsDepth)
        val intermediate = intermediateTarget.initAndGet(target.width, target.height)

        IrisPipelineBypass.run {
            val horizontal = horizontalBlurData.get(frame.uniform(1f / target.width, 0f))
            intermediate.createRenderPass({ "LiquidBounce fog blur horizontal" }).use { pass ->
                pass.setPipeline(ClientRenderPipelines.FogBlurHorizontal)
                pass.bindTexture("SceneSampler", sceneTexture, colorSampler)
                pass.bindTexture("DepthSampler", depthTexture, depthSampler)
                pass.bindTexture("DhDepthSampler", dhDepthTexture, depthSampler)
                pass.setUniform(ClientUniformDefine.FOG_BLUR.uboName, horizontal)
                pass.draw(3, 1, 0, 0)
            }

            val vertical = verticalBlurData.get(frame.uniform(0f, 1f / target.height))
            target.createRenderPass(
                { "LiquidBounce fog blur composite" },
                useDepthAttachment = false,
            ).use { pass ->
                pass.setPipeline(ClientRenderPipelines.FogBlurComposite)
                pass.bindTexture("BlurSampler", intermediate.colorTextureView, colorSampler)
                pass.bindTexture("DepthSampler", depthTexture, depthSampler)
                pass.bindTexture("DhDepthSampler", dhDepthTexture, depthSampler)
                pass.setUniform(ClientUniformDefine.FOG_BLUR.uboName, vertical)
                pass.draw(3, 1, 0, 0)
            }
        }
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        intermediateTarget.close()
        horizontalBlurData.close()
        verticalBlurData.close()
    }

    private const val MC_CLEAR_DEPTH = 0f
    private const val NO_DH_DEPTH = -1f
}

private data class FogBlurFrame(
    val inverseProjection: Matrix4f,
    val inverseViewRotation: Matrix4f,
    val dhInverseMvmProjection: Matrix4f,
    val ranges: FogBlurRanges,
    val kernel: GaussianKernel,
    val zeroToOneDepth: Boolean,
    val dhClearDepth: Float?,
    val dhZeroToOneDepth: Boolean,
    val dhDistanceMapping: DistantHorizonsFogDistanceMapping,
) {
    fun uniform(directionX: Float, directionY: Float) = FogBlurUniform(
        inverseProjection,
        inverseViewRotation,
        dhInverseMvmProjection,
        ranges,
        kernel,
        directionX,
        directionY,
        zeroToOneDepth,
        dhClearDepth,
        dhZeroToOneDepth,
        dhDistanceMapping,
    )

    companion object {
        fun snapshot(
            cameraState: CameraRenderState,
            projectionMatrix: Matrix4fc,
            fogData: FogData,
            distantHorizonsDepth: DistantHorizonsDepthTexture?,
        ): FogBlurFrame {
            val dhFogDistance = distantHorizonsFogSaturationDistance(
                fogData.environmentalEnd,
                fogData.renderDistanceEnd,
            )
            val dhDistanceMapping = distantHorizonsDepth?.let { depth ->
                DistantHorizonsFogDistanceMapping.from(depth.nearClipPlane, depth.farClipPlane, dhFogDistance)
            } ?: DistantHorizonsFogDistanceMapping.from(0f, 0f, dhFogDistance)
            return FogBlurFrame(
                inverseProjection = Matrix4f(projectionMatrix).invert(),
                inverseViewRotation = Matrix4f(cameraState.viewRotationMatrix).invert(),
                dhInverseMvmProjection = distantHorizonsDepth?.inverseMvmProjection ?: Matrix4f(),
                ranges = FogBlurRanges.snapshot(fogData),
                kernel = GaussianKernel.forScreenRadius(CustomFogRenderBridge.blurStrength()),
                zeroToOneDepth = gpuDevice.deviceInfo.isZZeroToOne,
                dhClearDepth = distantHorizonsDepth?.clearDepth,
                dhZeroToOneDepth = distantHorizonsDepth?.zeroToOneDepth == true,
                dhDistanceMapping = dhDistanceMapping,
            )
        }
    }
}

private data class FogBlurUniform(
    val inverseProjection: Matrix4f,
    val inverseViewRotation: Matrix4f,
    val dhInverseMvmProjection: Matrix4f,
    val ranges: FogBlurRanges,
    val kernel: GaussianKernel,
    val directionX: Float,
    val directionY: Float,
    val zeroToOneDepth: Boolean,
    val dhClearDepth: Float?,
    val dhZeroToOneDepth: Boolean,
    val dhDistanceMapping: DistantHorizonsFogDistanceMapping,
)

private data class FogBlurRanges(
    val environmentalStart: Float,
    val environmentalEnd: Float,
    val renderDistanceStart: Float,
    val renderDistanceEnd: Float,
) {
    companion object {
        fun snapshot(fogData: FogData) = FogBlurRanges(
            fogData.environmentalStart,
            fogData.environmentalEnd,
            fogData.renderDistanceStart,
            fogData.renderDistanceEnd,
        )
    }
}
