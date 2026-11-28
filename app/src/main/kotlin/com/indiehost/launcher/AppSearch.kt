package com.indiehost.launcher

import java.text.Normalizer

/**
 * App-drawer search rules, kept pure so they can be host-tested.
 *
 * Query and label are compared case-insensitively with diacritics removed, so
 * "cafe" finds "Café". Package names are compared lowercased without folding
 * accents because Android package ids are ASCII.
 *
 * Ranking follows the desktop-launcher pattern: an exact/prefix label match
 * wins, then a label substring, then a package-name match. Lower is better.
 */
object AppSearch {

    const val NO_MATCH = Int.MAX_VALUE

    private const val LABEL_PREFIX = 0
    private const val LABEL_SUBSTRING = 1000
    private const val PACKAGE_SUBSTRING = 3000

    private val REGEX_DIACRITICS = Regex("\\p{M}")

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(REGEX_DIACRITICS, "")
        .lowercase()
        .trim()

    /** Lower is better; [NO_MATCH] means the app should be hidden. */
    fun rank(label: String, packageName: String, query: String): Int {
        val needle = normalize(query)
        if (needle.isEmpty()) return LABEL_PREFIX
        val name = normalize(label)
        return when {
            name == needle || name.startsWith(needle) -> LABEL_PREFIX
            name.contains(needle) -> LABEL_SUBSTRING
            packageName.lowercase().contains(needle) -> PACKAGE_SUBSTRING
            else -> NO_MATCH
        }
    }

    fun matches(label: String, packageName: String, query: String): Boolean =
        rank(label, packageName, query) != NO_MATCH
}
