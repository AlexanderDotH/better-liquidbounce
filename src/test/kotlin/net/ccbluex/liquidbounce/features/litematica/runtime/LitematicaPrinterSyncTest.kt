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
package net.ccbluex.liquidbounce.features.litematica.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LitematicaPrinterSyncTest {

    @Test
    fun `enabling adopts Litematica EasyPlace as the initial source of truth`() {
        val runtime = runtime()

        val enabled = runtime.enable(currentPrinterToggle = false, currentEasyPlace = true)

        assertEquals(listOf(PrinterSyncCommand.SetPrinterToggle(true)), enabled.syncCommands)
        assertTrue(runtime.snapshot.printerEnabled)
        assertTrue(runtime.snapshot.easyPlaceEnabled)
    }

    @Test
    fun `bidirectional synchronization is idempotent across callbacks`() {
        val runtime = runtime()
        runtime.enable(currentPrinterToggle = false, currentEasyPlace = false)

        assertEquals(
            listOf(PrinterSyncCommand.SetEasyPlace(true)),
            runtime.printerToggleChanged(true),
        )
        assertTrue(runtime.easyPlaceChanged(true).isEmpty())

        assertEquals(
            listOf(PrinterSyncCommand.SetPrinterToggle(false)),
            runtime.easyPlaceChanged(false),
        )
        assertTrue(runtime.printerToggleChanged(false).isEmpty())
    }

    @Test
    fun `synchronization callbacks cannot mutate the other side while disabled`() {
        val runtime = runtime()
        runtime.enable(currentPrinterToggle = true, currentEasyPlace = true)
        runtime.disable()

        assertEquals(false, runtime.snapshot.printerEnabled)
        assertTrue(runtime.snapshot.easyPlaceEnabled)
        assertTrue(runtime.easyPlaceChanged(false).isEmpty())
        assertTrue(runtime.printerToggleChanged(false).isEmpty())
        assertTrue(runtime.snapshot.easyPlaceEnabled)
        assertEquals(false, runtime.snapshot.printerEnabled)
    }

    @Test
    fun `key activation pauses until the Litematica key is active while continuous ignores it`() {
        val runtime = runtime()
        runtime.enable(currentPrinterToggle = false, currentEasyPlace = true)

        runtime.setActivationMode(PrinterActivationMode.LITEMATICA_KEY)
        runtime.beginTick(PrinterTickInput(tick = 1, litematicaKeyActive = false))
        assertEquals(PrinterRuntimePhase.IDLE, runtime.snapshot.phase)
        assertEquals(PrinterPauseReason.LITEMATICA_KEY_IDLE, runtime.snapshot.pauseReason)

        runtime.beginTick(PrinterTickInput(tick = 2, litematicaKeyActive = true))
        assertEquals(PrinterRuntimePhase.READY, runtime.snapshot.phase)

        runtime.setActivationMode(PrinterActivationMode.CONTINUOUS)
        runtime.beginTick(PrinterTickInput(tick = 3, litematicaKeyActive = false))
        assertEquals(PrinterRuntimePhase.READY, runtime.snapshot.phase)
    }

    @Test
    fun `external blockers pause only while the blocker is present`() {
        val runtime = runtime()
        runtime.enable(currentPrinterToggle = true, currentEasyPlace = true)
        runtime.setActivationMode(PrinterActivationMode.CONTINUOUS)

        runtime.beginTick(PrinterTickInput(
            tick = 1,
            litematicaKeyActive = false,
            externalPauseReason = PrinterPauseReason.SCAFFOLD_ACTIVE,
        ))

        assertEquals(PrinterRuntimePhase.PAUSED, runtime.snapshot.phase)
        assertEquals(PrinterPauseReason.SCAFFOLD_ACTIVE, runtime.snapshot.pauseReason)

        runtime.beginTick(PrinterTickInput(tick = 2, litematicaKeyActive = false))
        assertEquals(PrinterRuntimePhase.READY, runtime.snapshot.phase)
    }

    private fun runtime() = LitematicaPrinterRuntime<TestTarget>(PrinterTimeSource { 0L })

    private data class TestTarget(val name: String)
}
