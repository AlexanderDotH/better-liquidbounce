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
package net.ccbluex.liquidbounce.features.misc

import net.ccbluex.liquidbounce.render.engine.type.Color4b

enum class ClientBrand {
    LIQUIDBOUNCE,
    METEOR,
    WURST,
    FEATHER,
    LABYMOD,
    OPTIFINE,
    ESSENTIAL,
}

object ClientBrandColors {

    fun color(brand: ClientBrand, liquidBounceColor: Color4b): Color4b = when (brand) {
        ClientBrand.LIQUIDBOUNCE -> liquidBounceColor
        ClientBrand.METEOR -> Color4b.fromHex("#913DE2")
        ClientBrand.WURST -> Color4b.fromHex("#BF5E01")
        ClientBrand.FEATHER -> Color4b.fromHex("#D73232")
        ClientBrand.LABYMOD -> Color4b.fromHex("#2563EB")
        ClientBrand.OPTIFINE -> Color4b.fromHex("#5168CF")
        ClientBrand.ESSENTIAL -> Color4b.fromHex("#2997FF")
    }
}
