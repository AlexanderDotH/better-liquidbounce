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

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.features.module.modules.misc.notifier.NotifierItemTracker
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.collection.itemSortedSetOf
import net.ccbluex.liquidbounce.utils.network.isDeathProtection
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import java.util.UUID

/**
 * Notifier module
 *
 * Notifies you about all kinds of events.
 */
object ModuleNotifier : ClientModule("Notifier", ModuleCategories.MISC) {

    init {
        doNotIncludeAlways()
    }

    private val joinMessages by boolean("JoinMessages", true)
    private val joinMessageFormat by text("JoinMessageFormat", "%s joined")

    private val leaveMessages by boolean("LeaveMessages", true)
    private val leaveMessageFormat by text("LeaveMessageFormat", "%s left")

    private val gameModeMessages by boolean("GameModeMessages", false)
    private val gameModeMessageFormat by text("GameModeMessageFormat", "%s changed their game mode to %s")

    private val itemConsumptionMessages by boolean("ItemConsumptionMessages", true)
    private val itemConsumptionMessageFormat by text("ItemConsumptionMessageFormat", $$"%1$s used %2$s")

    private val heldItemMessages by boolean("HeldItemMessages", false)
    private val heldItemMessageFormat by text("HeldItemMessageFormat", $$"%1$s holds %2$s x%3$s in %4$s")
    private val heldItems by items("HeldItems", itemSortedSetOf(Items.END_CRYSTAL, Items.ENCHANTED_GOLDEN_APPLE))

    private val totemPopMessages by boolean("TotemPopMessages", true)
    private val totemPopMessageFormat by text("TotemPopMessageFormat", $$"%1$s popped a totem %2$s times")

    private val useNotification by boolean("UseNotification", false)

    private val uuidNameCache = Object2ObjectOpenHashMap<UUID, String>()
    private val uuidGameModeCache = Object2ObjectOpenHashMap<UUID, GameType>()
    private val totemPopCounter = Object2IntOpenHashMap<UUID>()
    private val itemTracker = NotifierItemTracker()

    override fun onEnabled() {
        for (entry in network.onlinePlayers) {
            uuidNameCache[entry.profile.id] = entry.profile.name
            uuidGameModeCache[entry.profile.id] = entry.gameMode
        }
    }

    override fun onDisabled() {
        uuidNameCache.clear()
        uuidGameModeCache.clear()
        itemTracker.clear()
        totemPopCounter.clear()
    }

    val packetHandler = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            is ClientboundPlayerInfoUpdatePacket -> mc.execute {
                val actions = packet.actions()
                val entries = packet.entries()

                if (ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER in actions) {
                    for (entry in entries) {
                        handlePlayerAdd(entry)
                    }
                }

                if (ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE in actions) {
                    val isInitializing = ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER in actions

                    for (entry in entries) {
                        handleGameModeUpdate(entry, isInitializing)
                    }
                }
            }

            is ClientboundPlayerInfoRemovePacket -> mc.execute {
                for (uuid in packet.profileIds) {
                    val profileName = uuidNameCache.remove(uuid)
                    uuidGameModeCache.remove(uuid)

                    if (profileName != null && profileName.length >= 2) {
                        if (leaveMessages) {
                            sendNotifierMessage(leaveMessageFormat.format(profileName))
                        }
                    }
                }
            }

            is ClientboundEntityEventPacket -> if (packet.isDeathProtection) {
                mc.execute {
                    val entity = packet.getEntity(world) as? Player ?: return@execute
                    if (entity === mc.player || FriendManager.isFriend(entity.name.string)) return@execute

                    totemPopCounter.addTo(entity.uuid, 1)

                    if (totemPopMessages) {
                        sendNotifierMessage(
                            totemPopMessageFormat.format(
                                entity.name.string,
                                totemPopCounter.getInt(entity.uuid)
                            )
                        )
                    }
                }
            }

            is ClientboundDisconnectPacket, is ClientboundLoginPacket -> mc.execute(totemPopCounter::clear)
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        itemTracker.update(
            players = world.players(),
            isBot = ModuleAntiBot::isBot,
            trackConsumption = itemConsumptionMessages,
            consumptionFormat = itemConsumptionMessageFormat,
            trackHeldItems = heldItemMessages,
            heldItemFormat = heldItemMessageFormat,
            trackedItems = heldItems,
            emit = ::sendNotifierMessage,
        )
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        itemTracker.clear()
    }

    private fun handlePlayerAdd(entry: ClientboundPlayerInfoUpdatePacket.Entry) {
        val profile = entry.profile ?: return
        val profileName = profile.name

        if (profileName == null || profileName.length <= 2) {
            return
        }

        uuidNameCache[profile.id] = profileName

        if (joinMessages) {
            sendNotifierMessage(joinMessageFormat.format(profileName))
        }
    }

    private fun handleGameModeUpdate(entry: ClientboundPlayerInfoUpdatePacket.Entry, isInitializing: Boolean) {
        val previousGameMode = uuidGameModeCache.put(entry.profileId, entry.gameMode)

        if (isInitializing || previousGameMode == null || previousGameMode == entry.gameMode || !gameModeMessages) {
            return
        }

        val profileName = uuidNameCache[entry.profileId] ?: return
        sendNotifierMessage(gameModeMessageFormat.format(profileName, entry.gameMode))
    }

    private fun sendNotifierMessage(message: String) {
        if (useNotification) {
            notification(this.name, message, NotificationEvent.Severity.INFO)
        } else {
            chat(regular(message))
        }
    }

}
