package com.koala.docs

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val Navy = Color(0xFF00071A)
val Navy2 = Color(0xFF0A1630)
val Card = Color(0xFF101C38)
val CardHi = Color(0xFF1B2A4E)
val Line = Color(0xFF253661)
val Yellow = Color(0xFFFFD23F)
val Red = Color(0xFFE63946)
val Green = Color(0xFF4FE3C1)
val Muted = Color(0xFF8C9AB8)
val Soft = Color(0xFFC9D3EA)
val Paper = Color(0xFFF4F6FB)

/** Couleurs proposées pour les dossiers. */
val FolderColors = listOf(
    0xFFFFD23F, 0xFFFF8A3D, 0xFFE63946, 0xFFFF6FAE, 0xFFB9A6FF,
    0xFF6FD3FF, 0xFF4FE3C1, 0xFF8BD450, 0xFFC9D3EA,
)

val Raleway = FontFamily(
    Font(R.font.raleway_medium, FontWeight.Medium),
    Font(R.font.raleway_medium, FontWeight.Normal),
    Font(R.font.raleway_bold, FontWeight.Bold),
    Font(R.font.raleway_black, FontWeight.Black),
)

val BgBrush = Brush.verticalGradient(listOf(Navy, Navy2, Navy))

@Composable
fun KoaladocsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Yellow, onPrimary = Navy, background = Navy, surface = Card)) {
        // Raleway avec chiffres alignés (lnum) : sinon ses chiffres « elzéviriens » dansent sur la ligne
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = Raleway, fontWeight = FontWeight.Medium, color = Color.White, fontFeatureSettings = "lnum")
        ) { content() }
    }
}
