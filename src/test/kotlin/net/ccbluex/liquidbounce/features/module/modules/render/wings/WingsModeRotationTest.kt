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
package net.ccbluex.liquidbounce.features.module.modules.render.wings

import net.ccbluex.liquidbounce.features.module.modules.render.ModulePlayerModel
import net.ccbluex.liquidbounce.features.module.modules.render.wings.runtime.resolveWingsBodyRotation
import net.ccbluex.liquidbounce.features.module.modules.render.wings.runtime.shouldRenderWingsForEntity
import net.ccbluex.liquidbounce.features.module.modules.render.wings.runtime.shouldUseModelWingsRotation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WingsModeRotationTest {

    @Test
    fun `model yaw requires local Replace rotation with the body part enabled`() {
        assertTrue(shouldUseModelWingsRotation(true, true, ModulePlayerModel.Display.REPLACE, true, true))
        assertFalse(shouldUseModelWingsRotation(false, true, ModulePlayerModel.Display.REPLACE, true, true))
        assertFalse(shouldUseModelWingsRotation(true, false, ModulePlayerModel.Display.REPLACE, true, true))
        assertFalse(shouldUseModelWingsRotation(true, true, ModulePlayerModel.Display.GHOST, true, true))
        assertFalse(shouldUseModelWingsRotation(true, true, ModulePlayerModel.Display.REPLACE, false, true))
        assertFalse(shouldUseModelWingsRotation(true, true, ModulePlayerModel.Display.REPLACE, true, false))
    }

    @Test
    fun `missing or disabled model rotation falls back to vanilla yaw`() {
        assertEquals(25f, resolveWingsBodyRotation(25f, null, true))
        assertEquals(25f, resolveWingsBodyRotation(25f, 90f, false))
        assertEquals(90f, resolveWingsBodyRotation(25f, 90f, true))
    }

    @Test
    fun `local view and eligible friend preserve the original wing visibility policy`() {
        assertFalse(shouldRenderWingsForEntity(true, true, false, false, false))
        assertTrue(shouldRenderWingsForEntity(true, true, true, false, false))
        assertTrue(shouldRenderWingsForEntity(true, true, false, true, false))
        assertTrue(shouldRenderWingsForEntity(true, false, false, false, false))
        assertTrue(shouldRenderWingsForEntity(false, true, false, false, true))
        assertFalse(shouldRenderWingsForEntity(false, false, true, true, false))
    }

}
