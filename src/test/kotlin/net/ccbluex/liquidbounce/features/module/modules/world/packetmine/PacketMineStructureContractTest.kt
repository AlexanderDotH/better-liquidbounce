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
package net.ccbluex.liquidbounce.features.module.modules.world.packetmine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PacketMineStructureContractTest {

    @Test
    fun `settings retain names defaults and registration order`() {
        assertInOrder(
            moduleSource,
            "\"Mode\"",
            "float(\"Range\", 4.5f, 1f..6f)",
            "float(\"WallsRange\", 4.5f, 0f..6f)",
            "float(\"KeepRange\", 25f, 0f..200f)",
            "enumChoice(\"Swing\", SwingMode.HIDE_CLIENT)",
            "val switchMode = choices(",
            "\"Switch\"",
            "enumChoice(\"Rotate\", MineRotationMode.NEVER)",
            "tree(RotationsValueGroup(this))",
            "boolean(\"IgnoreOpenInventory\", true)",
            "float(\"BreakDamage\", 1f, 0f..2f)",
            "int(\"PostBreakDelay\", 6, 0..10, \"ticks\")",
            "boolean(\"AbortAlwaysDown\", false)",
            "int(\"SelectDelay\", 200, 0..400, \"ms\")",
            "PlacementRenderer(",
            "BreakingProgressRenderer(targetRenderer, this)",
        )
    }

    @Test
    fun `rotation validation and tick order stay stable`() {
        assertInOrder(
            functionBody(runtimeSource, "rotate"),
            "rotationMode.shouldRotate(mineTarget)",
            "raytraceBlockRotation(",
            "mineTarget.abort()",
            "if (shouldRotate)",
            "RotationManager.setRotationTarget(",
            "rotation = raytrace.rotation",
        )
        assertInOrder(
            functionBody(runtimeSource, "onTick"),
            "scheduler.advanceTick()",
            "ModulePacketMine._target ?: return",
            "mineTarget.isInvalidOrOutOfRange()",
            "ModulePacketMine._target = null",
            "mineTarget.updateBlockState()",
            "handleBreaking(mineTarget)",
        )
        assertInOrder(
            functionBody(runtimeSource, "handleBreaking"),
            "val invalidHit =",
            "getFailProcedure(mineTarget).execute(mineTarget)",
            "updateDirection(mineTarget)",
            "player.isCreative",
            "handleCreativeBreaking(mineTarget)",
            "handleSurvivalBreaking(mineTarget)",
        )
    }

    @Test
    fun `survival finish and reset order stay stable`() {
        assertInOrder(
            functionBody(runtimeSource, "handleSurvivalBreaking"),
            "getSlot(mineTarget.blockState)",
            "!mineTarget.started",
            "!scheduler.canStart()",
            "startBreaking(slot, mineTarget)",
            "shouldUpdate(mineTarget, slot)",
            "PacketMineProgress.update(mineTarget, slot)",
            "finishBreakingIfReady(mineTarget, switchMode)",
            "getSwitchingMethod().reset()",
        )
        assertInOrder(
            functionBody(runtimeSource, "finishBreakingIfReady"),
            "mineTarget.progress < ModulePacketMine.breakDamage",
            "mineTarget.finished",
            "finishReadyTick == null",
            "mineTarget.finishReadyTick = scheduler.tick",
            "scheduler.shouldFinish(finishReadyTick, ModulePacketMine.postBreakDelay)",
            "ModulePacketMine.mode.activeMode.finish(mineTarget)",
            "getSwitchingMethod().switchBack()",
        )
    }

    @Test
    fun `creative packets and progress rendering order stay stable`() {
        assertInOrder(
            functionBody(runtimeSource, "handleCreativeBreaking"),
            "interaction.startPrediction(world)",
            "interaction.destroyBlock(mineTarget.targetPos)",
            "ServerboundPlayerActionPacket(",
            "swingMode.swing(InteractionHand.MAIN_HAND)",
        )
        assertInOrder(
            functionBody(progressSource, "update"),
            "getBlockBreakingDelta(",
            "ModulePacketMine.switch(slot, mineTarget)",
            "ensureHasSentCarriedItem()",
            "targetRenderer.updateBox(",
        )
    }

    @Test
    fun `target selection retains cancellation and update semantics`() {
        assertInOrder(
            functionBody(selectorSource, "onMouseButton"),
            "mc.gui.screen() != null",
            "!ModulePacketMine.mode.activeMode.canManuallyChange",
            "!player.abilities.mayBuild",
            "chronometer.hasElapsed(ModulePacketMine.selectDelay.toLong())",
            "!event.isLeftButton",
            "hitResult !is BlockHitResult",
            "shouldTarget(blockPos, state)",
            "blockPos == activeTarget?.targetPos",
            "shouldBlockTargetChange(activeTarget)",
            "chronometer.reset()",
            "world.worldBorder.isWithinBounds(blockPos)",
            "ModulePacketMine._target =",
            "chronometer.reset()",
        )
        assertInOrder(
            functionBody(selectorSource, "onBlockStateUpdate"),
            "ModulePacketMine._target ?: return",
            "pos != target.targetPos",
            "state.isAir",
            "stopOnStateChange",
            "ModulePacketMine._target = null",
        )
    }

    @Test
    fun `module remains a bounded event and public API facade`() {
        assertFalse("@Suppress(\"TooManyFunctions\")" in moduleSource)
        assertTrue("runtime.onRotationUpdate()" in moduleSource)
        assertTrue("runtime.onTick()" in moduleSource)
        assertTrue("targetSelector.onMouseButton(event)" in moduleSource)
        assertTrue("targetSelector.setTarget(blockPos)" in moduleSource)
        assertFalse("private fun handleBreaking" in moduleSource)
        assertFalse("private fun updateBreakingProgress" in moduleSource)
    }

    private fun functionBody(source: String, functionName: String): String {
        val signature = Regex("""fun\s+${Regex.escape(functionName)}\(""")
            .find(source)
            ?.range
            ?.first
            ?: error("Missing function $functionName")
        val openingBrace = source.indexOf('{', signature)
        require(openingBrace >= 0) { "Missing body for $functionName" }
        var depth = 0
        source.forEachIndexed { index, character ->
            if (index < openingBrace) return@forEachIndexed
            when (character) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(openingBrace, index + 1)
            }
        }
        error("Unclosed body for $functionName")
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previousIndex + 1)
            assertTrue(index > previousIndex, "Expected `$marker` after index $previousIndex")
            previousIndex = index
        }
    }

    private companion object {
        val SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/world/packetmine",
        )
        val moduleSource: String = Files.readString(SOURCE_ROOT.resolve("ModulePacketMine.kt"))
        val runtimeSource: String = Files.readString(SOURCE_ROOT.resolve("PacketMineRuntime.kt"))
        val progressSource: String = Files.readString(SOURCE_ROOT.resolve("PacketMineProgress.kt"))
        val selectorSource: String = Files.readString(SOURCE_ROOT.resolve("PacketMineTargetSelector.kt"))
    }
}
