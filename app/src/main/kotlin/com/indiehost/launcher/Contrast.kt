package com.indiehost.launcher

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG relative luminance and contrast ratio, kept pure for host tests. */
object Contrast {

    private fun channel(value: Float): Double =
        if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    fun luminance(color: Color): Double =
        0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)

    fun ratio(first: Color, second: Color): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
