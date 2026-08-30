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
package net.ccbluex.liquidbounce.utils.network

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class UseItemRotationContractTest {

    @Test
    fun `prediction keeps packet and local item use on the same explicit rotation`() {
        val source = Files.readString(SOURCE)
        val carriedItem = source.indexOf("ensureHasSentCarriedItem()")
        val prediction = source.indexOf("startPrediction(world)")
        val helper = source.indexOf("private fun Player.useItemWithRotation(")
        val setYaw = source.indexOf("yRot = explicitYRot", helper)
        val setPitch = source.indexOf("xRot = explicitXRot", helper)
        val use = source.indexOf("itemStack.use(world, this, hand)", helper)
        val restoreYaw = source.indexOf("yRot = previousYRot", use)
        val restorePitch = source.indexOf("xRot = previousXRot", use)

        assertTrue(carriedItem in 0..<prediction)
        assertTrue(helper >= 0)
        assertTrue(setYaw in helper..<use)
        assertTrue(setPitch in helper..<use)
        assertTrue(restoreYaw > use)
        assertTrue(restorePitch > use)
    }

    private companion object {
        val SOURCE: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/network/UseItem.kt")
    }
}
