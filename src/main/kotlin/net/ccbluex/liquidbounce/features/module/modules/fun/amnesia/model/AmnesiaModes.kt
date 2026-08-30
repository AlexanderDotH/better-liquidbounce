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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model

import net.ccbluex.liquidbounce.common.Tagged

enum class BhopStyle(override val tag: String) : Tagged {
    NORMAL("Normal"),
    LOW_HOP("LowHop"),
    STRAFE("Strafe"),
}

enum class CriticalsMode(override val tag: String) : Tagged {
    MICRO_HOP("MicroHop"),
    PACKET("Packet"),
    BOTH("Both"),
}

enum class ScaffoldStyle(override val tag: String) : Tagged {
    NORMAL("Normal"),
    TELLY("Telly"),
    TOWER("Tower"),
}

enum class ScaffoldYawMode(override val tag: String) : Tagged {
    MOVEMENT("Movement"),
    SNAP_45("Snap45"),
    REVERSE("Reverse"),
}

enum class VelocityMode(override val tag: String) : Tagged {
    FREEZE("Freeze"),
    NO_VELOCITY("NoVelocity"),
}
