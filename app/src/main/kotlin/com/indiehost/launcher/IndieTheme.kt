package com.indiehost.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Game-client palette: the near-black blue of the chrome, the lighter blue of a
 * library panel, and the pale cyan it uses for links and buttons.
 *
 * This is only a look. It contains no third-party artwork, logos or fonts.
 */
internal object Indie {
    /** Client chrome / page background. */
    val Base = Color(0xFF171A21)

    /** Library panel background. */
    val Panel = Color(0xFF1B2838)

    /** Cards sitting on a panel. */
    val Card = Color(0xFF16202D)
    val CardRaised = Color(0xFF22344A)

    /** 1dp card outline. */
    val Edge = Color(0xFF2A3F5A)

    /** Bright link / highlight blue. */
    val Accent = Color(0xFF66C0F4)

    /** Button blue, darker so white-on-blue stays readable. */
    val AccentDeep = Color(0xFF2A6C93)

    val Text = Color(0xFFC6D4DF)
    val TextBright = Color(0xFFF2F7FB)
    val TextMuted = Color(0xFF8F98A0)
    val Green = Color(0xFFA4D007)

    val HeroGradient = Brush.linearGradient(
        listOf(Color(0xFF2A475E), Color(0xFF1B2838), Color(0xFF171A21)),
    )
}

private val indieColors = darkColorScheme(
    primary = Indie.Accent,
    onPrimary = Color(0xFF0B1219),
    secondary = Indie.AccentDeep,
    onSecondary = Indie.TextBright,
    background = Indie.Base,
    onBackground = Indie.Text,
    surface = Indie.Panel,
    onSurface = Indie.Text,
    surfaceVariant = Indie.CardRaised,
    onSurfaceVariant = Indie.TextMuted,
    outline = Indie.Edge,
    error = Color(0xFFE06C6C),
)

private val indieTypography = Typography().run {
    copy(
        headlineSmall = TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            color = Indie.TextBright,
        ),
        titleLarge = TextStyle(
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            color = Indie.TextBright,
        ),
        titleMedium = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Indie.TextBright,
        ),
        bodyLarge = TextStyle(fontSize = 15.sp, color = Indie.Text),
        bodyMedium = TextStyle(fontSize = 14.sp, color = Indie.Text),
        bodySmall = TextStyle(fontSize = 12.sp, color = Indie.TextMuted),
        labelMedium = TextStyle(fontSize = 12.sp, color = Indie.Text),
    )
}

@Composable
internal fun IndieTheme(content: @Composable () -> Unit) {
    // Deliberately dark-only: the launcher is meant to feel like one client, and
    // a light variant would fight the pinned game artwork and icons.
    MaterialTheme(colorScheme = indieColors, typography = indieTypography, content = content)
}
