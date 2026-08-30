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
package net.ccbluex.liquidbounce.script

import net.ccbluex.liquidbounce.script.bindings.api.ScriptContextProvider.setupContext
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.io.IOAccess
import java.io.File
import java.util.function.Function

internal object PolyglotContextFactory {

    fun create(
        language: String,
        file: File,
        debugOptions: ScriptDebugOptions,
        scriptMetadataRegistrar: Function<Map<String, Any>, PolyglotScript>,
    ): Context {
        val context = Context.newBuilder(language)
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup { true }
            .currentWorkingDirectory(file.parentFile.toPath())
            .allowIO(IOAccess.ALL)
            .allowCreateProcess(false)
            .allowCreateThread(true)
            .allowNativeAccess(false)
            .allowExperimentalOptions(true)
            .applyJsFeatures(language, file)
            .applyScriptDebugOptions(file, debugOptions)
            .build()

        val bindings = context.getBindings(language)
        context.setupContext(language, bindings)
        bindings.putMember("registerScript", scriptMetadataRegistrar)
        return context
    }

    private fun Context.Builder.applyJsFeatures(language: String, file: File): Context.Builder {
        if (language == "js") {
            option("js.nashorn-compat", "true")
            option("js.ecmascript-version", "2023")
            option("js.commonjs-require", "true")
            option("js.commonjs-require-cwd", file.parentFile.absolutePath)
        }
        return this
    }
}
