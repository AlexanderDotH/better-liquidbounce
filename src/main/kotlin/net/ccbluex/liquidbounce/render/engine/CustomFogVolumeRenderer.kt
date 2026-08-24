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

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.ClientShutdownEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.render.customambience.ModuleCustomAmbience.FogValueGroup
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker
import net.ccbluex.liquidbounce.render.ClientRenderPipelines
import net.ccbluex.liquidbounce.render.ClientUniformDefine
import net.ccbluex.liquidbounce.render.buffers.CachedUniform
import net.ccbluex.liquidbounce.render.createRenderPass
import net.ccbluex.liquidbounce.render.engine.esp.IrisPipelineBypass
import net.ccbluex.liquidbounce.utils.client.clientStartDurationMs
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.inGame
import net.minecraft.client.renderer.fog.FogData
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector4f

/** Composites moving world-space fog without sampling or blurring the rendered terrain color. */
object CustomFogVolumeRenderer : MinecraftShortcuts, EventListener {

    private val depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
    private val volumeData = CachedUniform<FogVolumeUniform>(ClientUniformDefine.FOG_VOLUME) { value ->
        putMat4f(value.inverseProjection)
        putMat4f(value.inverseViewRotation)
        putMat4f(value.dhInverseMvmProjection)
        putVec4(value.fogColor)
        putVec4(
            value.fogData.environmentalStart,
            value.fogData.environmentalEnd,
            value.fogData.renderDistanceStart,
            value.fogData.renderDistanceEnd,
        )
        putVec4(value.cameraX, value.cameraY, value.cameraZ, value.time)
        putVec4(value.strength, VOLUME_WORLD_PERIOD, value.maxDistance, if (value.zeroToOneDepth) 1f else 0f)
        putVec4(
            MC_CLEAR_DEPTH,
            value.cameraClearRadius,
            value.dhClearDepth ?: NO_DH_DEPTH,
            if (value.dhZeroToOneDepth) 1f else 0f,
        )
        putVec4(
            value.layers.spacing,
            value.layers.groundDensity,
            value.layers.middleDensity,
            value.layers.upperDensity,
        )
        putVec4(
            value.dhDistanceMapping.scale,
            value.dhDistanceMapping.offset,
            value.dhDistanceMapping.maxDistance,
            0f,
        )
    }

    @JvmStatic
    fun render(target: RenderTarget, cameraState: CameraRenderState, projectionMatrix: Matrix4fc) {
        if (!inGame || !FogValueGroup.shouldRenderVolume) return
        if (target.width <= 0 || target.height <= 0) return

        val depthTexture = target.depthTextureView ?: return
        val distantHorizonsDepth = DistantHorizonsDepthTextureProvider.resolve(target.width, target.height)
        val dhDepthTexture = distantHorizonsDepth?.textureView ?: depthTexture
        val fogData = cameraState.fogData.also(FogValueGroup::modifyFogData)
        val uniform = volumeData.get(snapshot(cameraState, projectionMatrix, fogData, distantHorizonsDepth))

        IrisPipelineBypass.run {
            target.createRenderPass(
                { "LiquidBounce custom volumetric fog" },
                useDepthAttachment = false,
            ).use { pass ->
                pass.setPipeline(ClientRenderPipelines.FogVolume)
                pass.bindTexture("DepthSampler", depthTexture, depthSampler)
                pass.bindTexture("DhDepthSampler", dhDepthTexture, depthSampler)
                pass.setUniform(ClientUniformDefine.FOG_VOLUME.uboName, uniform)
                pass.draw(3, 1, 0, 0)
            }
        }
    }

    private fun snapshot(
        cameraState: CameraRenderState,
        projectionMatrix: Matrix4fc,
        fogData: FogData,
        distantHorizonsDepth: DistantHorizonsDepthTexture?,
    ): FogVolumeUniform {
        val multiLayer = FogValueGroup.VolumetricFog.MultiLayerFog
        val maxDistance = volumetricFogMaxDistance(fogData.environmentalEnd, fogData.renderDistanceEnd)
        val dhFogDistance = distantHorizonsFogSaturationDistance(
            fogData.environmentalEnd,
            fogData.renderDistanceEnd,
        )
        val dhDistanceMapping = distantHorizonsDepth?.let { depth ->
            DistantHorizonsFogDistanceMapping.from(depth.nearClipPlane, depth.farClipPlane, dhFogDistance)
        } ?: DistantHorizonsFogDistanceMapping.from(0f, 0f, dhFogDistance)
        return FogVolumeUniform(
            inverseProjection = Matrix4f(projectionMatrix).invert(),
            inverseViewRotation = Matrix4f(cameraState.viewRotationMatrix).invert(),
            dhInverseMvmProjection = distantHorizonsDepth?.inverseMvmProjection ?: Matrix4f(),
            fogColor = Vector4f(fogData.color),
            fogData = FogVolumeRanges.snapshot(fogData),
            cameraX = wrapVolumetricFogCoordinate(cameraState.pos.x),
            cameraY = wrapVolumetricFogCoordinate(cameraState.pos.y),
            cameraZ = wrapVolumetricFogCoordinate(cameraState.pos.z),
            time = clientStartDurationMs / 1000f * VOLUME_SPEED,
            strength = volumetricFogStrength(FogValueGroup.VolumetricFog.strength) *
                volumetricInteractionStrength(ModuleNuker.running),
            maxDistance = maxDistance,
            cameraClearRadius = volumetricCameraClearRadius(FogValueGroup.VolumetricFog.cameraClearRadius),
            zeroToOneDepth = gpuDevice.deviceInfo.isZZeroToOne,
            dhClearDepth = distantHorizonsDepth?.clearDepth,
            dhZeroToOneDepth = distantHorizonsDepth?.zeroToOneDepth == true,
            dhDistanceMapping = dhDistanceMapping,
            layers = VolumetricFogLayerSettings.from(
                enabled = multiLayer.running,
                spacing = multiLayer.layerSpacing,
                groundDensity = multiLayer.groundDensity,
                middleDensity = multiLayer.middleDensity,
                upperDensity = multiLayer.upperDensity,
            ),
        )
    }

    @Suppress("unused")
    private val shutdownHandler = handler<ClientShutdownEvent> {
        volumeData.close()
    }

    private const val MC_CLEAR_DEPTH = 0f
    private const val NO_DH_DEPTH = -1f
    private const val VOLUME_SPEED = 0.15f
}

private data class FogVolumeUniform(
    val inverseProjection: Matrix4f,
    val inverseViewRotation: Matrix4f,
    val dhInverseMvmProjection: Matrix4f,
    val fogColor: Vector4f,
    val fogData: FogVolumeRanges,
    val cameraX: Float,
    val cameraY: Float,
    val cameraZ: Float,
    val time: Float,
    val strength: Float,
    val maxDistance: Float,
    val cameraClearRadius: Float,
    val zeroToOneDepth: Boolean,
    val dhClearDepth: Float?,
    val dhZeroToOneDepth: Boolean,
    val dhDistanceMapping: DistantHorizonsFogDistanceMapping,
    val layers: VolumetricFogLayerSettings,
)

private data class FogVolumeRanges(
    val environmentalStart: Float,
    val environmentalEnd: Float,
    val renderDistanceStart: Float,
    val renderDistanceEnd: Float,
) {
    companion object {
        fun snapshot(fogData: FogData) = FogVolumeRanges(
            fogData.environmentalStart,
            fogData.environmentalEnd,
            fogData.renderDistanceStart,
            fogData.renderDistanceEnd,
        )
    }
}
