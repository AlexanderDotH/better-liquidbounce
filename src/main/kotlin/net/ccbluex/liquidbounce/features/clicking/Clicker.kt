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
package net.ccbluex.liquidbounce.features.clicking

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.common.debug.DebugParameterSink
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.clicking.pattern.ClickPattern
import net.ccbluex.liquidbounce.features.clicking.pattern.ClickPatternContext
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.hasCooldown
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import java.util.Random

/** Schedules and predicts Minecraft input clicks, including multiple clicks within one game tick. */
open class Clicker<T>(
    val parent: T,
    val keyBinding: KeyMapping,
    val itemCooldown: ItemCooldown? = ItemCooldown(),
    maxCps: Int = 60,
    name: String = "Clicker",
    simulateAttackKeyDown: Boolean = false,
) : ValueGroup(name, aliases = listOf("ClickScheduler")), EventListener, ClickPatternContext where T : EventListener {

    companion object {
        internal val RNG = Random()
        private var lastClickTime = 0L
        private val lastClickPassed
            get() = System.currentTimeMillis() - lastClickTime
    }

    override val random: Random
        get() = RNG

    // Options
    private val cps by intRange("CPS", 5..8, 1..maxCps, "clicks")
        .onChanged {
            fill()
        }

    private val pattern by enumChoice("Technique", ClickPatterns.STABILIZED)
        .onChanged {
            fill()
        }

    init {
        itemCooldown?.let(this::tree)
    }

    /**
     * When missing a hit, Minecraft has a cooldown before you can attack again.
     * This option will consider the cooldown before attacking again.
     *
     * This is useful for anti-cheats that detect if you are ignoring this cooldown.
     * Applies to the FailSwing feature as well.
     */
    private val attackCooldown: Value<Boolean>? = if (keyBinding == mc.options.keyAttack) {
        boolean("AttackCooldown", true)
    } else {
        null
    }

    private val passesAttackCooldown
        get() = !(attackCooldown?.get() == true && mc.missTime > 0)

    private val schedule = ClickSchedule()

    init {
        fill()
    }

    // Clicks that were executed by [click] in the current tick
    var clickAmount: Int? = null
        private set

    open val isClickTick: Boolean
        get() = willClickAt(0)

    val ticksUntilClick: Int
        get() = schedule.ticksUntilClick

    var ticksSinceLastClick = 0
        private set

    fun willClickAt(tick: Int = 1) = getClickAmount(tick) > 0

    fun getClickAmount(tick: Int = 0): Int {
        if (isEnforcedClick()) {
            return 1
        }
        return schedule.getClickAmount(tick)
    }

    private fun isEnforcedClick(tick: Int = 0): Boolean {
        val hasCooldown = player.hasCooldown
        DebugParameterSink.publish(this, "HasCooldown") { hasCooldown }
        if (hasCooldown && itemCooldown?.isCooldownPassed(tick) == true) {
            return true
        }

        return lastClickPassed + (tick * 50L) >= 1000L
    }

    init {
        if (simulateAttackKeyDown && keyBinding == mc.options.keyAttack) {
            handler<KeybindIsPressedEvent> { event ->
                val clickAmount = this.clickAmount ?: return@handler

                // It turns out, we only want to do this with [attackKey], otherwise
                // [useKey] will do unexpected things.
                if (event.keyBinding == keyBinding) {
                    // We want to simulate the click in order to
                    // allow the game to handle the logic as if we clicked
                    event.isPressed = clickAmount > 0
                }
            }
        }
    }

    /**
     * Clicks [cps] times per call (tick). If the cooldown is not passed, it will not click.
     * [block] should return true if the click was successful. Otherwise, it will not count as a click.
     */
    fun click(block: () -> Boolean) {
        val clicks = getClickAmount()

        publishClickDebugParameters(clicks)

        var clickAmount = 0

        repeat(clicks) {
            if (!passesAttackCooldown) {
                return@repeat
            }

            if (itemCooldown?.isCooldownPassed() != false && block()) {
                clickAmount++
                itemCooldown?.newCooldown()
                lastClickTime = System.currentTimeMillis()
                ticksSinceLastClick = 0
            }
        }

        this.clickAmount = clickAmount
    }

    /**
     * Suspendable counterpart to [click] for actions that complete across multiple game ticks.
     * A failed attempt stops the current batch so expensive actions are retried on a later scheduler tick.
     */
    protected suspend fun clickSuspending(block: suspend () -> Boolean) {
        val clicks = getClickAmount()

        publishClickDebugParameters(clicks)

        var successfulClicks = 0

        try {
            run clickBatch@{
                repeat(clicks) {
                    if (!passesAttackCooldown || itemCooldown?.isCooldownPassed() == false) {
                        return@repeat
                    }

                    if (!block()) {
                        return@clickBatch
                    }

                    successfulClicks++
                    itemCooldown?.newCooldown()
                    lastClickTime = System.currentTimeMillis()
                    ticksSinceLastClick = 0
                }
            }
        } finally {
            clickAmount = successfulClicks
        }
    }

    /**
     * Returns true when a click attempt can be executed right now.
     * This uses the same gating logic as [click] before invoking [block].
     */
    fun canExecuteClickNow(): Boolean {
        if (getClickAmount() <= 0) {
            return false
        }

        if (!passesAttackCooldown) {
            return false
        }

        return itemCooldown?.isCooldownPassed() != false
    }

    private fun publishClickDebugParameters(clicks: Int) {
        DebugParameterSink.publish(this, "Current Clicks") { clicks }
        DebugParameterSink.publish(this, "Peek Clicks") { schedule.getClickAmount(1) }
        DebugParameterSink.publish(this, "Last Click Passed") { lastClickPassed }
        DebugParameterSink.publish(this, "Attack Cooldown") { mc.missTime }
        DebugParameterSink.publish(this, "Item Cooldown") { itemCooldown?.cooldownProgress() ?: 0.0f }
    }

    @Suppress("unused")
    private val gameHandler = handler<GameTickEvent>(
        priority = EventPriorityConvention.FIRST_PRIORITY
    ) {
        ticksSinceLastClick++
        clickAmount = null

        schedule.advanceAndRefill(pattern.pattern, cps, this)

        DebugParameterSink.publish(this, "Click Technique") { pattern.tag }
        DebugParameterSink.publish(this, "Click Array", schedule::debugString)
    }

    private fun fill() {
        schedule.refill(pattern.pattern, cps, this)
    }

    override fun parent() = parent

    @Suppress("unused")
    enum class ClickPatterns(
        override val tag: String,
        val pattern: ClickPattern,
    ) : Tagged {
        STABILIZED("Stabilized", ClickPatternCatalog.stabilized),
        EFFICIENT("Efficient", ClickPatternCatalog.efficient),
        SPAMMING("Spamming", ClickPatternCatalog.spamming),
        DOUBLE_CLICK("DoubleClick", ClickPatternCatalog.doubleClick),
        DRAG("Drag", ClickPatternCatalog.drag),
        BUTTERFLY("Butterfly", ClickPatternCatalog.butterfly),
        NORMAL_DISTRIBUTION("NormalDistribution", ClickPatternCatalog.normalDistribution),
    }

}
