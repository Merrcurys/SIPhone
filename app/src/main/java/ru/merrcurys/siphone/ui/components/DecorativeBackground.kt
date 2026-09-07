package ru.merrcurys.siphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Фоновое оформление экранов: цвет фона темы + мягкие декоративные круги
// по углам (как на странице настроек).
@Composable
fun Modifier.appDecorativeBackground(): Modifier {
    val scheme = MaterialTheme.colorScheme
    return this
        .background(scheme.background)
        .drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = 0.14f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension * 0.6f,
                center = Offset(size.width * 0.98f, size.height * 0.04f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.secondary.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.02f, size.height * 0.45f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = 0.10f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.95f, size.height * 1.02f)
            )
        }
}
