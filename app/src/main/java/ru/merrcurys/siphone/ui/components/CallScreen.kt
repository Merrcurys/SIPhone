package ru.merrcurys.siphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@Composable
fun CallScreen(
    phoneNumber: String,
    sipId: String?,
    sipPassword: String?,
    onHangup: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sipManager = remember { SipManager(context) }

    val callStateText by sipManager.callState.collectAsState()
    val isMuted by sipManager.isMuted.collectAsState()
    val isSpeakerOn by sipManager.isSpeakerOn.collectAsState()
    val isCallEnded by sipManager.isCallEnded.collectAsState()

    val scheme = MaterialTheme.colorScheme
    val background = Brush.verticalGradient(
        colors = listOf(scheme.primary, scheme.secondary)
    )

    fun hangup() {
        coroutineScope.launch {
            sipManager.endCall()
        }
        onHangup()
    }

    LaunchedEffect(Unit) {
        sipManager.initCore()
    }

    LaunchedEffect(phoneNumber, sipId, sipPassword) {
        sipManager.makeCall(phoneNumber, sipId, sipPassword)
    }

    LaunchedEffect(isCallEnded) {
        if (isCallEnded) {
            delay(2000)
            onHangup()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                sipManager.endCall()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                text = callStateText.uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.85f)
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
                    onClick = { sipManager.toggleMute() }
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
                            .clickable(onClick = ::hangup),
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
                    onClick = { sipManager.toggleSpeaker() }
                )
            }

            Spacer(modifier = Modifier.height(56.dp))
        }
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
