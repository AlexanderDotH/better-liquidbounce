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
package net.ccbluex.liquidbounce.features.command.commands.ingame.fakeplayer

import com.mojang.authlib.GameProfile
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.client.removeMessage
import net.ccbluex.liquidbounce.utils.text.warning
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.roundToDecimalPlaces
import net.ccbluex.liquidbounce.utils.world.nextLocalEntityId
import net.minecraft.world.entity.Entity
import java.util.UUID

internal fun FakePlayerSession.spawn(args: Array<out Any>, moving: Boolean) {
    val name = args.getOrNull(0)?.toString() ?: "FakePlayer"
    val fakePlayer = createFakePlayer(name, moving)
    fakePlayer.id = world.nextLocalEntityId()
    if (!moving) {
        fakePlayer.loadAttributes(fromPlayer(player))
    }
    fakePlayers.add(fakePlayer)
    world.addEntity(fakePlayer)
    chat(
        regular(
            translation(
                "liquidbounce.command.fakeplayer.fakePlayerSpawned",
                fakePlayer.x.roundToDecimalPlaces(),
                fakePlayer.y.roundToDecimalPlaces(),
                fakePlayer.z.roundToDecimalPlaces(),
            ),
        ),
        metadata = MessageMetadata(id = "CFakePlayer#info"),
    )
}

private fun FakePlayerSession.createFakePlayer(name: String, moving: Boolean): FakePlayer {
    val profile = GameProfile(UUID.randomUUID(), name)
    return if (moving) {
        MovingFakePlayer(snapshots = snapshots.toTypedArray(), world, profile, fakePlayers::remove)
    } else {
        FakePlayer(world, profile, fakePlayers::remove)
    }
}

internal fun FakePlayerSession.remove(command: Command, name: String) {
    requireFakePlayers()
    val playersToRemove = fakePlayers.filterTo(ReferenceOpenHashSet()) {
        it.name.string.equals(name, ignoreCase = true)
    }
    if (playersToRemove.isEmpty()) {
        reportMissingPlayer(command, name)
        return
    }
    playersToRemove.forEach { fakePlayer -> removePlayer(command, fakePlayer) }
    fakePlayers.removeAll(playersToRemove)
}

private fun FakePlayerSession.reportMissingPlayer(command: Command, name: String) {
    mc.gui.hud.chat.removeMessage("CFakePlayer#info")
    val metadata = MessageMetadata(id = "CFakePlayer#info", remove = false)
    chat(warning(command.result("noFakePlayerNamed", name)), metadata = metadata)
    chat(regular(command.result("currentlySpawned")), metadata = metadata)
    fakePlayers.forEach { fakePlayer -> chat(regular("- " + fakePlayer.name.string), metadata = metadata) }
}

private fun removePlayer(command: Command, fakePlayer: FakePlayer) {
    world.removeEntity(fakePlayer.id, Entity.RemovalReason.KILLED)
    chat(
        regular(
            command.result(
                "fakePlayerRemoved",
                fakePlayer.x.roundToDecimalPlaces(),
                fakePlayer.y.roundToDecimalPlaces(),
                fakePlayer.z.roundToDecimalPlaces(),
            ),
        ),
        metadata = MessageMetadata(id = "CFakePlayer#info"),
    )
}

internal fun FakePlayerSession.clear() {
    requireFakePlayers()
    fakePlayers.forEach { world.removeEntity(it.id, Entity.RemovalReason.DISCARDED) }
    fakePlayers.clear()
}

internal fun FakePlayerSession.startRecording(command: Command) {
    if (recording) {
        throw CommandException(command.result("alreadyRecording"))
    }
    recording = true
    chat(regular(command.result("startedRecording")), metadata = MessageMetadata(id = "CFakePlayer#info"))
    notification("FakePlayer", command.result("startedRecordingNotification"), NotificationEvent.Severity.INFO)
}

internal fun FakePlayerSession.finishRecording(command: Command, args: Array<out Any>) {
    if (!recording) {
        throw CommandException(command.result("notRecording"))
    }
    if (snapshots.isEmpty()) {
        throw CommandException(command.result("somethingWentWrong"))
    }
    spawn(args, moving = true)
    stopRecording()
}

internal fun FakePlayerSession.stopRecording() {
    recording = false
    snapshots.clear()
    notification(
        "FakePlayer",
        translation("liquidbounce.command.fakeplayer.stoppedRecording"),
        NotificationEvent.Severity.INFO,
    )
}

internal fun checkInGame() {
    if (mc.level == null || mc.player == null) {
        throw CommandException(translation("liquidbounce.command.fakeplayer.mustBeInGame"))
    }
}

private fun FakePlayerSession.requireFakePlayers() {
    if (fakePlayers.isEmpty()) {
        throw CommandException(translation("liquidbounce.command.fakeplayer.noFakePlayers"))
    }
}
