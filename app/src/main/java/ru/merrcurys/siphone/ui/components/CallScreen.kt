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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.merrcurys.siphone.sip.SipManager
import ru.merrcurys.siphone.ui.theme.appTheme
import kotlinx.coroutines.delay

@Composable
fun CallScreen(
    phoneNumber: String,
    sipId: String?,
    sipPassword: String?,
    onHangup: () -> Unit,
    sipManager: SipManager? = null,
    autoDial: Boolean = true
) {
    val context = LocalContext.current
    val manager = sipManager ?: remember { SipManager(context.applicationContext) }

    val callStateText by manager.callState.collectAsState()
    val isInCall by manager.isInCall.collectAsState()
    val isMuted by manager.isMuted.collectAsState()
    val isSpeakerOn by manager.isSpeakerOn.collectAsState()
    val isCallEnded by manager.isCallEnded.collectAsState()

    // Таймер длительности разговора, отсчитывается после ответа собеседника
    var callDurationSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(isInCall) {
        if (isInCall) {
            callDurationSeconds = 0
            while (true) {
                delay(1000)
                callDurationSeconds++
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val screenInteractionSource = remember { MutableInteractionSource() }
    val background = Brush.verticalGradient(
        colors = listOf(scheme.primary, scheme.secondary)
    )

    LaunchedEffect(phoneNumber, sipId, sipPassword, autoDial) {
        if (autoDial) {
            manager.makeCall(phoneNumber, sipId, sipPassword)
        }
    }

    LaunchedEffect(isCallEnded) {
        if (isCallEnded) {
            delay(2000)
            onHangup()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = screenInteractionSource,
                indication = null,
                // Перехватываем тапы, чтобы они не проходили сквозь экран звонка
                // на лежащий под ним экран набора номера.
                onClick = {}
            )
            .background(background)
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 1.0f, size.height * 0.05f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = size.minDimension * 0.45f,
                    center = Offset(size.width * 0.0f, size.height * 0.9f)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.05f),
                    radius = size.minDimension * 0.5f,
                    center = Offset(size.width * 0.1f, size.height * 0.15f)
                )
            }
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
                text = if (isInCall) {
                    formatCallDuration(callDurationSeconds)
                } else {
                    callStateText.uppercase()
                },
                fontSize = if (isInCall) 26.sp else 13.sp,
                fontWeight = if (isInCall) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = if (isInCall) 0.sp else 2.sp,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = phoneNumber,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "SIP-звонок",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) {
                                "Включить микрофон"
                            } else {
                                "Выключить микрофон"
                            },
                            tint = if (isMuted) scheme.error else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    active = isMuted,
                    onClick = { manager.toggleMute() }
                )

                Box(
                    modifier = Modifier.size(88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.5f),
                                    radius = size.width / 2f,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                                )
                            }
                    )
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(scheme.error)
                            .clickable(onClick = onHangup),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Завершить звонок",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                }

                CallControlButton(
                    icon = {
                        Icon(
                            imageVector = if (isSpeakerOn) {
                                Icons.Default.VolumeUp
                            } else {
                                Icons.Default.VolumeDown
                            },
                            contentDescription = if (isSpeakerOn) {
                                "Выключить громкую связь"
                            } else {
                                "Включить громкую связь"
                            },
                            tint = if (isSpeakerOn) scheme.secondary else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    active = isSpeakerOn,
                    onClick = { manager.toggleSpeaker() }
                )
            }

            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}

// Формат длительности разговора: 05:23, а от часа — 1:02:03
private fun formatCallDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun CallControlButton(
    icon: @Composable () -> Unit,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(
                if (active) Color.White.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.15f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CallScreenPreview() {
    appTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            CallScreen(
                phoneNumber = "+7 900 000-00-00",
                sipId = "1001",
                sipPassword = "password123",
                onHangup = {}
            )
        }
    }
}
