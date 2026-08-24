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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.features.baritone.BaritoneFeature
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLifecycleEvent
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneNavigationMode
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories

/** General-purpose Baritone navigation with LiquidBounce-owned configuration and controls. */
object ModuleBaritone : ClientModule(
    name = "Baritone",
    category = ModuleCategories.PLAYER,
    state = false,
) {

    private val navigationModes = choices(
        "NavigationMode",
        FlyNavigation,
        arrayOf(FlyNavigation, WalkNavigation),
    )

    private object FlyNavigation : Mode("Fly") {

        override val parent: ModeValueGroup<Mode>
            get() = navigationModes

        val armTimeoutSeconds by int("ArmTimeout", 10, 1..60, "seconds")
        val maxRestarts by int("MaxRestarts", 3, 0..10)
        val retryDistanceBlocks by int("RetryDistance", 32, 1..256, "blocks")

    }

    private object WalkNavigation : Mode("Walk") {

        override val parent: ModeValueGroup<Mode>
            get() = navigationModes

    }

    private val defaultConflictModules = listOf(
        "FightBot",
        "SpearKill",
        "MaceKill",
        "ClickTp",
        "Teleport",
        "Phase",
        "Clip",
        "VClip",
        "Fly",
        "Speed",
        "LongJump",
        "AutoDodge",
        "TargetStrafe",
        "Freeze",
        "ElytraFly",
        "AutoWalk",
        "Blink",
        "FreeCam",
        "Scaffold",
    )

    val pauseOnUserInput by boolean("PauseOnUserInput", true)

    val resumeDelayTicks by int("ResumeDelay", 10, 0..100, "ticks")

    val conflictModuleNames by registryMutableList(
        "ConflictModules",
        defaultConflictModules.toMutableList(),
        ValueType.CLIENT_MODULE,
    )

    @Suppress("unused")
    private val openDashboard = action("OpenDashboard", callback = BaritoneFeature::openDashboard)

    internal val navigationMode: BaritoneNavigationMode
        get() = when (navigationModes.activeMode) {
            FlyNavigation -> BaritoneNavigationMode.FLY
            WalkNavigation -> BaritoneNavigationMode.WALK
            else -> error("Unsupported Baritone navigation mode: ${navigationModes.activeMode.name}")
        }

    internal val flyNavigationConfig: BaritoneFlyNavigationConfig
        get() = BaritoneFlyNavigationConfig(
            armTimeoutTicks = FlyNavigation.armTimeoutSeconds * TICKS_PER_SECOND,
            maxRestarts = FlyNavigation.maxRestarts,
            retryDistanceBlocks = FlyNavigation.retryDistanceBlocks,
        )

    override fun onDisabled() {
        releaseControl(BaritoneFeature.facadeOrNull())
    }

    internal fun releaseControl(facade: BaritoneFacade?) {
        facade ?: return
        facade.lifecycle(BaritoneLifecycleEvent.DISABLE)
    }

    private const val TICKS_PER_SECOND = 20

}

internal data class BaritoneFlyNavigationConfig(
    val armTimeoutTicks: Int,
    val maxRestarts: Int,
    val retryDistanceBlocks: Int,
)
