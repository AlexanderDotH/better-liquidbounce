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
package net.ccbluex.liquidbounce.features.module.modules.world.packetmine

import net.ccbluex.liquidbounce.event.events.BlockAttackEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.mode.CivMineMode
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.mode.ImmediateMineMode
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.mode.NormalMineMode
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.tool.AlwaysToolMode
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.tool.NeverToolMode
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.tool.OnStopToolMode
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.tool.PostStartToolMode
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.placement.PlacementRenderer
import net.ccbluex.liquidbounce.render.progress.BreakingProgress
import net.ccbluex.liquidbounce.render.progress.BreakingProgressRenderer
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.minecraft.core.BlockPos

/**
 * PacketMine module
 *
 * Automatically mines blocks you click once. Using AutoTool is recommended.
 *
 * @author ccetl
 */
object ModulePacketMine : ClientModule("PacketMine", ModuleCategories.WORLD), BreakingProgress.Provider {

    val mode = modes(
        this,
        "Mode",
        NormalMineMode,
        arrayOf(NormalMineMode, ImmediateMineMode, CivMineMode)
    ).apply {
        tagBy(this)
    }

    internal val range by float("Range", 4.5f, 1f..6f)
    internal val wallsRange by float("WallsRange", 4.5f, 0f..6f).onChange {
        minOf(range, it)
    }

    val keepRange by float("KeepRange", 25f, 0f..200f).onChange {
        maxOf(range, it)
    }

    val swingMode by enumChoice("Swing", SwingMode.HIDE_CLIENT)
    val switchMode = choices(
        "Switch",
        OnStopToolMode,
        arrayOf(AlwaysToolMode, PostStartToolMode, OnStopToolMode, NeverToolMode)
    )

    internal val rotationMode by enumChoice("Rotate", MineRotationMode.NEVER)
    internal val rotations = tree(RotationsValueGroup(this))
    internal val ignoreOpenInventory by boolean("IgnoreOpenInventory", true)
    val breakDamage by float("BreakDamage", 1f, 0f..2f)
    /**
     * Extra delay before starting the next target after sending `STOP_DESTROY_BLOCK`.
     *
     * Vanilla applies a chained-break cooldown in
     * [net.minecraft.client.multiplayer.MultiPlayerGameMode.continueDestroyBlock]
     * by setting `destroyDelay = 5` after a successful break.
     * The default `6` is to prevent possible flags by different network environment and anticheat.
     *
     * @see net.minecraft.client.multiplayer.MultiPlayerGameMode.destroyDelay
     */
    internal val postBreakDelay by int("PostBreakDelay", 6, 0..10, "ticks")
    val abortAlwaysDown by boolean("AbortAlwaysDown", false)
    internal val selectDelay by int("SelectDelay", 200, 0..400, "ms")

    val targetRenderer = tree(
        PlacementRenderer(
            "TargetRendering", true, this,
            defaultColor = Color4b(255, 255, 0, 90),
            clump = false
        )
    )
    private val progressRenderer = targetRenderer.tree(BreakingProgressRenderer(targetRenderer, this))

    private val targetSelector = PacketMineTargetSelector()
    private val runtime = PacketMineRuntime()

    /**
     * The current target of the module.
     *
     * Should never be accessed directly by other modules!
     */
    @Suppress("ObjectPropertyName", "ObjectPropertyNaming")
    var _target: MineTarget? = null // yes "_" because kotlin lacks package private
        set(value) { // and I don't want to offer this to modules using this to mine something
            if (value == field) {
                return
            }

            field?.cleanUp()
            value?.init()
            field = value
        }

    init {
        mode.onChanged {
            if (mc.level != null && mc.player != null) {
                onDisabled()
                onEnabled()
            }
        }
    }

    override fun onEnabled() {
        runtime.resetStartDelay()
        interaction.stopDestroyBlock()
    }

    override fun onDisabled() {
        targetRenderer.clearSilently()
        runtime.resetStartDelay()
        _target = null
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        runtime.onRotationUpdate()
    }

    @Suppress("unused")
    private val repeatable = handler<GameTickEvent> {
        runtime.onTick()
    }

    override fun breakingProgress() = PacketMineProgress.current()

    fun switch(slot: HotbarItemSlot?, mineTarget: MineTarget) {
        runtime.switch(slot, mineTarget)
    }

    @Suppress("unused")
    private val mouseButtonHandler = handler<MouseButtonEvent> { event ->
        targetSelector.onMouseButton(event)
    }

    @Suppress("unused")
    private val blockAttackHandler = handler<BlockAttackEvent> {
        it.cancelEvent()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        _target = null
    }

    @Suppress("unused")
    private val blockUpdateHandler = handler<PacketEvent> {
        targetSelector.onPacket(it.packet)
    }

    fun setTarget(blockPos: BlockPos) {
        targetSelector.setTarget(blockPos)
    }

    @Suppress("FunctionNaming", "FunctionName")
    fun _resetTarget() {
        _target = null
    }

}
