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

package net.ccbluex.liquidbounce.features.module.modules.render.chams

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import net.ccbluex.liquidbounce.render.engine.LazyRenderTargetHolder
import net.ccbluex.liquidbounce.render.withOutputTarget
import net.minecraft.client.renderer.feature.ItemFeatureRenderer
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.util.Util
import net.minecraft.world.entity.Entity
import java.util.function.Function
import com.mojang.blaze3d.pipeline.RenderTarget

internal class ChamsRenderCapture(
    moduleName: String,
    private val enabled: () -> Boolean,
    private val shouldRenderEntity: (Entity?) -> Boolean,
    private val composite: (target: RenderTarget, chamsTarget: RenderTarget) -> Unit,
) : AutoCloseable {

    private val supportedRenderTypes = hashSetOf(
        "armor_cutout_no_cull",
        "armor_decal_cutout_no_cull",
        "armor_entity_glint",
        "entity_translucent",
        "entity_cutout",
        "entity_cutout_cull",
        "entity_cutout_no_cull",
        "entity_solid",
        "entity_glint",
        "glint",
        "glint_translucent",
        "item_cutout",
        "item_translucent",
    )

    private val renderTargetHolder = LazyRenderTargetHolder(moduleName, useDepth = true)
    private val outputTarget = OutputTarget("liquidbounce_chams", renderTargetHolder)
    private val remapRenderType: Function<RenderType, RenderType> = Util.memoize { original ->
        ChamsRenderTypeBridge.withOutputTarget(original, outputTarget)
    }
    private val heldItemEntityContext = ScopedValue.newInstance<Entity>()
    private val heldItemSubmits = ReferenceOpenHashSet<ItemFeatureRenderer.Submit>()

    private var dirty = false

    fun remapIfNeeded(renderType: RenderType, entity: Entity?): RenderType {
        if (!enabled() || !shouldRenderEntity(entity) || !supports(renderType)) return renderType

        dirty = true
        return remapRenderType.apply(renderType)
    }

    fun withHeldItemContext(entity: Entity?, block: Runnable) {
        if (enabled() && shouldRenderEntity(entity)) {
            ScopedValue.where(heldItemEntityContext, entity).run(block)
            return
        }

        block.run()
    }

    fun markHeldItemSubmitIfActive(submit: ItemFeatureRenderer.Submit) {
        if (heldItemEntityContext.isBound) heldItemSubmits.add(submit)
    }

    fun isHeldItemSubmit(submit: ItemFeatureRenderer.Submit): Boolean = submit in heldItemSubmits

    fun remapHeldItemRenderTypeIfNeeded(
        submit: ItemFeatureRenderer.Submit,
        renderType: RenderType,
    ): RenderType {
        if (!isHeldItemSubmit(submit) || !supports(renderType)) return renderType

        dirty = true
        return remapRenderType.apply(renderType)
    }

    fun remapCurrentHeldItemRenderTypeIfNeeded(renderType: RenderType): RenderType {
        val entity = heldItemEntityContext.takeIf { it.isBound }?.get() ?: return renderType
        return remapIfNeeded(renderType, entity)
    }

    fun beginFrameIfNeeded() {
        if (enabled() && dirty) renderTargetHolder.initAndGet()
    }

    fun compositeIfNeeded(target: RenderTarget) {
        if (!dirty) {
            heldItemSubmits.clear()
            return
        }

        dirty = false
        try {
            renderTargetHolder.get()?.let { composite(target, it) }
        } finally {
            heldItemSubmits.clear()
        }
    }

    override fun close() {
        dirty = false
        heldItemSubmits.clear()
        renderTargetHolder.close()
    }

    private fun supports(renderType: RenderType): Boolean =
        ChamsRenderTypeBridge.name(renderType) in supportedRenderTypes
}
