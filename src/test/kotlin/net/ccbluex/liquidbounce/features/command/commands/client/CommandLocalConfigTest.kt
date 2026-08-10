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

package net.ccbluex.liquidbounce.features.command.commands.client

import net.ccbluex.liquidbounce.config.autoconfig.LocalConfigLoadSelection
import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandLocalConfigTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private val normalModule = ClientModule("LocalConfigCommandCombat", ModuleCategories.COMBAT)
    private val otherNormalModule = ClientModule("LocalConfigCommandMovement", ModuleCategories.MOVEMENT)
    private val renderModule = ClientModule("LocalConfigCommandRender", ModuleCategories.RENDER)

    private val modules = listOf(normalModule, otherNormalModule, renderModule)

    @Test
    fun `load exposes an optional vararg selection with autocomplete`() {
        val load = CommandLocalConfig.createCommand().subcommands.single { it.name == "load" }
        val selection = load.parameters.single { it.name == "selection" }

        assertFalse(selection.required)
        assertTrue(selection.vararg)
        assertTrue(selection.verifier != null)
        assertTrue(selection.autocompletionHandler != null)
    }

    @Test
    fun `render selection is case insensitive`() {
        val result = CommandLocalConfig.parseLoadSelectionToken("ReNdEr", modules)

        val parsed = assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, result)
        assertSame(CommandLocalConfig.LoadSelectionToken.Render, parsed.mappedResult)
    }

    @Test
    fun `comma separated module groups are parsed and unioned with render`() {
        val first = parseModules("localconfigcommandcombat,LOCALCONFIGCOMMANDMOVEMENT")
        val duplicate = parseModules("LocalConfigCommandCombat")

        val selection = CommandLocalConfig.combineLoadSelection(
            listOf(first, duplicate, CommandLocalConfig.LoadSelectionToken.Render),
        )

        assertEquals(
            LocalConfigLoadSelection(
                modules = linkedSetOf(normalModule, otherNormalModule),
                includeRender = true,
            ),
            selection,
        )
    }

    @Test
    fun `explicit render module without render opt in is rejected`() {
        val token = parseModules(renderModule.name)

        val exception = assertThrows(CommandLocalConfig.RenderOptInRequiredException::class.java) {
            CommandLocalConfig.combineLoadSelection(listOf(token))
        }

        assertEquals(setOf(renderModule), exception.modules)
    }

    @Test
    fun `render opt in represents all render modules rather than a selected render subset`() {
        val render = parseModules(renderModule.name)

        val selection = CommandLocalConfig.combineLoadSelection(
            listOf(render, CommandLocalConfig.LoadSelectionToken.Render),
        )

        assertTrue(selection.includeRender)
        assertTrue(selection.modules.isEmpty())
    }

    @Test
    fun `autocomplete preserves a comma separated module prefix`() {
        assertTrue(
            CommandLocalConfig.autocompleteLoadSelection(
                "LocalConfigCommandCombat,localconfigcommandm",
                modules,
            )
                .contains("LocalConfigCommandCombat,LocalConfigCommandMovement"),
        )
    }

    @Test
    fun `autocomplete includes the case insensitive render token`() {
        assertTrue(CommandLocalConfig.autocompleteLoadSelection("RE", modules).contains("render"))
    }

    private fun parseModules(source: String): CommandLocalConfig.LoadSelectionToken.Modules {
        val result = CommandLocalConfig.parseLoadSelectionToken(source, modules)
        val parsed = assertInstanceOf(Parameter.Verificator.Result.Ok::class.java, result)
        return assertInstanceOf(CommandLocalConfig.LoadSelectionToken.Modules::class.java, parsed.mappedResult)
    }
}
