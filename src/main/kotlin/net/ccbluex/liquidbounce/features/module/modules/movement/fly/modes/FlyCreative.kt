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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationInput
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.math.withLength
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

private data class FlightAbilitiesSnapshot(
    val mayfly: Boolean,
    val flying: Boolean,
    val flyingSpeed: Float
)

private fun LocalPlayer.flightAbilitiesSnapshot() = FlightAbilitiesSnapshot(
    abilities.mayfly,
    abilities.flying,
    abilities.flyingSpeed
)

private fun LocalPlayer.restoreFlightAbilities(snapshot: FlightAbilitiesSnapshot) {
    val hasVanillaFlight = isCreative || isSpectator

    abilities.mayfly = snapshot.mayfly || hasVanillaFlight
    abilities.flyingSpeed = snapshot.flyingSpeed

    if (!abilities.mayfly) {
        abilities.flying = false
        return
    }

    if (!hasVanillaFlight) {
        abilities.flying = snapshot.flying
    }
}

internal object FlyCreative : Mode("Creative"), FlyAutomationProfile {

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = true,
        landing = true,
        kind = FlyAutomationKind.CONTINUOUS,
    )

    override fun automationReadiness() = FlyAutomationReadiness.Ready


    private var previousAbilities = FlightAbilitiesSnapshot(mayfly = false, flying = false, flyingSpeed = 0.05f)

    private val speed by float("Speed", 0.1f, 0.1f..5f)

    private object SprintSpeed : ToggleableValueGroup(this, "SprintSpeed", true) {
        val speed by float("Speed", 0.1f, 0.1f..5f)
    }

    init {
        tree(SprintSpeed)
    }

    private val maxVelocity by float("MaxVelocity", 4f, 1f..20f)

    private val bypassVanillaCheck by boolean("BypassVanillaCheck", true)

    private val forceFlight by boolean("ForceFlight", true)

    override fun enable() {
        previousAbilities = player.flightAbilitiesSnapshot()
        player.abilities.mayfly = true
    }

    private fun shouldFlyDown(): Boolean {
        if (!bypassVanillaCheck) return false
        if (player.tickCount % 40 != 0) return false

        // check if the player is above a block or in midair
        // if the player is right above a block, we don't need to fly down
        if (world.getBlockStates(player.boundingBox.move(0.0, -0.55, 0.0)).anyMatch { !it.isAir }) return false

        return true
    }

    val repeatable = tickHandler {
        player.abilities.flyingSpeed =
            if (FlyAutomationInput.sprint(mc.options.keySprint.isDown) && SprintSpeed.enabled) {
                SprintSpeed.speed
            } else {
                speed
            }

        if (forceFlight) player.abilities.flying = true

        if (player.deltaMovement.lengthSqr() > maxVelocity.sq()) {
            player.deltaMovement = player.deltaMovement.withLength(maxVelocity.toDouble())
        }

        if (shouldFlyDown()) {
            network.send(MovePacketType.POSITION_AND_ON_GROUND.generatePacket())
        }

    }

    val packetHandler = handler<PacketEvent> { event ->
        if (shouldFlyDown() && event.packet is ServerboundMovePlayerPacket) {
            event.packet.y = player.yLast - 0.04
        }
    }

    override fun disable() {
        player.restoreFlightAbilities(previousAbilities)
    }

}
