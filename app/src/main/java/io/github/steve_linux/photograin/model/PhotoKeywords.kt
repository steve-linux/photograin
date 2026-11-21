// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

import java.text.Normalizer
import java.util.Locale

internal object PhotoKeywords {
    const val MAX_COUNT = 24
    const val MAX_LENGTH = 48
    private val separators = Regex("[,;\\r\\n]+")
    private val spaces = Regex("[\\p{Z}\\s]+")
    private val marks = Regex("\\p{M}+")

    fun key(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFC)

    private fun searchKey(value: String): String =
        marks.replace(Normalizer.normalize(key(value), Normalizer.Form.NFD), "")

    fun containsKeyword(keywords: Set<String>, selected: String): Boolean {
        val required = key(selected)
        return keywords.any { key(it) == required }
    }

    fun matchesToken(keywords: Set<String>, token: String): Boolean {
        val query = searchKey(token)
        return query.isNotEmpty() && keywords.any { searchKey(it).contains(query) }
    }

    /** Keeps the first spelling of each keyword and reports invalid input before saving. */
    fun parse(input: String): Set<String>? {
        val words = input.split(separators).map { word ->
            Normalizer.normalize(spaces.replace(word, " ").trim(), Normalizer.Form.NFC)
        }.filter(String::isNotEmpty).distinctBy(::key)
        if (words.size > MAX_COUNT || words.any { word ->
                word.codePointCount(0, word.length) > MAX_LENGTH || word.any(Char::isISOControl)
            }
        ) return null
        return words.toSet()
    }
}
