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

package net.ccbluex.liquidbounce.integration.theme.component.components.minimap

import it.unimi.dsi.fastutil.objects.ReferenceArrayList
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.config.types.group.Alignment
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.features.render.RenderedEntities
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeHudComponent
import net.ccbluex.liquidbounce.render.getBounds
import net.ccbluex.liquidbounce.render.engine.type.BoundingBox2f
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentRotation
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.math.ceilToInt
import net.ccbluex.liquidbounce.utils.math.toRadians
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items

object MinimapHudComponent : NativeHudComponent("Minimap", false, Alignment(
    horizontalAlignment = Alignment.ScreenAxisX.LEFT,
    horizontalOffset = 7,
    verticalAlignment = Alignment.ScreenAxisY.TOP,
    verticalOffset = 180,
), description = "Shows nearby terrain and entities.") {

    private val size by int("Size", 96, 1..256)
    private val viewDistance by float("ViewDistance", 3.0F, 1.0F..8.0F)
    private val fixedDirection by boolean("FixedDirection", false)

    override val guiScaledWidth: Float
        get() = size.toFloat()

    override val guiScaledHeight: Float
        get() = size.toFloat()

    private object TextureValueGroup : ToggleableValueGroup(this, "Texture", true) {
        val vertexColor by color("VertexColor", Color4b.WHITE)

        override fun onEnabled() {
            ChunkScanner.subscribe(ChunkRenderer.MinimapChunkUpdateSubscriber)
        }

        override fun onDisabled() {
            ChunkScanner.unsubscribe(ChunkRenderer.MinimapChunkUpdateSubscriber)
            ChunkRenderer.unloadEverything()
        }
    }

    private object EntityValueGroup : ToggleableValueGroup(this, "Entity", true) {
        val scale by float("Scale", 1f, 0.25F..4F)
        val outOfBounds by enumChoice("OutOfBounds", OutOfBounds.NONE)

        val entities = ReferenceArrayList<LivingEntity>()

        private val MINIMAP_ENTITY_ORDER = Comparator<Entity> { e1, e2 ->
            when {
                e1.y != e2.y -> e1.y.compareTo(e2.y)
                e1.x != e2.x -> e1.x.compareTo(e2.x)
                else -> e1.z.compareTo(e2.z)
            }
        }

        override fun onEnabled() {
            RenderedEntities.subscribe(this)
            RenderedEntities.onUpdated {
                entities.clear()
                entities.ensureCapacity(RenderedEntities.size)
                RenderedEntities.filterTo(entities) { it !== player }
                entities.sortWith(MINIMAP_ENTITY_ORDER)
            }
            super.onEnabled()
        }

        override fun onDisabled() {
            RenderedEntities.unsubscribe(this)
            super.onDisabled()
        }

        enum class OutOfBounds(override val tag: String) : Tagged {
            NONE("None"),
            ALL("All"),
        }
    }

    private class ExtraElement(
        name: String,
        private val size: Float,
        private val draw: Renderer,
    ) : ToggleableValueGroup(this, name, false) {
        val placement by enumChoice("Placement", Placement.TOP_LEFT)

        fun render(ctx: GuiGraphicsExtractor, boundingBox: BoundingBox2f) {
            if (enabled) {
                ctx.pose().withPush {
                    when (placement) {
                        Placement.TOP_LEFT -> translate(boundingBox.xMin, boundingBox.yMin)
                        Placement.TOP_RIGHT -> translate(boundingBox.xMax - size, boundingBox.yMin)
                        Placement.BOTTOM_LEFT -> translate(boundingBox.xMin, boundingBox.yMax - size)
                        Placement.BOTTOM_RIGHT -> translate(boundingBox.xMax - size, boundingBox.yMax - size)
                    }
                    draw(ctx)
                }
            }
        }

        private enum class Placement(override val tag: String) : Tagged {
            TOP_LEFT("TopLeft"),
            TOP_RIGHT("TopRight"),
            BOTTOM_LEFT("BottomLeft"),
            BOTTOM_RIGHT("BottomRight"),
        }

        fun interface Renderer {
            operator fun invoke(ctx: GuiGraphicsExtractor)
        }
    }

    private val extraElements = arrayOf(
        ExtraElement("Compass", GuiRenderer.DEFAULT_ITEM_SIZE.toFloat()) { ctx ->
            val stack = player.inventory.nonEquipmentItems.find { it.item === Items.COMPASS } ?: COMPASS
            ctx.item(stack, 0, 0)
        },
        ExtraElement("Clock", GuiRenderer.DEFAULT_ITEM_SIZE.toFloat()) { ctx ->
            val stack = player.inventory.nonEquipmentItems.find { it.item === Items.CLOCK } ?: CLOCK
            ctx.item(stack, 0, 0)
        },
    )

    private val COMPASS by lazy(LazyThreadSafetyMode.NONE) { Items.COMPASS.defaultInstance }
    private val CLOCK by lazy(LazyThreadSafetyMode.NONE) { Items.CLOCK.defaultInstance }

    init {
        tree(TextureValueGroup)
        tree(EntityValueGroup)
        extraElements.forEach(::tree)
        ChunkRenderer
        registerComponentListen(this)
    }

    val renderHandler = handler<OverlayRenderEvent>(priority = EventPriorityConvention.MODEL_STATE) { event ->
        if (HideAppearance.isHidingNow) {
            return@handler
        }

        val playerPos = player.interpolateCurrentPosition(event.tickDelta)
        val playerRotation = player.interpolateCurrentRotation(event.tickDelta)

        val minimapSize = size

        val boundingBox = getGuiScaledBounds(minimapSize.toFloat(), minimapSize.toFloat())

        val centerBB = boundingBox.centerVec

        val baseX = (playerPos.x / 16.0).toInt()
        val baseZ = (playerPos.z / 16.0).toInt()

        val playerOffX = (playerPos.x / 16.0) % 1.0
        val playerOffZ = (playerPos.z / 16.0) % 1.0

        val chunksToRenderAround = (Mth.SQRT_OF_TWO * (viewDistance + 1)).ceilToInt()

        val scale = minimapSize / (2.0F * viewDistance)
        val mapRotation = if (!fixedDirection) -(playerRotation.yaw + 180.0F).toRadians() else 0F

        with(event.context) {
            val bounds = getBounds(boundingBox)
            scissorStack.withPush(bounds) {
                pose().withPush {
                    translate(boundingBox.xMin + minimapSize * 0.5F, boundingBox.yMin + minimapSize * 0.5F)
                    scale(scale)

                    if (mapRotation != 0F) rotate(mapRotation)
                    translate(-playerOffX.toFloat(), -playerOffZ.toFloat())

                    drawMinimapTerrain(
                        bounds,
                        MinimapTerrainRenderState(
                            enabled = TextureValueGroup.enabled,
                            vertexColor = { TextureValueGroup.vertexColor },
                            baseX = baseX,
                            baseZ = baseZ,
                            chunksToRenderAround = chunksToRenderAround,
                            viewDistance = viewDistance,
                        ),
                    )

                    if (EntityValueGroup.enabled) {
                        drawMinimapEntities(
                            MinimapEntityRenderState(
                                entities = EntityValueGroup.entities,
                                tickDelta = event.tickDelta,
                                baseX = baseX.toFloat(),
                                baseZ = baseZ.toFloat(),
                                scale = EntityValueGroup.scale,
                            ),
                        )
                    }
                }
            }

            if (EntityValueGroup.enabled && EntityValueGroup.outOfBounds != EntityValueGroup.OutOfBounds.NONE) {
                drawMinimapOutOfBoundsEntityMarkers(
                    entities = EntityValueGroup.entities,
                    tickDelta = event.tickDelta,
                    viewport = MinimapMarkerViewport(
                        center = centerBB,
                        boundingBox = boundingBox,
                        playerChunkX = playerPos.x.toFloat() / 16.0F,
                        playerChunkZ = playerPos.z.toFloat() / 16.0F,
                        mapScale = scale,
                        viewDistance = viewDistance,
                        rotation = mapRotation,
                    ),
                )
            }

            for (element in extraElements) {
                element.render(this, boundingBox)
            }

            val chrome = resolveCurrentMinimapHudChrome(ModuleHud.theme)
            drawMinimapHudChrome(boundingBox, bounds, chrome)
        }
    }

}
