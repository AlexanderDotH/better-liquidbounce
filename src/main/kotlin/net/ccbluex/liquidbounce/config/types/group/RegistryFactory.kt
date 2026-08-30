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
package net.ccbluex.liquidbounce.config.types.group

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.EntityType
import java.util.SequencedSet

abstract class RegistryFactory protected constructor(
    name: String,
    value: MutableCollection<Value<*>>,
    valueType: ValueType,
    independentDescription: Boolean,
    aliases: List<String>,
) : WorldFactory(name, value, valueType, independentDescription, aliases) {

    fun <C : SequencedSet<SoundEvent>> sounds(name: String, default: C) =
        registryList(name, default, ValueType.SOUND_EVENT)

    fun <C : SequencedSet<MobEffect>> mobEffects(name: String, default: C) =
        registryList(name, default, ValueType.MOB_EFFECT)

    fun <C : SequencedSet<Identifier>> enchantments(name: String, default: C) =
        registryList(name, default, ValueType.ENCHANTMENT)

    fun <C : SequencedSet<Identifier>> c2sPackets(name: String, default: C) =
        registryList(name, default, ValueType.C2S_PACKET)

    fun <C : SequencedSet<Identifier>> s2cPackets(name: String, default: C) =
        registryList(name, default, ValueType.S2C_PACKET)

    fun <C : SequencedSet<EntityType<*>>> entityTypes(name: String, default: C) =
        registryList(name, default, ValueType.ENTITY_TYPE)
}
