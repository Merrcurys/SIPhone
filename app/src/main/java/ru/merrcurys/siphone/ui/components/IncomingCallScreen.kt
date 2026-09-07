package ru.merrcurys.siphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.merrcurys.siphone.data.models.Contact
import ru.merrcurys.siphone.ui.theme.appTheme

// Экран входящего звонка: крупное имя контакта (если найден), мелко — SIP-адрес,
// аватарка контакта (WEBP). Показывается поверх вкладок, пока приложение открыто.
@Composable
fun IncomingCallScreen(
    callerRaw: String,
    contact: Contact?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val screenInteractionSource = remember { MutableInteractionSource() }
    val background = Brush.verticalGradient(
        colors = listOf(scheme.primary, scheme.secondary)
    )
    val rawAddress = displayAddress(callerRaw)
    val title = contact?.name ?: rawAddress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = screenInteractionSource,
                indication = null,
                // Не даём тапам пройти на вкладки под этим оверлеем
                onClick = {}
            )
            .background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "ВХОДЯЩИЙ ЗВОНОК",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            ContactAvatar(
                avatarPath = contact?.avatarPath,
                size = 132.dp,
                backgroundColor = Color.White.copy(alpha = 0.18f)
            )

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (contact != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = rawAddress,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IncomingCallButton(
                    label = "Отклонить",
                    icon = { iconColor ->
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Отклонить вызов",
                            modifier = Modifier.size(28.dp),
                            tint = iconColor
                        )
                    },
                    backgroundColor = scheme.error,
                    onClick = onDecline
                )

                IncomingCallButton(
                    label = "Ответить",
                    icon = { iconColor ->
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Ответить на звонок",
                            modifier = Modifier.size(28.dp),
                            tint = iconColor
                        )
                    },
                    backgroundColor = Color(0xFF2EAF50),
                    onClick = onAccept
                )
            }

            Spacer(modifier = Modifier.height(44.dp))
        }
    }
}

@Composable
private fun IncomingCallButton(
    label: String,
    icon: @Composable (Color) -> Unit,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            icon(Color.White)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun IncomingCallScreenPreview() {
    appTheme {
        IncomingCallScreen(
            callerRaw = "call2sip05318201@call2sip.onlinepbx.ru",
            contact = Contact(id = "1", name = "Василий", sipAddress = "call2sip05318201@call2sip.onlinepbx.ru"),
            onAccept = {},
            onDecline = {}
        )
    }
}
