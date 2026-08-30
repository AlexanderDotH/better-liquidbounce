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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.fireball.runtime

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class FlyFireballRuntimeArchitectureContractTest {

    @Test
    fun `fireball runtime owns its completion signal`() {
        val runtimeSource = Path(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/movement/fly/" +
                "modes/fireball/runtime/FlyFireballRuntime.kt",
        ).readText()

        assertFalse(runtimeSource.contains("movement.fly.modes.FlyAutomaticEndSignal"))
        assertContains(runtimeSource, "private var pendingAutomaticEnd: FlyAutomationEnd? = null")
    }

}
