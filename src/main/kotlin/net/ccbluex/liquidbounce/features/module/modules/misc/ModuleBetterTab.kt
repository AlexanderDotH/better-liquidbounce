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
package net.ccbluex.liquidbounce.features.module.modules.misc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.misc.ExternalClient
import net.ccbluex.liquidbounce.features.misc.ExternalClientUsers
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.misc.bettertab.BetterTabClientIndicators
import net.ccbluex.liquidbounce.features.module.modules.misc.bettertab.ClientLabelStyle
import net.ccbluex.liquidbounce.features.module.modules.misc.bettertab.ExternalClientDetection
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.ccbluex.liquidbounce.utils.text.PlainText
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component

/**
 * ModuleBetterTab
 *
 * @author sqlerrorthing
 * @since 12/28/2024
 **/
@Suppress("MagicNumber")
object ModuleBetterTab : ClientModule("BetterTab", ModuleCategories.RENDER) {

    val sorting by enumChoice("Sorting", Sorting.VANILLA)

    private val visibility by multiEnumChoice("Visibility",
        Visibility.HEADER,
        Visibility.FOOTER
    )

    @JvmStatic
    fun isVisible(visibility: Visibility) = visibility in this.visibility

    object Limits : ValueGroup("Limits") {
        val tabSize by int("TabSize", 80, 1..1000)
        val height by int("ColumnHeight", 20, 1..100)
    }

    object Highlight : ToggleableValueGroup(ModuleBetterTab, "Highlight", true) {
        open class HighlightColored(
            name: String,
            color: Color4b
        ) : ToggleableValueGroup(this, name, true) {
            val color by color("Color", color)
        }

        class Others(color: Color4b) : HighlightColored("Others", color) {
            val filter = tree(PlayerFilter())
        }

        val self = tree(HighlightColored("Self", Color4b(50, 193, 50, 80)))
        val friends = tree(HighlightColored("Friends", Color4b(16, 89, 203, 80)))
        val others = tree(Others(Color4b(35, 35, 35, 80)))
    }

    object AccurateLatency : ToggleableValueGroup(ModuleBetterTab, "AccurateLatency", true) {
        val suffix by boolean("AppendMSSuffix", true)
    }

    object PlayerHider : ToggleableValueGroup(ModuleBetterTab, "PlayerHider", false) {
        val filter = tree(PlayerFilter())
    }

    object ClientPlayers : ToggleableValueGroup(
        ModuleBetterTab,
        "ClientPlayers",
        true,
        aliases = listOf("LiquidBouncePlayers"),
    ) {
        val clients by multiEnumChoice("Clients", ExternalClient.entries)
        val labelStyle by enumChoice("LabelStyle", ClientLabelStyle.FULL)
        val legend by boolean("Legend", true)
        val ownershipSignals by boolean("OwnershipSignals", true)
        val color by color("Color", Color4b.LIQUID_BOUNCE)
    }

    @JvmStatic
    fun clientBadges(entry: PlayerInfo): Component? {
        if (!running || !ClientPlayers.running) {
            return null
        }

        return BetterTabClientIndicators.playerBadges(
            users = ExternalClientUsers.users(entry.profile.id),
            labelStyle = ClientPlayers.labelStyle,
            ownershipSignals = ClientPlayers.ownershipSignals,
            enabledClients = ClientPlayers.clients,
            liquidBounceColor = ClientPlayers.color,
        )
    }

    @JvmStatic
    fun clientHeader(serverHeader: Component?, entries: Collection<PlayerInfo>): Component? {
        if (!ClientPlayers.running || !ClientPlayers.legend) {
            return serverHeader
        }

        val listedUuids = entries.mapTo(hashSetOf()) { it.profile.id }
        val legend = BetterTabClientIndicators.legend(
            users = ExternalClientUsers.all().filter { it.uuid in listedUuids },
            labelStyle = ClientPlayers.labelStyle,
            ownershipSignals = ClientPlayers.ownershipSignals,
            enabledClients = ClientPlayers.clients,
            liquidBounceColor = ClientPlayers.color,
        )
        return BetterTabClientIndicators.appendLegend(serverHeader, legend)
    }

    @Suppress("unused", "InjectDispatcher")
    private val clientDetectionHandler = tickHandler(Dispatchers.IO) {
        val snapshot = withContext(Dispatchers.Minecraft) {
            if (!ClientPlayers.running) {
                return@withContext null
            }
            Triple(
                mc.connection?.onlinePlayers?.associate { it.profile.id to it.profile.name }.orEmpty(),
                ClientPlayers.clients.toSet(),
                ClientPlayers.ownershipSignals,
            )
        } ?: return@tickHandler
        val (players, clients, ownershipSignals) = snapshot
        ExternalClientDetection.refresh(
            players = players,
            clients = clients,
            ownershipSignals = ownershipSignals,
        )
    }

    val showGameMode by boolean("ShowGameMode", true)

    init {
        treeAll(
            Limits,
            Highlight,
            AccurateLatency,
            PlayerHider,
            ClientPlayers,
        )
    }

}

class PlayerFilter: ValueGroup("Filter") {
    private val filterBy by multiEnumChoice("FilterBy", Filter.entries)

    private val names by regexList("Names", linkedSetOf())

    fun isInFilter(entry: PlayerInfo) = names.any { regex ->
        filterBy.any { filter -> filter.matches(entry, regex) }
    }

    @Suppress("unused")
    private enum class Filter(
        override val tag: String,
        val matches: PlayerInfo.(Regex) -> Boolean
    ) : Tagged {
        DISPLAY_NAME("DisplayName", { regex ->
            this.tabListDisplayName?.string?.let { regex.matches(it) } ?: false
        }),

        PLAYER_NAME("PlayerName", { regex ->
            regex.matches(this.profile.name)
        })
    }
}

@Suppress("unused")
enum class Sorting(
    override val tag: String,
    val comparator: Comparator<PlayerInfo>?
) : Tagged {
    VANILLA("Vanilla", null),
    PING("Ping", Comparator.comparingInt { it.latency }),
    LENGTH("NameLength", Comparator.comparingInt { it.profile.name.length }),
    SCORE_LENGTH("DisplayNameLength", Comparator.comparingInt {
        (it.tabListDisplayName ?: PlainText.EMPTY).string.length
    }),
    ALPHABETICAL("Alphabetical", Comparator.comparing { it.profile.name }),
    REVERSE_ALPHABETICAL("ReverseAlphabetical", Comparator.comparing({ it.profile.name }, Comparator.reverseOrder())),
    NONE("None", { _, _ -> 0 })
}

@Suppress("unused")
enum class Visibility(
    override val tag: String
) : Tagged {
    HEADER("Header"),
    FOOTER("Footer"),
    NAME_ONLY("NameOnly")
}
