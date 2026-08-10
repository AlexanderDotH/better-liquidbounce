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
package net.ccbluex.liquidbounce.features.module.modules.render

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3f
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModuleBlockESPTest {

    @BeforeEach
    fun bootstrapMinecraft() {
        MinecraftBootstrap.ensureInitialized()
    }

    @Test
    fun `nether portal is an ordinary default target without adding its obsidian frame`() {
        val targets = defaultBlockEspTargets()

        assertTrue(Blocks.NETHER_PORTAL in targets)
        assertFalse(Blocks.OBSIDIAN in targets)
    }

    @Test
    fun `legacy enabled portal checkbox migrates portal into targets exactly once`() {
        val config = blockEspConfig(netherPortals = true, targets = listOf("minecraft:dragon_egg"))

        migrateLegacyNetherPortalTarget(config)
        migrateLegacyNetherPortalTarget(config)

        assertEquals(
            listOf("minecraft:dragon_egg", "minecraft:nether_portal"),
            storedTargetIds(config),
        )
    }

    @Test
    fun `legacy disabled or missing portal checkbox does not change saved targets`() {
        val disabled = blockEspConfig(netherPortals = false, targets = listOf("minecraft:dragon_egg"))
        val missing = blockEspConfig(netherPortals = null, targets = listOf("minecraft:dragon_egg"))

        migrateLegacyNetherPortalTarget(disabled)
        migrateLegacyNetherPortalTarget(missing)

        assertEquals(listOf("minecraft:dragon_egg"), storedTargetIds(disabled))
        assertEquals(listOf("minecraft:dragon_egg"), storedTargetIds(missing))
    }

    @Test
    fun `shader esp exposes the standard gaussian glow controls`() {
        val shaderEsp = ModuleBlockESP.ShaderEspMode

        assertEquals(
            listOf("Radius", "Softness", "Intensity", "CoreSize", "Opacity"),
            shaderEsp.inner.map { it.name },
        )
        assertRange(shaderEsp, "Radius", 4f, 24f, "px")
        assertRange(shaderEsp, "Softness", 0.5f, 1.5f, "")
        assertRange(shaderEsp, "Intensity", 0f, 2f, "")
        assertRange(shaderEsp, "CoreSize", 0f, 3f, "px")
    }

    @Test
    fun `legacy Glow keeps a crisp outline without painting the block surface`() {
        assertEquals(4f, ModuleBlockESP.GlowMode.style.radius)
        assertEquals(0f, ModuleBlockESP.GlowMode.style.intensity)
        assertEquals(2f, ModuleBlockESP.GlowMode.style.coreSize)
        assertEquals(1f, ModuleBlockESP.GlowMode.style.opacity)
    }

    @Test
    fun `module definition removes portal checkbox and adds shader esp without replacing glow`() {
        val moduleClass = Class.forName(ModuleBlockESP::class.java.name, false, javaClass.classLoader)

        assertFalse(moduleClass.declaredFields.any { it.name.contains("netherPortals", ignoreCase = true) })
        val nestedTypes = moduleClass.declaredClasses.map { it.simpleName }
        assertTrue("GlowMode" in nestedTypes)
        assertTrue("ShaderEspMode" in nestedTypes)
    }

    @Test
    fun `block tracers default off and expose the glow tracer controls`() {
        val tracers = BlockEspTracerSettings()

        assertFalse(tracers.enabled)
        assertEquals(
            listOf("Enabled", "LineWidth", "Radius", "Softness", "Intensity", "Opacity"),
            tracers.inner.map { it.name },
        )
        assertRange(tracers, "LineWidth", 1f, 16f, "")
        assertRange(tracers, "Radius", 4f, 24f, "px")
        assertRange(tracers, "Softness", 0.5f, 1.5f, "")
        assertRange(tracers, "Intensity", 0f, 2f, "")
    }

    @Test
    fun `six connected portal cells produce one tracer at the portal center`() {
        val portal = Blocks.NETHER_PORTAL.defaultBlockState()
            .setValue(NetherPortalBlock.AXIS, Direction.Axis.X)
        val sources = buildList {
            for (x in 10..11) {
                for (y in 20..22) {
                    add(BlockTracerSource(BlockPos(x, y, 30), portal))
                }
            }
        }

        val targets = collectBlockTracerTargets(sources)

        assertEquals(1, targets.size)
        assertEquals(Vec3(11.0, 21.5, 30.5), targets.single().worldPosition)
    }

    @Test
    fun `touching portals with different axes remain separate tracer targets`() {
        val portalX = Blocks.NETHER_PORTAL.defaultBlockState()
            .setValue(NetherPortalBlock.AXIS, Direction.Axis.X)
        val portalZ = Blocks.NETHER_PORTAL.defaultBlockState()
            .setValue(NetherPortalBlock.AXIS, Direction.Axis.Z)

        val targets = collectBlockTracerTargets(
            listOf(
                BlockTracerSource(BlockPos.ZERO, portalX),
                BlockTracerSource(BlockPos(1, 0, 0), portalZ),
            )
        )

        assertEquals(2, targets.size)
    }

    @Test
    fun `block tracer uses an opaque color and camera relative block center`() {
        val source = BlockTracerSource(BlockPos(10, 20, 30), Blocks.DRAGON_EGG.defaultBlockState())
        val target = collectBlockTracerTargets(listOf(source)).single()
        val eye = Vec3f(1f, 2f, 3f)

        val batch = createBlockTracerBatch(
            targets = listOf(target),
            eyePosition = eye,
            cameraPosition = Vec3(1.0, 2.0, 3.0),
            maximumDistanceSquared = Double.MAX_VALUE,
            lineWidth = 3f,
        ) { _, _ -> Color4b(12, 34, 56, 50) }

        assertEquals(1, batch.segments.size)
        assertEquals(Color4b(12, 34, 56, 255), batch.segments.single().color)
        assertEquals(eye, batch.segments.single().eyePosition)
        assertEquals(Vec3f(9.5f, 18.5f, 27.5f), batch.segments.single().targetPosition)
    }

    @Test
    fun `block tracer excludes targets beyond the configured distance`() {
        val source = BlockTracerSource(BlockPos(10, 20, 30), Blocks.DRAGON_EGG.defaultBlockState())
        val target = collectBlockTracerTargets(listOf(source)).single()

        val batch = createBlockTracerBatch(
            targets = listOf(target),
            eyePosition = Vec3f.ZERO,
            cameraPosition = Vec3.ZERO,
            maximumDistanceSquared = 1.0,
            lineWidth = 1f,
        ) { _, _ -> Color4b.WHITE }

        assertTrue(batch.segments.isEmpty())
    }

    private fun assertRange(
        values: ValueGroup,
        name: String,
        from: Float,
        to: Float,
        suffix: String,
    ) {
        val value = values.inner.single { it.name == name } as RangedValue<*>
        assertEquals(from, value.range.start)
        assertEquals(to, value.range.endInclusive)
        assertEquals(suffix, value.suffix)
    }

    private fun blockEspConfig(netherPortals: Boolean?, targets: List<String>) = JsonObject().apply {
        addProperty("name", "BlockESP")
        add("value", JsonArray().apply {
            add(storedValue("Targets", JsonArray().apply { targets.forEach(::add) }))
            if (netherPortals != null) {
                add(storedValue("NetherPortals", netherPortals))
            }
        })
    }

    private fun storedValue(name: String, value: Any) = JsonObject().apply {
        addProperty("name", name)
        when (value) {
            is JsonArray -> add("value", value)
            is Boolean -> addProperty("value", value)
        }
    }

    private fun storedTargetIds(config: JsonObject): List<String> = config.getAsJsonArray("value")
        .map { it.asJsonObject }
        .single { it["name"].asString == "Targets" }
        .getAsJsonArray("value")
        .map { it.asString }
}
