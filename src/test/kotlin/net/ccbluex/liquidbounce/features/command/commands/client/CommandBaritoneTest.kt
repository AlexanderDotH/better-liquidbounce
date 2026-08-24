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
package net.ccbluex.liquidbounce.features.command.commands.client

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneCommandOutput
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSetting
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingType
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingValue
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class CommandBaritoneTest {

    @Test
    fun `baritone command exposes native controls and short alias`() {
        val command = CommandBaritone.createCommand()

        assertEquals("baritone", command.name)
        assertEquals(listOf("b"), command.aliases)
        assertTrue(command.executable)
        assertEquals(listOf("gui", "status", "pause", "resume", "cancel"), command.subcommands.map { it.name })
    }

    @Test
    fun `baritone accepts an optional upstream command tail with completion`() {
        val command = CommandBaritone.createCommand()
        val tail = command.parameters.single()

        assertEquals("command", tail.name)
        assertFalse(tail.required)
        assertTrue(tail.vararg)
        assertNotNull(tail.autocompletionHandler)
    }

    @Test
    fun `setting command aliases are routed to LiquidBounce authoritative settings`() {
        listOf("set", "setting", "settings", "SeT").forEach { name ->
            assertEquals(BaritoneCommandTarget.SETTINGS, baritoneCommandTarget(listOf(name, "allowBreak")))
        }
        assertEquals(BaritoneCommandTarget.UPSTREAM, baritoneCommandTarget(listOf("goto", "10", "64", "10")))
    }

    @Test
    fun `unknown command tails delegate to Baritone command execution`() {
        var executedCommand: String? = null
        val messages = mutableListOf<String>()
        val facade = facadeProxy { method, arguments ->
            if (method == "executeCommand") {
                executedCommand = arguments.single() as String
                BaritoneResult.Success(BaritoneCommandOutput(listOf("Pathing started.")))
            } else {
                null
            }
        }
        val command = testCommand(facade, messages)

        execute(command, arrayOf("goto", "10", "64", "-20"))

        assertEquals("goto 10 64 -20", executedCommand)
        assertEquals(listOf("Pathing started."), messages)
    }

    @Test
    fun `set command updates the facade setting instead of Baritone native files`() {
        val setting = booleanSetting("allowBreak", true)
        var updatedName: BaritoneSettingName? = null
        var updatedValue: BaritoneSettingValue? = null
        var upstreamCommand: String? = null
        val facade = facadeProxy { method, arguments ->
            when (method) {
                "setting" -> setting
                "updateSetting" -> {
                    updatedName = when (val name = arguments[0]) {
                        is BaritoneSettingName -> name
                        is String -> BaritoneSettingName(name)
                        else -> error(
                            "Unexpected setting-name representation: " +
                                (name?.let { it::class.java.name } ?: "null")
                        )
                    }
                    updatedValue = arguments[1] as BaritoneSettingValue
                    BaritoneResult.Success(setting)
                }
                "executeCommand" -> {
                    upstreamCommand = arguments.single() as String
                    BaritoneResult.Success(BaritoneCommandOutput(emptyList()))
                }
                else -> null
            }
        }
        val command = testCommand(facade, mutableListOf())

        execute(command, arrayOf("set", "allowBreak", "off"))

        assertEquals(BaritoneSettingName("allowBreak"), updatedName)
        assertEquals(BaritoneSettingValue.BooleanValue(false), updatedValue)
        assertNull(upstreamCommand)
    }

    @Test
    fun `fallback completion comes from the facade`() {
        val facade = facadeProxy { method, _ ->
            if (method == "completions") {
                BaritoneResult.Success(listOf("goto", "goal"))
            } else {
                null
            }
        }
        val command = testCommand(facade, mutableListOf())
        val autocomplete = requireNotNull(command.parameters.single().autocompletionHandler)

        val suggestions = autocomplete.autocomplete("go", listOf("go")).toList()

        assertEquals(listOf("goto", "goal"), suggestions)
    }

    @Test
    fun `setting parser validates booleans and preserves canonical enum spelling`() {
        val boolean = booleanSetting("allowBreak", true)
        val enum = BaritoneSetting(
            name = BaritoneSettingName("pathEventTimeoutBehavior"),
            type = BaritoneSettingType.ENUM,
            value = BaritoneSettingValue.EnumValue("CANCEL_AND_SET_GOAL"),
            defaultValue = BaritoneSettingValue.EnumValue("CANCEL_AND_SET_GOAL"),
            description = "Controls path timeout behavior.",
            mutable = true,
            options = listOf("CANCEL_AND_SET_GOAL", "RECALCULATE"),
        )

        assertEquals(
            BaritoneSettingParseResult.Success(BaritoneSettingValue.BooleanValue(false)),
            parseBaritoneSettingValue(boolean, "no"),
        )
        assertEquals(
            BaritoneSettingParseResult.Success(BaritoneSettingValue.EnumValue("RECALCULATE")),
            parseBaritoneSettingValue(enum, "recalculate"),
        )
        assertTrue(parseBaritoneSettingValue(boolean, "sometimes") is BaritoneSettingParseResult.Failure)
    }

    private fun testCommand(facade: BaritoneFacade, messages: MutableList<String>) =
        CommandBaritone.createCommand(
            facadeProvider = { facade },
            openDashboard = {},
            feedback = { _, message -> messages += message },
        )

    private fun execute(command: Command, tail: Array<String>) {
        val context = Command.Handler.Context(command, arrayOf(tail))
        with(requireNotNull(command.handler)) {
            context()
        }
    }

    private fun booleanSetting(name: String, value: Boolean) = BaritoneSetting(
        name = BaritoneSettingName(name),
        type = BaritoneSettingType.BOOLEAN,
        value = BaritoneSettingValue.BooleanValue(value),
        defaultValue = BaritoneSettingValue.BooleanValue(true),
        description = "Controls $name.",
        mutable = true,
    )

    private fun facadeProxy(call: (String, List<Any?>) -> Any?): BaritoneFacade = Proxy.newProxyInstance(
        BaritoneFacade::class.java.classLoader,
        arrayOf(BaritoneFacade::class.java),
    ) { _, method, arguments ->
        call(method.name.substringBefore('-'), arguments.orEmpty().toList())
    } as BaritoneFacade

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
