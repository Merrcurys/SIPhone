package ru.merrcurys.siphone

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import ru.merrcurys.siphone.data.repositories.SettingsRepository
import ru.merrcurys.siphone.data.repositories.ThemeMode
import ru.merrcurys.siphone.sip.SipManager
import ru.merrcurys.siphone.ui.components.CallScreen
import ru.merrcurys.siphone.ui.components.IncomingCallScreen
import ru.merrcurys.siphone.ui.screens.DialScreen
import ru.merrcurys.siphone.ui.screens.SettingsScreen
import ru.merrcurys.siphone.ui.theme.appTheme

private const val SCREEN_ANIM_MS = 320

class MainActivity : ComponentActivity() {
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            val context = LocalContext.current
            val settingsRepository = remember { SettingsRepository(context) }
            var themeMode by remember { mutableStateOf(settingsRepository.getThemeMode()) }
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            appTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        settingsRepository.saveThemeMode(mode)
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    var isSettings by rememberSaveable { mutableStateOf(false) }
    var outgoingNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var incomingNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    var mockServer by remember { mutableStateOf(settingsRepository.isMockServer()) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Один общий SIP-контроллер на всё приложение. Пересоздаётся при смене mock-режима.
    val sipManager = remember(mockServer) { SipManager(context.applicationContext) }

    // Пока приложение открыто — держим регистрацию, чтобы приходили входящие звонки.
    // Как только приложение уходит в фон/закрывается — снимаем регистрацию (сторонние
    // не смогут позвонить) и освобождаем ресурсы ядра.
    DisposableEffect(lifecycleOwner.lifecycle, sipManager) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> coroutineScope.launch { sipManager.startRegistration() }
                Lifecycle.Event.ON_STOP -> coroutineScope.launch { sipManager.stopRegistration() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Повторная регистрация после выхода из настроек (сервер/SIP ID могли измениться).
    LaunchedEffect(isSettings) {
        if (!isSettings) {
            sipManager.startRegistration()
        }
    }

    val activeCallNumber = outgoingNumber ?: incomingNumber
    val incomingCaller by sipManager.incomingCaller.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val number = pendingCallNumber
        pendingCallNumber = null
        if (granted && number != null) {
            outgoingNumber = number
        } else if (!granted) {
            Toast.makeText(context, "Для звонка нужен доступ к микрофону", Toast.LENGTH_LONG).show()
        }
    }

    val startCall: (String) -> Unit = { number ->
        val micAllowed = mockServer ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        if (micAllowed) {
            outgoingNumber = number
        } else {
            pendingCallNumber = number
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val finishCall = {
        // Завершаем вызов в общем контроллере, затем закрываем экран звонка
        coroutineScope.launch { sipManager.endCall() }
        outgoingNumber = null
        incomingNumber = null
    }

    BackHandler(enabled = isSettings && activeCallNumber == null) {
        isSettings = false
    }

    BackHandler(enabled = activeCallNumber != null) {
        finishCall()
    }

    // Физическая кнопка «Назад» во время входящего звонка отклоняет его
    BackHandler(enabled = incomingCaller != null && activeCallNumber == null) {
        sipManager.declineIncomingCall()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold { innerPadding ->
            AnimatedContent(
                targetState = isSettings,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = {
                    if (targetState) {
                        (scaleIn(tween(SCREEN_ANIM_MS), initialScale = 0.8f) +
                            fadeIn(tween(SCREEN_ANIM_MS)))
                            .togetherWith(
                                scaleOut(tween(SCREEN_ANIM_MS), targetScale = 0.9f) +
                                    fadeOut(tween(SCREEN_ANIM_MS))
                            )
                    } else {
                        (scaleIn(tween(SCREEN_ANIM_MS), initialScale = 0.9f) +
                            fadeIn(tween(SCREEN_ANIM_MS)))
                            .togetherWith(
                                scaleOut(tween(SCREEN_ANIM_MS), targetScale = 0.9f) +
                                    fadeOut(tween(SCREEN_ANIM_MS))
                            )
                    }
                },
                label = "main_screen"
            ) { showSettings ->
                if (showSettings) {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onBack = { isSettings = false },
                        onMockServerChange = { enabled ->
                            // Останавливаем старый контроллер (Linphone остался бы
                            // зарегистрированным) и пересоздаём фасад под новый режим.
                            if (!enabled || mockServer != enabled) {
                                coroutineScope.launch { sipManager.stopRegistration() }
                            }
                            mockServer = enabled
                        }
                    )
                } else {
                    DialScreen(
                        onOpenSettings = { isSettings = true },
                        onStartCall = startCall
                    )
                }
            }
        }

        // Экран входящего звонка (виден только пока приложение открыто и нет активного вызова)
        if (incomingCaller != null && activeCallNumber == null) {
            IncomingCallScreen(
                caller = incomingCaller ?: "",
                onAccept = {
                    // Запоминаем номер до accept — accept сразу очищает incomingCaller
                    val caller = incomingCaller
                    sipManager.acceptIncomingCall()
                    if (caller != null) {
                        incomingNumber = caller
                    }
                },
                onDecline = { sipManager.declineIncomingCall() }
            )
        }

        val callNumber = activeCallNumber
        if (callNumber != null) {
            CallScreen(
                phoneNumber = callNumber,
                sipId = settingsRepository.getSipId(),
                sipPassword = settingsRepository.getSipPassword(),
                onHangup = finishCall,
                sipManager = sipManager,
                autoDial = incomingNumber == null
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainPreview() {
    appTheme {
        MainScreen(themeMode = ThemeMode.SYSTEM, onThemeModeChange = {})
    }
}
