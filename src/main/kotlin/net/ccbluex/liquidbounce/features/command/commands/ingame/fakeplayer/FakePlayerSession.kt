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

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet

internal class FakePlayerSession {
    val fakePlayers = ReferenceOpenHashSet<FakePlayer>()
    var recording = false
    val snapshots = ArrayList<PosPoseSnapshot>()
}
