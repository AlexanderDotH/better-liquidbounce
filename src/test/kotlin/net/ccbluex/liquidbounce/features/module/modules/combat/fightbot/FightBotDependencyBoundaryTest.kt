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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class FightBotDependencyBoundaryTest {

    @Test
    fun `FightBot runtime depends on injected ports instead of combat and render implementations`() {
        Files.list(FIGHT_BOT_SOURCE_ROOT).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.forEach { path ->
                val source = Files.readString(path)
                FORBIDDEN_IMPORTS.forEach { forbiddenImport ->
                    assertFalse(forbiddenImport in source, "$path must not import $forbiddenImport")
                }
            }
        }

        val facade = Files.readString(MODULE_FACADE)
        listOf(
            "FightBotCombatPort",
            "FightBotRemoteWeaponPort",
            "FightBotDebugPort",
            "FightBotTargetPort",
        ).forEach { port -> assertTrue(port in facade, "ModuleFightBot must wire $port") }
    }

    private companion object {
        val FIGHT_BOT_SOURCE_ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/fightbot",
        )
        val MODULE_FACADE: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/ModuleFightBot.kt",
        )
        val FORBIDDEN_IMPORTS = listOf(
            "import net.ccbluex.liquidbounce.features.combat.runtime",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.*",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.Module",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.killaura",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.macekill",
            "import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill",
            "import net.ccbluex.liquidbounce.features.module.modules.render",
        )
    }
}
