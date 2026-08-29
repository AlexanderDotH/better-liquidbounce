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
package net.ccbluex.liquidbounce.features.litematica.integration.litematica262

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class Litematica262MixinContractTest {

    @Test
    fun `optional mixin gates every native easy place entrypoint`() {
        val source = Files.readString(MIXIN_SOURCE)

        assertTrue(source.contains("@Pseudo"))
        assertTrue(source.contains("fi.dy.masa.litematica.util.EasyPlaceUtils"))
        assertTrue(source.contains("easyPlaceOnUseTick"))
        assertTrue(source.contains("onRightClickTail"))
        assertTrue(source.contains("handleEasyPlaceWithMessage"))
        assertTrue(source.contains("shouldSuppressNativeEasyPlace"))
        assertTrue(source.contains("setReturnValue(true)"))
        assertTrue(source.contains("require = 0"))
    }

    private companion object {
        val MIXIN_SOURCE: Path = Path.of(
            "src/main/java/net/ccbluex/liquidbounce/injection/mixins/litematica/MixinLitematicaEasyPlaceUtils.java",
        )
    }
}
