package com.zerocode.threes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF30333A)
val Paper = Color(0xFFF6F5F1)
val BoardMint = Color(0xFFBBD8D5)
val EmptyMint = Color(0xFFAED0CE)
val OneBlue = Color(0xFF58C7E8)
val OneBlueEdge = Color(0xFF389FDF)
val TwoRed = Color(0xFFFF6680)
val TwoRedEdge = Color(0xFFE84D70)
val ThreeGold = Color(0xFFFFC957)
val ThreeGoldEdge = Color(0xFFE7A93B)
val SoftWhite = Color(0xFFFFFEFB)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = OneBlue,
    background = Paper,
    onBackground = Ink,
    surface = SoftWhite,
    onSurface = Ink,
    outline = Color(0xFFD7D5CF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F2EB),
    onPrimary = Color(0xFF202226),
    secondary = Color(0xFF63CDE6),
    background = Color(0xFF17191C),
    onBackground = Color(0xFFF5F2EB),
    surface = Color(0xFF25272B),
    onSurface = Color(0xFFF5F2EB),
    outline = Color(0xFF42454B),
)

@Composable
fun ThreesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
