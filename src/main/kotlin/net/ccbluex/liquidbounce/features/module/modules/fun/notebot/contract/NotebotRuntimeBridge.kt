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
import net.minecraft.network.chat.MutableComponent

internal enum class NotebotStage {
    TEST,
    TUNE,
    PLAY,
}

internal interface NotebotRuntimeHook {
    fun range(): Double
    fun reuseBlocks(): Boolean
    fun pianoOnly(): Boolean
    fun stageDelay(stage: NotebotStage): Int
    fun onStageChanged(stage: NotebotStage)
    fun message(key: String): MutableComponent
    fun chat(component: Component)
    fun reportLoadError(key: String, vararg args: Any)
    fun sendProgress(name: Component, progress: Int, total: Int)
    fun setRenderedBlocks(blocks: List<BlockPos>)
    fun disable()
}

internal object NotebotRuntimeBridge : NotebotRuntimeHook {
    private object DisabledRuntime : NotebotRuntimeHook {
        override fun range() = 0.0
        override fun reuseBlocks() = false
        override fun pianoOnly() = false
        override fun stageDelay(stage: NotebotStage) = 0
        override fun onStageChanged(stage: NotebotStage) = Unit
        override fun message(key: String): MutableComponent = Component.empty()
        override fun chat(component: Component) = Unit
        override fun reportLoadError(key: String, vararg args: Any) = Unit
        override fun sendProgress(name: Component, progress: Int, total: Int) = Unit
        override fun setRenderedBlocks(blocks: List<BlockPos>) = Unit
        override fun disable() = Unit
    }

    private var provider: NotebotRuntimeHook = DisabledRuntime

    fun install(provider: NotebotRuntimeHook) {
        this.provider = provider
    }

    override fun range() = provider.range()
    override fun reuseBlocks() = provider.reuseBlocks()
    override fun pianoOnly() = provider.pianoOnly()
    override fun stageDelay(stage: NotebotStage) = provider.stageDelay(stage)
    override fun onStageChanged(stage: NotebotStage) = provider.onStageChanged(stage)
    override fun message(key: String) = provider.message(key)
    override fun chat(component: Component) = provider.chat(component)
    override fun reportLoadError(key: String, vararg args: Any) = provider.reportLoadError(key, *args)
    override fun sendProgress(name: Component, progress: Int, total: Int) =
        provider.sendProgress(name, progress, total)
    override fun setRenderedBlocks(blocks: List<BlockPos>) = provider.setRenderedBlocks(blocks)
    override fun disable() = provider.disable()

    internal fun <T> withProviderForTest(provider: NotebotRuntimeHook, block: () -> T): T {
        val previous = this.provider
        this.provider = provider
        return try {
            block()
        } finally {
            this.provider = previous
        }
    }
}
