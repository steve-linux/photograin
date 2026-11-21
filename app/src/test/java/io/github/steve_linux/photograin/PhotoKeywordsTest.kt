// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin

import io.github.steve_linux.photograin.model.PhotoKeywords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoKeywordsTest {
    @Test
    fun normalizesWhitespaceAndEquivalentSpellings() {
        val keywords = PhotoKeywords.parse("  Città ; CITTA\u0300, lago\u00a0di   Garda\nlago di Garda")
        assertEquals(setOf("Città", "lago di Garda"), keywords)
        assertEquals(emptySet<String>(), PhotoKeywords.parse(" , ;\n"))
    }

    @Test
    fun keepsExactFilterDistinctFromAccentInsensitiveTextSearch() {
        val keywords = setOf("Città vecchia", "Lago")
        assertTrue(PhotoKeywords.matchesToken(keywords, "CITTA"))
        assertTrue(PhotoKeywords.matchesToken(keywords, "vecchia"))
        assertFalse(PhotoKeywords.matchesToken(keywords, "\u0300"))
        assertFalse(PhotoKeywords.containsKeyword(keywords, "città"))
        assertTrue(PhotoKeywords.containsKeyword(keywords, "CITTÀ VECCHIA"))
    }

    @Test
    fun rejectsTooManyKeywordsAndCountsUnicodeCodePoints() {
        assertNull(PhotoKeywords.parse((0..PhotoKeywords.MAX_COUNT).joinToString(",") { "tag$it" }))
        val emoji = "\uD83C\uDF04".repeat(PhotoKeywords.MAX_LENGTH)
        assertEquals(setOf(emoji), PhotoKeywords.parse(emoji))
        assertNull(PhotoKeywords.parse(emoji + "x"))
    }
}
