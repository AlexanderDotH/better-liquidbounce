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

package net.ccbluex.liquidbounce.features.global

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.config.types.list.MultiChoiceListValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class GlobalSettingsRichPresenceContractTest {

    @Test
    fun `configuration wire names order aliases and defaults remain stable`() {
        val settings = GlobalSettingsRichPresence

        assertEquals("RichPresence", settings.name)
        assertEquals(listOf("DiscordPresence"), settings.aliases)
        assertTrue(settings.enabled)
        assertEquals(
            listOf(
                "Enabled",
                "ActivityType",
                "StatusDisplayType",
                "Separator",
                "DetailsParts",
                "StateParts",
                "LargeImage",
                "SmallImage",
            ),
            settings.inner.map { it.name },
        )
        assertEquals("Competing", settings.setting("ActivityType").selectedTag())
        assertEquals("Name", settings.setting("StatusDisplayType").selectedTag())
        assertEquals(" - ", settings.setting("Separator").get())
        assertEquals(listOf("ClientName", "ClientVersion"), settings.setting("DetailsParts").selectedTags())
        assertEquals(listOf("ClientCommit", "Modules"), settings.setting("StateParts").selectedTags())
    }

    @Test
    fun `large and small image defaults retain their independent setting order`() {
        val groups = GlobalSettingsRichPresence.inner.filterIsInstance<ToggleableValueGroup>().associateBy { it.name }
        val large = requireNotNull(groups["LargeImage"])
        val small = requireNotNull(groups["SmallImage"])

        assertTrue(large.enabled)
        assertFalse(small.enabled)
        assertEquals(listOf("Enabled", "Asset", "Parts"), large.inner.map { it.name })
        assertEquals(listOf("Enabled", "Asset", "Parts"), small.inner.map { it.name })
        assertEquals("Logo", large.setting("Asset").selectedTag())
        assertEquals("Logo", small.setting("Asset").selectedTag())
        assertEquals(listOf("ProtocolVersion"), large.setting("Parts").selectedTags())
        assertEquals(listOf("ClientBranch", "ClientCommit"), small.setting("Parts").selectedTags())
    }

    @Test
    fun `IPC lifecycle and activity construction retain observable order`() {
        val source = Path.of(RICH_PRESENCE_SOURCE).readText()

        source.assertInOrder(
            "override fun onEnabled()",
            "timestamp = Instant.now()",
            "doNotTryToConnect = false",
        )
        source.assertInOrder(
            "private val updateCycle",
            "waitTicks(20)",
            "if (enabled)",
            "connectIpc()",
            "shutdownIpc()",
            "val ipcClient = ipcClient",
            "ipcClient.state != DiscordIpcClient.State.CONNECTED",
            "val activity = DiscordActivity(",
            "type = activityType.activityType",
            "statusDisplayType = statusDisplayType.statusDisplayType",
            "startTimestamp = timestamp",
            "details = buildText(detailsParts)",
            "state = buildText(stateParts)",
            "largeImage =",
            "smallImage =",
            "buttons = buttons",
            "ipcClient.sendActivity(activity)",
        )
        source.assertInOrder(
            "private val shutdownHandler",
            "shutdownIpc()",
        )
    }

    private fun ToggleableValueGroup.setting(name: String): Value<*> = inner.single { it.name == name }

    private fun Value<*>.selectedTag(): String = ((this as ChoiceListValue<*>).get() as Tagged).tag

    private fun Value<*>.selectedTags(): List<String> =
        (this as MultiChoiceListValue<*>).get().map { (it as Tagged).tag }

    private fun String.assertInOrder(vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private companion object {
        const val RICH_PRESENCE_SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/global/GlobalSettingsRichPresence.kt"
    }
}
