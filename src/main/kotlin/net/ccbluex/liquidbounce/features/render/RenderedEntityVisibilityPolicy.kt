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
package net.ccbluex.liquidbounce.features.render

internal class RenderedEntityVisibilityPolicy<E> {
    private var shouldRenderEntity: ((E) -> Boolean)? = null
    private var shouldRefreshOnPerspective: (() -> Boolean)? = null

    @Synchronized
    fun install(
        shouldRenderEntity: (E) -> Boolean,
        shouldRefreshOnPerspective: () -> Boolean,
    ) {
        check(this.shouldRenderEntity == null && this.shouldRefreshOnPerspective == null) {
            "Rendered entity visibility policy is already installed"
        }
        this.shouldRenderEntity = shouldRenderEntity
        this.shouldRefreshOnPerspective = shouldRefreshOnPerspective
    }

    fun shouldRender(entity: E): Boolean = checkNotNull(shouldRenderEntity) {
        "Rendered entity visibility policy has not been installed"
    }(entity)

    fun shouldRefreshOnPerspective(): Boolean = checkNotNull(shouldRefreshOnPerspective) {
        "Rendered entity perspective policy has not been installed"
    }()
}
