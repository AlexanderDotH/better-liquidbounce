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
package net.ccbluex.liquidbounce.features.command.commands.ingame

import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.server.ServerObserver
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.variable
import net.ccbluex.liquidbounce.utils.text.warning
import net.ccbluex.liquidbounce.utils.math.roundToDecimalPlaces
import net.ccbluex.liquidbounce.utils.text.hideSensitiveAddress
import net.ccbluex.liquidbounce.utils.text.joinToText
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.chat.HoverEvent

internal fun printServerInformation(command: Command, detectionTags: List<String>) {
    val serverInfo = network.serverData
    val resolvedAddress = ServerObserver.serverAddress?.toString()
    val tps = ServerObserver.tps
    val ping = network.getPlayerInfo(player.uuid)?.latency ?: 0
    val advertisedVersion = "${serverInfo?.version?.string} (${serverInfo?.protocol})"
    val detectedVersion = ServerObserver.serverVersion ?: "<= 1.20.4"
    chat(warning(command.result("header")))
    printServerIdentity(command, serverInfo, resolvedAddress, advertisedVersion, detectedVersion)
    printServerPerformance(command, tps, ping)
    printServerTraffic(command)
    printAntiCheat(command, serverInfo?.ip ?: "")
    printHostingInformation(command)
    printPluginInformation(command)
    printDetectionMethods(command, detectionTags)
}

private fun printServerIdentity(
    command: Command,
    serverInfo: ServerData?,
    resolvedAddress: String?,
    advertisedVersion: String,
    detectedVersion: String,
) {
    command.printStyledText("address", serverInfo?.ip?.hideSensitiveAddress())
    command.printStyledText("resolvedAddress", resolvedAddress?.hideSensitiveAddress())
    command.printStyledText("serverId", ServerObserver.serverId)
    command.printStyledText("serverType", ServerObserver.serverType?.tag)
    command.printStyledText("brand", network.serverBrand())
    command.printStyledText("advertisedVersion", advertisedVersion)
    command.printStyledText(
        "detectedVersion",
        detectedVersion,
        hover = HoverEvent.ShowText(command.result("detectedVersion.description", variable(detectedVersion))),
    )
}

private fun printServerPerformance(command: Command, tps: Double, ping: Int) {
    val tpsText = if (tps.isNaN()) command.result("nan").string else tps.roundToDecimalPlaces(2).toString()
    command.printStyledText("tps", tpsText)
    command.printStyledText("ping", ping.toString())
}

private fun printServerTraffic(command: Command) {
    val channels = ServerObserver.payloadChannels.map { variable(it.toString()) }.joinToText(regular(", "))
    command.printStyledComponent("channels", channels)
    val transactions = ServerObserver.transactions.map { variable(it.toString()) }.joinToText(regular(", "))
    command.printStyledComponent("transactions", transactions)
    val differences = ServerObserver.transactions.windowed(2) { it[1] - it[0] }
        .map { variable(it.toString()) }
        .joinToText(regular(", "))
    command.printStyledComponent("transactionDifferences", differences)
}

private fun printAntiCheat(command: Command, address: String) {
    val antiCheat = ServerObserver.guessAntiCheat(address)?.let(::variable) ?: markAsError("N/A")
    command.printStyledComponent(
        "guessedAntiCheat",
        antiCheat,
        hover = HoverEvent.ShowText(command.result("guessedAntiCheat.description")),
    )
}

private fun printHostingInformation(command: Command) {
    val data = ServerObserver.hostingInformation ?: return
    command.printStyledText("hostingIp", data.ip)
    command.printStyledText("hostingHostname", data.hostname)
    command.printStyledText("hostingOrganization", data.org)
    command.printStyledText("hostingCountry", data.country)
    command.printStyledText("hostingCity", data.city)
    command.printStyledText("hostingRegion", data.region)
}

private fun printPluginInformation(command: Command) {
    val plugins = ServerObserver.plugins ?: return
    val pluginList = ServerObserver.formattedPluginList?.joinToText(regular(", ")) ?: markAsError("N/A")
    chat(regular(command.result("plugins", variable(plugins.size.toString()), pluginList)))
}

private fun printDetectionMethods(command: Command, tags: List<String>) {
    if (tags.isEmpty()) {
        return
    }
    val detectionList = tags.map(::variable).joinToText(regular(", "))
    command.printStyledComponent("detectParameter", detectionList, formatting = ::warning)
}
