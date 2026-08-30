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
package net.ccbluex.liquidbounce.common.interop

/**
 * Neutral marker for a HUD component that may be serialized as an interop event payload.
 *
 * The concrete component remains owned by the theme integration. Serialization intentionally
 * uses the runtime implementation so the established WebSocket representation stays unchanged.
 */
interface HudComponentPayload
