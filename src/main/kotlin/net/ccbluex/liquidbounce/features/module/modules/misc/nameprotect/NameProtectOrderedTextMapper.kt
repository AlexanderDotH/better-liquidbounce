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
package net.ccbluex.liquidbounce.features.module.modules.misc.nameprotect

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.ccbluex.fastutil.LfuCache
import net.ccbluex.fastutil.Pool
import net.ccbluex.fastutil.Pool.Companion.use
import net.ccbluex.liquidbounce.utils.text.bypassesNameProtection
import net.ccbluex.liquidbounce.utils.collection.Pools
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink

private const val ORDERED_TEXT_CACHE_SIZE = 512

internal class NameProtectOrderedTextMapper(private val mappings: NameProtectMappings) {

    private val characterPool = Pool(
        initializer = { ObjectArrayList(128) },
        finalizer = ObjectArrayList<MappedCharacter>::clear,
    ).synchronized()
    private val cache = LfuCache<FormattedCharSequence, WrappedOrderedText>(ORDERED_TEXT_CACHE_SIZE) { _, value ->
        characterPool.recycle(value.mappedCharacters)
    }

    fun wrapCached(original: FormattedCharSequence): FormattedCharSequence =
        cache.getOrPut(original) { wrap(original) }

    fun wrap(original: FormattedCharSequence): WrappedOrderedText {
        val mappedCharacters = characterPool.borrow()
        val originalCharacters = readCharacters(original)
        val replacements = findReplacements(originalCharacters)
        applyReplacements(originalCharacters, mappedCharacters, replacements)
        characterPool.recycle(originalCharacters)
        return WrappedOrderedText(mappedCharacters)
    }

    private fun readCharacters(original: FormattedCharSequence): ObjectArrayList<MappedCharacter> {
        val characters = characterPool.borrow()
        original.accept { _, style, codePoint ->
            characters += MappedCharacter(style, style.color?.bypassesNameProtection ?: false, codePoint)
            true
        }
        return characters
    }

    private fun findReplacements(characters: ObjectArrayList<MappedCharacter>) = Pools.StringBuilder.use {
        it.ensureCapacity(characters.size)
        characters.forEach { character -> it.appendCodePoint(character.codePoint) }
        mappings.findReplacements(it)
    }

    private fun applyReplacements(
        original: ObjectArrayList<MappedCharacter>,
        mapped: ObjectArrayList<MappedCharacter>,
        replacements: List<Pair<org.ahocorasick.trie.Emit, NameProtectMappings.MappingData>>,
    ) {
        var replacementIndex = 0
        var currentIndex = 0
        while (currentIndex < original.size) {
            val replacement = replacements.getOrNull(replacementIndex)
            val start = replacement?.first?.start
            if (start != currentIndex) {
                val copyEnd = start ?: original.size
                mapped.addAll(original.subList(currentIndex, copyEnd))
                currentIndex = copyEnd
                continue
            }
            if (original[start].bypassesNameProtection) {
                replacementIndex++
                continue
            }
            appendReplacement(mapped, original[currentIndex].style, requireNotNull(replacement).second)
            currentIndex = replacement.first.end + 1
            replacementIndex++
        }
    }

    private fun appendReplacement(
        output: ObjectArrayList<MappedCharacter>,
        style: Style,
        replacement: NameProtectMappings.MappingData,
    ) {
        output.ensureCapacity(output.size + replacement.newName.length)
        replacement.newName.mapTo(output) { character ->
            MappedCharacter(style.withColor(replacement.colorGetter().argb), false, character.code)
        }
    }
}

internal class MappedCharacter(
    @JvmField val style: Style,
    @JvmField val bypassesNameProtection: Boolean,
    @JvmField val codePoint: Int,
)

internal class WrappedOrderedText(@JvmField val mappedCharacters: ObjectArrayList<MappedCharacter>) :
    FormattedCharSequence {
    override fun accept(visitor: FormattedCharSink): Boolean {
        mappedCharacters.forEachIndexed { index, character ->
            if (!visitor.accept(index, character.style, character.codePoint)) return false
        }
        return true
    }
}
