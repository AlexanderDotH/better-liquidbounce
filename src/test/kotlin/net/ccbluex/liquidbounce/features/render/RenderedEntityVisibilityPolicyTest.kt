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
package net.ccbluex.liquidbounce.features.render

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderedEntityVisibilityPolicyTest {

    @Test
    fun `installed policies are evaluated live`() {
        var visibleIds = setOf(1)
        var refreshOnPerspective = false
        val policy = RenderedEntityVisibilityPolicy<Int>()

        policy.install(
            shouldRenderEntity = { id -> id in visibleIds },
            shouldRefreshOnPerspective = { refreshOnPerspective },
        )

        assertTrue(policy.shouldRender(1))
        assertFalse(policy.shouldRender(2))
        assertFalse(policy.shouldRefreshOnPerspective())

        visibleIds = setOf(2)
        refreshOnPerspective = true

        assertFalse(policy.shouldRender(1))
        assertTrue(policy.shouldRender(2))
        assertTrue(policy.shouldRefreshOnPerspective())
    }

    @Test
    fun `targeting dependencies remain in bootstrap wiring`() {
        val renderedEntities = readSource("features/render/RenderedEntities.kt")
        val initializer = readSource("bootstrap/liquidbounce/ClientManagerInitializer.kt")
        val globalTargets = readSource("features/global/GlobalSettingsTarget.kt")

        assertFalse(renderedEntities.contains("features.combat"))
        assertFalse(renderedEntities.contains("features.global"))
        assertTrue(renderedEntities.contains("visibilityPolicy.shouldRender(entity)"))
        assertTrue(renderedEntities.contains("visibilityPolicy.shouldRefreshOnPerspective()"))
        assertTrue(globalTargets.contains("get() = Targets.SELF in visual"))
        assertTrue(initializer.contains("RenderedEntities.installVisibilityPolicy("))
        assertTrue(initializer.contains("shouldRenderEntity = { entity -> entity.shouldBeShown() }"))
        assertTrue(initializer.contains("shouldRefreshOnPerspective = { GlobalSettingsTarget.rendersSelf }"))
        assertFalse(initializer.contains("features.combat.model.Targets"))
    }

    private fun readSource(relativePath: String) = Files.readString(
        Path.of("src/main/kotlin/net/ccbluex/liquidbounce/$relativePath")
    )
}
