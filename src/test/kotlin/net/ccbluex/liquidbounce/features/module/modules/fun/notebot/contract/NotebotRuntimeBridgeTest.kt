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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NotebotRuntimeBridgeTest {

    @Test
    fun `bridge preserves settings stage delay and feedback mapping`() {
        val feedback = mutableListOf<String>()
        val provider = object : NotebotRuntimeHook {
            override fun range() = 5.5
            override fun reuseBlocks() = false
            override fun pianoOnly() = true
            override fun stageDelay(stage: NotebotStage) = stage.ordinal + 2
            override fun onStageChanged(stage: NotebotStage) {
                feedback.add("stage:$stage")
            }
            override fun message(key: String) = Component.literal(key)
            override fun chat(component: Component) {
                feedback.add(component.string)
            }
            override fun reportLoadError(key: String, vararg args: Any) {
                feedback.add("$key:${args.joinToString()}")
            }
            override fun sendProgress(name: Component, progress: Int, total: Int) {
                feedback.add("${name.string}:$progress/$total")
            }
            override fun setRenderedBlocks(blocks: List<BlockPos>) {
                feedback.add("blocks:${blocks.size}")
            }
            override fun disable() {
                feedback.add("disabled")
            }
        }

        NotebotRuntimeBridge.withProviderForTest(provider) {
            assertEquals(5.5, NotebotRuntimeBridge.range())
            assertFalse(NotebotRuntimeBridge.reuseBlocks())
            assertEquals(3, NotebotRuntimeBridge.stageDelay(NotebotStage.TUNE))
            NotebotRuntimeBridge.onStageChanged(NotebotStage.PLAY)
            NotebotRuntimeBridge.sendProgress(Component.literal("Play"), 2, 4)
        }

        assertEquals(listOf("stage:PLAY", "Play:2/4"), feedback)
    }
}
