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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerInteractedItemEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.portal.PortalBlockTracker
import net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.render.PredictionRenderer
import net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.render.StrongholdRenderSettings
import net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.session.EyeThrowSession
import net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.session.StrongholdPredictionSession
import net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.session.StrongholdPredictionSettings
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.math.toFixed
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

/**
 * Tracks Eye of Ender throws and estimates the strongest stronghold chunk candidate.
 *
 * [Article](https://github.com/Ninjabrain1/Ninjabrain-Bot/blob/main/triangulation.pdf)
 */
object ModuleStrongholdFinder : ClientModule(
    "StrongholdFinder",
    ModuleCategories.WORLD,
    aliases = listOf("Triangulation")
) {

    private val sigma by float("Sigma", 0.03f, 0.005f..0.20f, "°").onChanged {
        onEstimatorSettingsChanged()
    }

    private val hypothesisCount by int("HypothesisCount", 20000, 2000..100000).onChanged {
        prediction.invalidateHypotheses()
        onEstimatorSettingsChanged()
    }

    private val requireSameStrongholdAcrossThrows by boolean("RequireSameStrongholdAcrossThrows", true).onChanged {
        onEstimatorSettingsChanged()
    }

    private val sampleDelayTicks by int("SampleDelayTicks", 2, 0..10)
    private val minEyeHorizontalSpeed by float("MinEyeHorizontalSpeed", 0.02f, 0.001f..0.2f)
    private val maxSampleAgeTicks by int("MaxSampleAgeTicks", 20, 5..100)
    private val maxEyeSpawnDistance by float("MaxEyeSpawnDistance", 8f, 1f..32f)

    private val showTopCandidates by int("ShowTopCandidates", 3, 1..10).onChanged {
        onEstimatorSettingsChanged()
    }

    private val renderRays by boolean("RenderRays", true)
    private val renderBestChunk by boolean("RenderBestChunk", true)
    private val renderTopChunks by boolean("RenderTopChunks", true)
    private val announcePrediction by boolean("AnnouncePrediction", true)
    private val resetOnWorldChange by boolean("ResetOnWorldChange", true)

    private val eyeThrows = EyeThrowSession()
    private val prediction = StrongholdPredictionSession()
    private val portalBlocks = PortalBlockTracker()

    override fun onDisabled() {
        resetState()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        if (resetOnWorldChange) {
            resetState()
        }
    }

    @Suppress("unused")
    private val interactedItemHandler = handler<PlayerInteractedItemEvent> { event ->
        if (!isOverworld() || !event.actionResult.consumesAction()) {
            return@handler
        }

        if (event.player.getItemInHand(event.hand).item != Items.ENDER_EYE) {
            return@handler
        }

        eyeThrows.recordThrow(player.position(), player.tickCount, world.dimension(), maxSampleAgeTicks)
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (!isOverworld()) {
            return@handler
        }

        when (val packet = event.packet) {
            is ClientboundAddEntityPacket -> mc.execute {
                eyeThrows.trackSpawn(
                    packet,
                    world.dimension(),
                    player.tickCount,
                    maxSampleAgeTicks,
                    maxEyeSpawnDistance,
                )
            }
            is ClientboundBlockUpdatePacket -> mc.execute { portalBlocks.track(packet.pos, packet.blockState.block) }
            is ClientboundSectionBlocksUpdatePacket -> mc.execute {
                packet.runUpdates { pos, state -> portalBlocks.track(pos, state.block) }
            }
            is ClientboundLevelChunkWithLightPacket -> mc.execute { portalBlocks.scan(world, packet.x, packet.z) }
            is ClientboundForgetLevelChunkPacket -> mc.execute { portalBlocks.removeChunk(packet.pos) }
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (!isOverworld()) {
            return@handler
        }

        eyeThrows.captureMeasurements(
            world,
            player.tickCount,
            maxSampleAgeTicks,
            sampleDelayTicks,
            minEyeHorizontalSpeed,
        ) { measurement ->
            prediction.record(measurement)
            notification(name, message("sampleCaptured", prediction.sampleCount), NotificationEvent.Severity.INFO)
            recomputePosterior(announce = true)
        }
    }

    @Suppress("unused")
    private val render3DHandler = handler<WorldRenderEvent> { event ->
        if (!isOverworld()) {
            return@handler
        }

        event.renderEnvironment {
            val playerPosition = player.interpolateCurrentPosition(event.partialTicks)
            if (portalBlocks.isNotEmpty()) {
                portalBlocks.render(this, playerPosition)
                return@renderEnvironment
            }

            PredictionRenderer.renderWorld(
                this,
                prediction.measurements,
                prediction.snapshot,
                playerPosition.y,
                renderSettings(),
            )
        }
    }

    @Suppress("unused")
    private val renderOverlayHandler = handler<OverlayRenderEvent> { event ->
        if (!isOverworld() || portalBlocks.isNotEmpty()) {
            return@handler
        }

        PredictionRenderer.renderOverlay(event, mc, name, prediction.snapshot ?: return@handler, sigma)
    }

    private fun recomputePosterior(announce: Boolean) {
        val announcement = prediction.recompute(predictionSettings(), announce) ?: return
        val chunkPos = announcement.chunkPos
        notification(
            name,
            message("bestChunk", chunkPos.x, chunkPos.z, (announcement.probability * 100.0).toFixed(1)),
            NotificationEvent.Severity.INFO
        )
        prediction.markAnnounced(chunkPos)
    }

    private fun onEstimatorSettingsChanged() {
        if (prediction.hasMeasurements()) {
            recomputePosterior(announce = false)
        }
    }

    private fun predictionSettings() = StrongholdPredictionSettings(
        hypothesisCount,
        sigma,
        requireSameStrongholdAcrossThrows,
        showTopCandidates,
        announcePrediction,
    )

    private fun renderSettings() = StrongholdRenderSettings(
        showTopCandidates,
        renderRays,
        renderBestChunk,
        renderTopChunks,
    )

    private fun resetState() {
        eyeThrows.clear()
        prediction.clear()
        portalBlocks.clear()
    }

    private fun isOverworld(): Boolean = world.dimension() == Level.OVERWORLD
}
