package com.indiehost.launcher

import java.text.Normalizer

/**
 * App-drawer search rules, kept pure so they can be host-tested.
 *
 * Query and label are compared case-insensitively with diacritics removed, so
 * "cafe" finds "Café". Package names are compared lowercased without folding
 * accents because Android package ids are ASCII.
 */
object AppSearch {

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(REGEX_DIACRITICS, "")
        .lowercase()
        .trim()

    fun matches(label: String, packageName: String, query: String): Boolean {
        val needle = normalize(query)
        if (needle.isEmpty()) return true
        return normalize(label).contains(needle) || packageName.lowercase().contains(needle)
    }

    private val REGEX_DIACRITICS = Regex("\\p{M}")
}
