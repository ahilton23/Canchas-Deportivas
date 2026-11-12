package com.example.canchasdeportivas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
    primary = Color(0xFF076A50), onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F4DB), onPrimaryContainer = Color(0xFF053D2D),
    secondary = Color(0xFF455C52), secondaryContainer = Color(0xFFE1EDE6),
    tertiary = Color(0xFF856008), tertiaryContainer = Color(0xFFFFEDBD),
    background = Color(0xFFF5F7F3), onBackground = Color(0xFF182820),
    surface = Color(0xFFFCFDF9), onSurface = Color(0xFF182820),
    surfaceVariant = Color(0xFFE4EBE3), onSurfaceVariant = Color(0xFF4B5B50),
    outline = Color(0xFF77877C), outlineVariant = Color(0xFFD0DBD2)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7DD9B0), onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF07533D), onPrimaryContainer = Color(0xFFC9F4DB),
    secondary = Color(0xFFB6CDBE), secondaryContainer = Color(0xFF30483B),
    tertiary = Color(0xFFF1CB78), background = Color(0xFF101C16),
    surface = Color(0xFF16251D), onSurface = Color(0xFFE1EDE3),
    surfaceVariant = Color(0xFF2C3C32), onSurfaceVariant = Color(0xFFBDCEC0)
)
@Composable
fun CanchasDeportivasTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content)
}
