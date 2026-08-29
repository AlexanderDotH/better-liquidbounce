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
package net.ccbluex.liquidbounce.features.litematica.render

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.utils.client.mc

object LitematicaRenderBridge : LitematicaRenderSink, EventListener {
    private val stateStore = LitematicaRenderStateStore()

    override fun update(snapshot: LitematicaRenderSnapshot) {
        stateStore.update(snapshot)
    }

    override fun clear() {
        stateStore.clear()
    }

    fun snapshot(): LitematicaRenderSnapshot = stateStore.snapshot()

    @Suppress("unused")
    private val worldRenderHandler = handler<WorldRenderEvent> { event ->
        if (HideAppearance.isHidingNow) return@handler
        LitematicaWorldTargetRenderer.render(event, snapshot().targets)
    }

    @Suppress("unused")
    private val overlayRenderHandler = handler<OverlayRenderEvent> { event ->
        if (HideAppearance.isHidingNow || mc.gui.hud.isHidden) return@handler
        snapshot().hud?.let { LitematicaHudRenderer.render(event.context, it) }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        clear()
    }
}
