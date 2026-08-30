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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.litematica.application.LitematicaApplication
import net.ccbluex.liquidbounce.features.litematica.application.LitematicaApplicationCreation
import net.ccbluex.liquidbounce.features.litematica.application.LitematicaApplicationFactory
import net.ccbluex.liquidbounce.features.litematica.application.LitematicaApplicationSettings
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActivationMode
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaAirPlaceMode
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlannerSettings
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.features.chat.notification

object ModuleLitematica : ClientModule(
    name = "Litematica",
    category = ModuleCategories.WORLD,
    aliases = listOf("Printer"),
) {

    private val printerValue = boolean("Printer", false).onChanged { enabled ->
        application?.printerToggleChanged(enabled)
    }
    private var printer by printerValue

    private val activationValue = enumChoice("Activation", LitematicaActivationMode.LITEMATICA_KEY).onChanged {
        application?.activationChanged(it)
    }
    private val activation by activationValue
    private val range by float("Range", 4.5f, 1.0f..6.0f, "blocks")
    private val actionDelay by int("ActionDelay", 1, 1..20, "ticks")
    private val retryLimit by int("RetryLimit", 10, 1..50, "retries")
    private val airPlace by enumChoice("AirPlace", LitematicaAirPlaceMode.SMART)
    private val breakWrong by boolean("BreakWrong", true)
    private val breakExtra by boolean("BreakExtra", true)
    private val breakBlockEntities by boolean("BreakBlockEntities", false)
    private val fluids by boolean("Fluids", true)
    private val rotations = tree(RotationsValueGroup(this))
    private val swingMode by enumChoice("Swing", SwingMode.DO_NOT_HIDE)

    private val applicationFactory = LitematicaApplicationFactory()
    private var application: LitematicaApplication? = null

    init {
        tagBy(activationValue)
    }

    override fun onEnabled() {
        when (val created = applicationFactory.create(this, rotations) { printer = it }) {
            is LitematicaApplicationCreation.Ready -> {
                application = created.application
                created.application.enable(printer, settingsSnapshot())
            }
            is LitematicaApplicationCreation.Unavailable -> {
                notification(
                    name,
                    created.availability.detail,
                    NotificationEvent.Severity.ERROR,
                )
                enabled = false
            }
        }
    }

    override fun onDisabled() {
        application?.disable()
        application = null
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        application?.rotationUpdate(settingsSnapshot())
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        application?.tick(settingsSnapshot())
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        application?.worldChanged()
    }

    private fun settingsSnapshot() = LitematicaApplicationSettings(
        planner = LitematicaPlannerSettings(
            activation = activation,
            range = range.toDouble(),
            actionDelayTicks = actionDelay,
            retryLimit = retryLimit,
            airPlace = airPlace,
            breakWrong = breakWrong,
            breakExtra = breakExtra,
            breakBlockEntities = breakBlockEntities,
            fluids = fluids,
        ),
        swingMode = swingMode,
    )
}
