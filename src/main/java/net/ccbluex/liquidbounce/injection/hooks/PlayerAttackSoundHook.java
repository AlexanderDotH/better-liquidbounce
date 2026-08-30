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
package net.ccbluex.liquidbounce.injection.hooks;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class PlayerAttackSoundHook {

    private static final String FAKE_PLAYER_CLASS_NAME =
        "net.ccbluex.liquidbounce.features.command.commands.ingame.fakeplayer.FakePlayer";

    private PlayerAttackSoundHook() {
    }

    private static boolean isFakePlayer(Entity target) {
        Class<?> type = target.getClass();
        while (type != null) {
            if (FAKE_PLAYER_CLASS_NAME.equals(type.getName())) {
                return true;
            }

            type = type.getSuperclass();
        }

        return false;
    }

    public static void playIfFakePlayer(Object owner, Entity target, SoundEvent soundEvent) {
        if (!isFakePlayer(target)) {
            return;
        }

        Player player = Player.class.cast(owner);
        player.level().playSound(
            player,
            player.getX(),
            player.getY(),
            player.getZ(),
            soundEvent,
            player.getSoundSource(),
            1F,
            1F
        );
    }
}
