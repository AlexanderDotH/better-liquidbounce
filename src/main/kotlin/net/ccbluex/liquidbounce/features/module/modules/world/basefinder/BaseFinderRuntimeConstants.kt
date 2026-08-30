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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.render.engine.type.Color4b

/** Fixed BaseFinder evidence / seed-compare tuning (not exposed in ClickGUI). */
internal const val DIRTY_CHUNKS_PER_TICK = 2
internal const val ENTITY_SAMPLE_INTERVAL_TICKS = 20
internal const val SEED_WORKER_THREADS = DEFAULT_BASE_FINDER_SEED_WORKER_THREADS
internal const val SEED_PROMOTIONS_PER_TICK = 1
internal const val SEED_SPARSE_SAMPLES_PER_CHUNK = 16
internal const val SEED_SPARSE_AUDIT_WINDOW = 2
/** Packed expected columns are large; keep only a small LRU around the scan ring. */
internal const val SEED_CACHE_CHUNKS = 64
internal const val SEED_FREEZES_PER_TICK = 4
internal const val SEED_OVERLAY_RESCAN_INTERVAL_TICKS = 40
internal const val SEED_MISMATCH_RENDER_LIMIT = 2048
internal val SEED_MISMATCH_MISSING_SOLID_COLOR = Color4b(64, 220, 255)
internal val SEED_MISMATCH_UNEXPECTED_SOLID_COLOR = Color4b(255, 140, 40)
internal val SEED_MISMATCH_UTILITY_COLOR = Color4b(255, 64, 220)
internal val SEED_MISMATCH_MATERIAL_SWAP_COLOR = Color4b(240, 230, 90)
