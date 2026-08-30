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

package net.ccbluex.liquidbounce.features.autoconfig

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.input.InputBind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class LocalConfigCodecTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    private val normalA = FixtureModule("LocalConfigCombatA", ModuleCategories.COMBAT)
    private val normalB = FixtureModule("LocalConfigMovementB", ModuleCategories.MOVEMENT)
    private val renderA = FixtureModule("LocalConfigRenderA", ModuleCategories.RENDER)
    private val renderB = FixtureModule("LocalConfigRenderB", ModuleCategories.RENDER)
    private val allModules = listOf(normalA, normalB, renderA, renderB)

    @Test
    fun `local JSON preserves public modules and adds a sorted render snapshot`() {
        val base = localConfig(
            standardEntries = listOf(publicModuleEntry(normalA)),
        )
        val originalStandardModules = base.getAsJsonObject("modules").deepCopy()

        val result = LocalConfigCodec.createLocalConfigJson(
            base,
            listOf(renderB, normalA, renderA),
        )

        assertEquals(originalStandardModules, result.getAsJsonObject("modules"))
        assertEquals(
            listOf(renderA.name, renderB.name),
            result.getAsJsonObject("renderModules").moduleNames(),
        )
        assertFalse(result.getAsJsonObject("modules").moduleNames().contains(renderA.name))
        assertFalse(base.has("renderModules"), "the supplied public AutoConfig tree must not be mutated")
    }

    @Test
    fun `render snapshot always contains enabled bind hidden and private state`() {
        val savedBind = InputBind(
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            InputBind.BindAction.HOLD,
        )
        renderA.enabledValue.setRaw(true)
        renderA.bindValue.setRaw(savedBind)
        renderA.hiddenValue.setRaw(true)
        renderA.publicSetting.setRaw(true)
        renderA.privateSetting.setRaw(true)

        val result = LocalConfigCodec.createLocalConfigJson(localConfig(), listOf(renderA))
        val renderEntry = result.getAsJsonObject("renderModules").moduleEntry(renderA.name)

        assertEquals(true, renderEntry.setting("Enabled").asBoolean)
        assertEquals(true, renderEntry.setting("Hidden").asBoolean)
        assertEquals(true, renderEntry.setting("PublicSetting").asBoolean)
        assertEquals(true, renderEntry.setting("PrivateSetting").asBoolean)
        assertNotNull(renderEntry.setting("Bind"))
    }

    @Test
    fun `default load plan excludes render from legacy modules and dedicated snapshot`() {
        val json = localConfig(
            standardEntries = listOf(fileModuleEntry(normalA), fileModuleEntry(renderA)),
            renderEntries = listOf(fileModuleEntry(renderA)),
        )

        val plan = LocalConfigCodec.createLoadPlan(json, LocalConfigLoadSelection(), allModules)
        val calls = mutableListOf<String>()

        plan.apply(
            loadStandard = { _, modules -> calls += "standard:${modules.joinToString { it.name }}" },
            loadRender = { _, _ -> calls += "render" },
        )

        assertEquals(listOf(normalA, normalB), plan.standardModules)
        assertNull(plan.renderSnapshot)
        assertEquals(listOf("standard:${normalA.name}, ${normalB.name}"), calls)
    }

    @Test
    fun `default load with only render modules strips the standard module tree`() {
        val json = localConfig(
            standardEntries = listOf(fileModuleEntry(renderA)),
            renderEntries = listOf(fileModuleEntry(renderA)),
        )

        val plan = LocalConfigCodec.createLoadPlan(
            json,
            LocalConfigLoadSelection(),
            listOf(renderA),
        )

        assertTrue(plan.standardModules.isEmpty())
        assertFalse(plan.standardConfig.has("modules"))
        assertNull(plan.renderSnapshot)
    }

    @Test
    fun `render opt in restores all normal and render modules`() {
        val json = localConfig(
            standardEntries = allModules.map(::fileModuleEntry),
            renderEntries = listOf(fileModuleEntry(renderA), fileModuleEntry(renderB)),
        )
        val plan = LocalConfigCodec.createLoadPlan(
            json,
            LocalConfigLoadSelection(includeRender = true),
            allModules,
        )
        val calls = mutableListOf<Pair<String, List<ValueGroup>>>()

        plan.apply(
            loadStandard = { _, modules -> calls += "standard" to modules },
            loadRender = { _, modules -> calls += "render" to modules },
        )

        assertEquals(allModules.sortedBy { it.name.lowercase() }, plan.standardModules)
        assertEquals(listOf(renderA, renderB), plan.renderModules)
        assertEquals(listOf("standard", "render"), calls.map { it.first })
        assertEquals(listOf(renderA, renderB), calls.last().second)
    }

    @Test
    fun `dedicated render snapshot is applied after a duplicate legacy render entry`() {
        renderA.privateSetting.setRaw(false)
        val legacyEntry = fileModuleEntry(renderA)
        renderA.privateSetting.setRaw(true)
        val snapshotEntry = fileModuleEntry(renderA)
        val json = localConfig(
            standardEntries = listOf(legacyEntry),
            renderEntries = listOf(snapshotEntry),
        )
        val plan = LocalConfigCodec.createLoadPlan(
            json,
            LocalConfigLoadSelection(includeRender = true),
            listOf(renderA),
        )
        var restoredPrivateState: Boolean? = null

        plan.apply(
            loadStandard = { standard, _ ->
                restoredPrivateState = standard.getAsJsonObject("modules")
                    .moduleEntry(renderA.name)
                    .setting("PrivateSetting")
                    .asBoolean
            },
            loadRender = { snapshot, _ ->
                restoredPrivateState = snapshot.moduleEntry(renderA.name)
                    .setting("PrivateSetting")
                    .asBoolean
            },
        )

        assertEquals(true, restoredPrivateState)
    }

    @Test
    fun `selected normal modules are combined with every render module`() {
        val plan = LocalConfigCodec.createLoadPlan(
            localConfig(renderEntries = listOf(fileModuleEntry(renderA), fileModuleEntry(renderB))),
            LocalConfigLoadSelection(modules = setOf(normalA), includeRender = true),
            allModules,
        )

        assertEquals(listOf(normalA, renderA, renderB), plan.standardModules)
        assertEquals(listOf(renderA, renderB), plan.renderModules)
    }

    @Test
    fun `old config without render snapshot still loads legacy render when opted in`() {
        val json = localConfig(
            standardEntries = listOf(fileModuleEntry(normalA), fileModuleEntry(renderA)),
        )
        val plan = LocalConfigCodec.createLoadPlan(
            json,
            LocalConfigLoadSelection(includeRender = true),
            listOf(normalA, renderA),
        )
        var renderCallbackCalled = false

        plan.apply(
            loadStandard = { _, modules -> assertEquals(listOf(normalA, renderA), modules) },
            loadRender = { _, _ -> renderCallbackCalled = true },
        )

        assertFalse(plan.hasRenderSnapshot)
        assertNull(plan.renderSnapshot)
        assertFalse(renderCallbackCalled)
    }

    private fun localConfig(
        standardEntries: List<JsonObject> = emptyList(),
        renderEntries: List<JsonObject>? = null,
    ) = JsonObject().apply {
        addProperty("name", "autoconfig")
        add("modules", moduleGroup(standardEntries))
        renderEntries?.let { add("renderModules", moduleGroup(it)) }
    }

    private fun moduleGroup(entries: List<JsonObject>) = JsonObject().apply {
        addProperty("name", "modules")
        add("value", JsonArray().apply { entries.forEach(::add) })
    }

    private fun fileModuleEntry(module: ClientModule): JsonObject =
        fileGson.toJsonTree(module, ValueGroup::class.javaObjectType).asJsonObject

    private fun publicModuleEntry(module: FixtureModule) = JsonObject().apply {
        addProperty("name", module.name)
        add("value", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", "PublicSetting")
                addProperty("value", module.publicSetting.get())
            })
        })
    }

    private fun JsonObject.moduleNames(): List<String> = getAsJsonArray("value")
        .map { it.asJsonObject.getAsJsonPrimitive("name").asString }

    private fun JsonObject.moduleEntry(moduleName: String): JsonObject = getAsJsonArray("value")
        .map { it.asJsonObject }
        .single { it.getAsJsonPrimitive("name").asString == moduleName }

    private fun JsonObject.setting(settingName: String) = getAsJsonArray("value")
        .map { it.asJsonObject }
        .single { it.getAsJsonPrimitive("name").asString == settingName }
        .get("value")

    private class FixtureModule(name: String, category: net.ccbluex.liquidbounce.features.module.ModuleCategory) :
        ClientModule(name, category) {

        val publicSetting = boolean("PublicSetting", false)
        val privateSetting = boolean("PrivateSetting", false).doNotIncludeAlways()
        val hiddenValue: Value<Boolean>
            get() = inner.single { it.name == "Hidden" } as Value<Boolean>
    }

    private fun <T : Any> Value<T>.setRaw(value: T) {
        inner = value
    }
}
