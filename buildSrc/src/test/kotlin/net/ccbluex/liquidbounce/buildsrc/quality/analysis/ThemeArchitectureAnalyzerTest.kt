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

package net.ccbluex.liquidbounce.buildsrc.quality.analysis

import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeArchitectureAnalyzerTest {

    private val analyzer = ThemeArchitectureAnalyzer(Path.of("..").toAbsolutePath().normalize())

    @Test
    fun `route features cannot import another route internals`() {
        val source = theme(
            "src-theme/src/routes/hud/Hud.svelte",
            "<script>\nimport Setting from '../clickgui/setting/Setting.svelte'\n</script>",
        )

        val finding = analyzer.analyze(listOf(source)).single()

        assertEquals("LB-ARCH-001", finding.ruleId)
        assertEquals(2, finding.line)
        assertEquals("theme-route:hud->clickgui", finding.subject)
        assertTrue("src-theme/src/shared" in finding.recommendation)
    }

    @Test
    fun `integration cannot depend on a concrete route store`() {
        val source = theme(
            "src-theme/src/integration/accounts.ts",
            "import { isLoggingIn } from '../routes/menu/altmanager/altmanager_store'",
        )

        val finding = analyzer.analyze(listOf(source)).single()

        assertEquals("theme-layer:integration->menu", finding.subject)
        assertTrue("route-owned adapter" in finding.recommendation)
    }

    @Test
    fun `same route shared core and composition roots remain allowed`() {
        val files = listOf(
            theme(
                "src-theme/src/routes/hud/Hud.svelte",
                "<script>import Child from './elements/Child.svelte'; import { listen } from '../../integration/ws'</script>",
            ),
            theme("src-theme/src/App.svelte", "<script>import Hud from './routes/hud/Hud.svelte'</script>"),
            theme(
                "src-theme/src/dev/hud-preview/main.ts",
                "const view = import('../../routes/hud/Hud.svelte')",
            ),
        )

        assertTrue(analyzer.analyze(files).isEmpty())
    }

    @Test
    fun `dynamic cross route import is also blocked`() {
        val source = theme(
            "src-theme/src/routes/menu/Menu.svelte",
            "<script>const settings = import('../clickgui/Settings.svelte')</script>",
        )

        assertEquals("theme-route:menu->clickgui", analyzer.analyze(listOf(source)).single().subject)
    }

    private fun theme(path: String, content: String) = SourceFile(path, content, SourceKind.UI)
}
