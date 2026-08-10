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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.chunk.ChunkAccess
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Regenerates expected terrain through vanilla FEATURES using a minimal background [MinecraftServer].
 *
 * The server runs the real chunk pyramid (neighbors included), so stone variants / gravel / ores match
 * the typed seed. Works in singleplayer and multiplayer. Failures are hard errors — no Base-column fallback.
 */
internal object MinecraftFullBaseFinderChunkExpector : BaseFinderChunkExpector {

    private val fullChunkCache = ConcurrentHashMap<FullChunkKey, ExpectedChunkBlocks>()
    private val lastFailure = AtomicReference<String?>(null)

    override fun expectColumns(
        context: BaseFinderWorldGenContext,
        chunk: ChunkCoordinate,
        localSamples: Collection<Pair<Int, Int>>,
    ): ExpectedChunkBlocks {
        val cacheKey = FullChunkKey(context.seed, context.dimensionKey, chunk.x, chunk.z)
        val full = fullChunkCache[cacheKey]
            ?: generateFullChunk(context, chunk)?.also {
                fullChunkCache[cacheKey] = it
                trimCache()
            }
        if (full == null) {
            val reason = lastFailure.get()
                ?: BaseFinderBackgroundServerHost.lastFailure()
                ?: "Features generation failed"
            fail(reason)
            error(reason)
        }

        if (localSamples.size >= 256) return full
        val wanted = localSamples.mapTo(HashSet(localSamples.size)) { (x, z) ->
            ObservedChunkBlocks.packLocal(x, z)
        }
        return full.copy(columns = full.columns.filterKeys { it in wanted })
    }

    fun clearCache() {
        fullChunkCache.clear()
        lastFailure.set(null)
        BaseFinderBackgroundServerHost.clearFailure()
        BaseFinderBackgroundServerHost.shutdown()
        BaseFinderHeadlessWorldGenHost.clear()
    }

    /** Drop cached FEATURES columns without shutting the background server down. */
    fun invalidateGeneratedChunks() {
        fullChunkCache.clear()
    }

    /** Keep only packed FEATURES columns that are still inside the active scan set. */
    fun retainChunks(seed: Long, dimensionKey: String, keep: Set<ChunkCoordinate>) {
        if (keep.isEmpty()) {
            fullChunkCache.keys.removeIf { it.seed == seed && it.dimensionKey == dimensionKey }
            return
        }
        fullChunkCache.keys.removeIf { key ->
            key.seed == seed &&
                key.dimensionKey == dimensionKey &&
                ChunkCoordinate(key.chunkX, key.chunkZ) !in keep
        }
    }

    fun lastFailure(): String? = lastFailure.get() ?: BaseFinderBackgroundServerHost.lastFailure()

    /**
     * Background server view distance for ticket machinery only.
     *
     * Features compare does **not** regenerate the player's whole render disk. Workers request one
     * scan-ring chunk at a time via [BaseFinderBackgroundServer.generateExpectedChunk]; vanilla
     * pulls in just that chunk's generation neighbors. Keep VD at the vanilla floor so we never
     * eagerly load client RD×RD around spawn.
     */
    internal fun targetViewDistance(): Int = BaseFinderBackgroundServer.GENERATION_VIEW_DISTANCE

    private fun generateFullChunk(
        context: BaseFinderWorldGenContext,
        chunk: ChunkCoordinate,
    ): ExpectedChunkBlocks? {
        lastFailure.set(null)
        return try {
            val server = BaseFinderBackgroundServerHost.ensureRunning(context.seed, targetViewDistance())
            syncFocusFromPlayer(server)
            val access = server.generateExpectedChunk(context.dimensionKey, chunk.x, chunk.z)
                ?: run {
                    fail("generate returned null dim=${context.dimensionKey} chunk=${chunk.x},${chunk.z}")
                    return null
                }
            val packed = packExpected(
                access,
                chunk,
                context.heightAccessor.minY,
                context.heightAccessor.height,
            )
            // Pack first, then drop server holders — do not unload while still reading [access].
            server.reclaimAfterGeneration(context.dimensionKey, chunk.x, chunk.z)
            packed
        } catch (error: Throwable) {
            fail(
                "generate dim=${context.dimensionKey} chunk=${chunk.x},${chunk.z}: " +
                    (error.message ?: error::class.java.simpleName),
            )
            null
        }
    }

    private fun syncFocusFromPlayer(server: BaseFinderBackgroundServer) {
        val player = mc.player ?: return
        val pos = player.blockPosition()
        server.syncPlayerFocus(
            dimensionKey = player.level().dimension().identifier().toString(),
            blockX = pos.x,
            blockY = pos.y,
            blockZ = pos.z,
            yaw = player.yRot,
        )
    }

    private fun packExpected(
        access: ChunkAccess,
        chunk: ChunkCoordinate,
        minY: Int,
        height: Int,
    ): ExpectedChunkBlocks {
        val originX = chunk.x shl 4
        val originZ = chunk.z shl 4
        val columns = HashMap<Int, IntArray>(256)
        val mutable = BlockPos.MutableBlockPos()
        for (localX in 0..15) {
            for (localZ in 0..15) {
                val packed = IntArray(height)
                for (yOffset in 0 until height) {
                    val state = access.getBlockState(mutable.set(originX + localX, minY + yOffset, originZ + localZ))
                    packed[yOffset] = BuiltInRegistries.BLOCK.getId(state.block)
                }
                columns[ObservedChunkBlocks.packLocal(localX, localZ)] = packed
            }
        }
        return ExpectedChunkBlocks(
            chunk = chunk,
            minY = minY,
            height = height,
            columns = columns,
            fidelity = ExpectedTerrainFidelity.FEATURES,
        )
    }

    private fun fail(message: String) {
        lastFailure.set(message)
    }

    private fun trimCache() {
        val overflow = fullChunkCache.size - MAX_CACHED_CHUNKS
        if (overflow <= 0) return
        val keys = fullChunkCache.keys.take(overflow)
        for (key in keys) fullChunkCache.remove(key)
    }

    private data class FullChunkKey(
        val seed: Long,
        val dimensionKey: String,
        val chunkX: Int,
        val chunkZ: Int,
    )

    /** Packed full-height columns are ~0.4MB each; keep a small working set only. */
    private const val MAX_CACHED_CHUNKS = 48
}
