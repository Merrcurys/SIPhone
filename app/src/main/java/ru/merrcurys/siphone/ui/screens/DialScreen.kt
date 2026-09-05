package ru.merrcurys.siphone.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.merrcurys.siphone.ui.theme.appTheme

private data class DialKeySpec(val digit: String, val letters: String)

private val KEY_LAYOUT = listOf(
    listOf(DialKeySpec("1", ""), DialKeySpec("2", "ABC"), DialKeySpec("3", "DEF")),
    listOf(DialKeySpec("4", "GHI"), DialKeySpec("5", "JKL"), DialKeySpec("6", "MNO")),
    listOf(DialKeySpec("7", "PQRS"), DialKeySpec("8", "TUV"), DialKeySpec("9", "WXYZ")),
    listOf(DialKeySpec("*", ""), DialKeySpec("0", "+"), DialKeySpec("#", ""))
)

@Composable
fun DialScreen(
    onOpenSettings: () -> Unit = {},
    onStartCall: (String) -> Unit = {}
) {
    var phoneNumber by remember { mutableStateOf("") }

    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension * 0.62f,
                    center = Offset(size.width * 1.02f, size.height * 0.12f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            scheme.secondary.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension * 0.5f,
                    center = Offset(size.width * 0.02f, size.height * 0.42f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 1.06f, size.height * 0.98f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Вызов",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primary.copy(alpha = 0.10f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Настройки",
                        tint = scheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            NumberHero(
                phoneNumber = phoneNumber,
                onDelete = { phoneNumber = phoneNumber.dropLast(1) },
                onClear = { phoneNumber = "" }
            )

            Spacer(modifier = Modifier.weight(0.55f))

            DialPad(onDigitClick = { digit -> phoneNumber += digit })

            Spacer(modifier = Modifier.weight(1f))

            CallButton(
                enabled = phoneNumber.isNotEmpty(),
                onClick = { onStartCall(phoneNumber) }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberHero(
    phoneNumber: String,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val hasNumber = phoneNumber.isNotEmpty()

    val heroBrush = Brush.verticalGradient(
        colors = listOf(scheme.primary, scheme.secondary)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = scheme.primary,
                ambientColor = scheme.primary
            )
            .clip(RoundedCornerShape(32.dp))
            .background(heroBrush)
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.95f, size.height * 0.05f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    style = Stroke(width = 2.dp.toPx()),
                    radius = size.minDimension * 0.34f,
                    center = Offset(size.width * 0.06f, size.height * 1.05f)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasNumber) phoneNumber else "Введите номер",
                fontSize = if (hasNumber) {
                    numberFontSize(phoneNumber.length)
                } else {
                    18.sp
                },
                fontWeight = if (hasNumber) FontWeight.Bold else FontWeight.Medium,
                color = Color.White.copy(alpha = if (hasNumber) 1f else 0.85f),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (hasNumber) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .combinedClickable(
                            onClick = onDelete,
                            onLongClick = onClear
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Удалить",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }
    }
}

@Composable
private fun numberFontSize(length: Int): TextUnit = when {
    length <= 4 -> 32.sp
    length <= 7 -> 28.sp
    length <= 10 -> 24.sp
    length <= 14 -> 20.sp
    else -> 16.sp
}

@Composable
fun DialPad(onDigitClick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        KEY_LAYOUT.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 20.dp,
                    alignment = Alignment.CenterHorizontally
                )
            ) {
                row.forEach { spec ->
                    DialButton(
                        digit = spec.digit,
                        letters = spec.letters,
                        onClick = { onDigitClick(spec.digit) }
                    )
                }
            }
        }
    }
}

@Composable
fun DialButton(
    digit: String,
    letters: String = "",
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 130),
        label = "keyScale"
    )

    val background: Brush = if (isPressed) {
        Brush.verticalGradient(colors = listOf(scheme.primary, scheme.secondary))
    } else {
        val base = scheme.surfaceVariant.copy(alpha = 0.55f)
        Brush.verticalGradient(colors = listOf(base, base))
    }

    Box(
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .then(
                if (!isPressed) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = scheme.primary.copy(alpha = 0.14f),
                            shape = CircleShape
                        )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) Color.White else scheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPressed) {
                        Color.White.copy(alpha = 0.9f)
                    } else {
                        scheme.onSurfaceVariant.copy(alpha = 0.65f)
                    }
                )
            } else {
                Text(
                    text = "\u00A0",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Transparent
                )
            }
        }
    }
}

@Composable
private fun CallButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    val enabledBrush = Brush.verticalGradient(colors = listOf(scheme.primary, scheme.secondary))

    Box(
        modifier = Modifier.size(88.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(
                    elevation = if (enabled) 14.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = scheme.primary,
                    spotColor = scheme.primary
                )
                .clip(CircleShape)
                .background(
                    if (enabled) enabledBrush
                    else {
                        val grey = scheme.surfaceVariant.copy(alpha = 0.6f)
                        Brush.verticalGradient(colors = listOf(grey, grey))
                    }
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Позвонить",
                modifier = Modifier.size(30.dp),
                tint = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DialScreenPreview() {
    appTheme {
        DialScreen()
    }
}
