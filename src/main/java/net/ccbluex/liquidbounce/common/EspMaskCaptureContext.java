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

package net.ccbluex.liquidbounce.common;

/**
 * Associates deferred model submissions with LiquidBounce ESP mask colors.
 */
public final class EspMaskCaptureContext {

    private static final ScopedValue<EspMaskRequest> REQUEST = ScopedValue.newInstance();

    private EspMaskCaptureContext() {
    }

    public static EspMaskRequest current() {
        return REQUEST.isBound() ? REQUEST.get() : EspMaskRequest.NONE;
    }

    public static void run(EspMaskRequest request, Runnable render) {
        if (request.isEmpty()) {
            render.run();
            return;
        }

        ScopedValue.where(REQUEST, request).run(render);
    }
}
