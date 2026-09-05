package ru.merrcurys.siphone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val LightColorScheme = lightColorScheme(
    primary = AccentGreen,
    onPrimary = ColorTextWhite,
    secondary = GreenAccent,
    tertiary = YellowAccent,
    background = Background,
    surface = BackgroundModal,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = RedAccent,
    outline = GreyMiddle
)


private val DarkColorScheme = darkColorScheme(
    primary = AccentGreenDark,
    onPrimary = ColorTextWhite,
    secondary = GreenAccent,
    tertiary = YellowAccent,
    background = BackgroundDark,
    surface = BackgroundModalDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    error = RedAccent,
    outline = GreyMiddleDark
)

@Composable
fun appTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    val systemUiController = rememberSystemUiController()
    val darkIcons = !darkTheme

    SideEffect {
        systemUiController.setSystemBarsColor(
            color = Color.Transparent,
            darkIcons = darkIcons,
            isNavigationBarContrastEnforced = false
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
