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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import ru.merrcurys.siphone.data.models.CallRecord
import ru.merrcurys.siphone.data.models.Contact
import ru.merrcurys.siphone.data.repositories.CallHistoryRepository
import ru.merrcurys.siphone.data.repositories.ContactsRepository
import ru.merrcurys.siphone.data.repositories.SettingsRepository
import ru.merrcurys.siphone.data.repositories.ThemeMode
import ru.merrcurys.siphone.sip.SipManager
import ru.merrcurys.siphone.ui.components.CallScreen
import ru.merrcurys.siphone.ui.components.IncomingCallScreen
import ru.merrcurys.siphone.ui.components.RecordActionsMenu
import ru.merrcurys.siphone.ui.components.displayAddress
import ru.merrcurys.siphone.ui.screens.CallTabScreen
import ru.merrcurys.siphone.ui.screens.ContactsTabScreen
import ru.merrcurys.siphone.ui.screens.SettingsScreen
import ru.merrcurys.siphone.ui.theme.appTheme

private const val TAB_CALL = 0
private const val TAB_CONTACTS = 1
private const val TAB_SETTINGS = 2

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

// Сверху три вкладки: Вызов, Контакты, Настройки.
@Composable
fun MainScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_CALL) }
    var outgoingNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var incomingNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingAddContact by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOpenContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuRecord by remember { mutableStateOf<CallRecord?>(null) }
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val contactsRepository = ContactsRepository.getInstance(context)
    val callHistoryRepository = CallHistoryRepository.getInstance(context)
    var mockServer by remember { mutableStateOf(settingsRepository.isMockServer()) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Один общий SIP-контроллер на всё приложение. Пересоздаётся при смене mock-режима.
    val sipManager = remember(mockServer) { SipManager(context.applicationContext) }

    // Пока приложение открыто — держим регистрацию, чтобы приходили входящие звонки.
    // Как только приложение уходит в фон/закрывается — снимаем регистрацию.
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

    val contacts by contactsRepository.contacts.collectAsState()
    val incomingCaller by sipManager.incomingCaller.collectAsState()
    val activeCallNumber = outgoingNumber ?: incomingNumber

    fun contactFor(raw: String?): Contact? = ContactsRepository.findContact(contacts, raw)

    // Повторная регистрация при возврате на вкладку вызова (настройки могли измениться)
    LaunchedEffect(selectedTab) {
        if (selectedTab == TAB_CALL) {
            sipManager.startRegistration()
        }
    }

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
        coroutineScope.launch { sipManager.endCall() }
        outgoingNumber = null
        incomingNumber = null
    }

    BackHandler(enabled = activeCallNumber != null) {
        finishCall()
    }

    BackHandler(enabled = incomingCaller != null && activeCallNumber == null) {
        sipManager.declineIncomingCall()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Фон под системной «шторкой» (статус-бар), чтобы она не оставалась белой:
            // красим всю область окна, включая полосу со временем и батареей.
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                TopTabs(
                    selected = selectedTab,
                    onSelect = { selectedTab = it }
                )

            when (selectedTab) {
                TAB_CALL -> CallTabScreen(
                    contactsRepository = contactsRepository,
                    callHistoryRepository = callHistoryRepository,
                    onStartCall = startCall,
                    onAddContact = { address ->
                        pendingAddContact = address
                        selectedTab = TAB_CONTACTS
                    },
                    onOpenContact = { contact ->
                        pendingOpenContactId = contact.id
                        selectedTab = TAB_CONTACTS
                    },
                    onLongPressRecord = { menuRecord = it },
                    modifier = Modifier.weight(1f)
                )
                TAB_CONTACTS -> ContactsTabScreen(
                    contactsRepository = contactsRepository,
                    callHistoryRepository = callHistoryRepository,
                    onStartCall = startCall,
                    modifier = Modifier.weight(1f),
                    prefillAddress = pendingAddContact,
                    onPrefillConsumed = { pendingAddContact = null },
                    openContactId = pendingOpenContactId,
                    onOpenContactConsumed = { pendingOpenContactId = null }
                )
                else -> SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onMockServerChange = { enabled ->
                        if (!enabled || mockServer != enabled) {
                            coroutineScope.launch { sipManager.stopRegistration() }
                        }
                        mockServer = enabled
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            }
        }

        // Экран входящего звонка (только пока приложение открыто и нет активного вызова)
        val caller = incomingCaller
        if (caller != null && activeCallNumber == null) {
            IncomingCallScreen(
                callerRaw = caller,
                contact = contactFor(caller),
                onAccept = {
                    val raw = incomingCaller
                    sipManager.acceptIncomingCall()
                    if (raw != null) {
                        incomingNumber = raw
                    }
                },
                onDecline = { sipManager.declineIncomingCall() }
            )
        }

        val callNumber = activeCallNumber
        if (callNumber != null) {
            CallScreen(
                phoneNumber = callNumber,
                contact = contactFor(callNumber),
                onHangup = finishCall,
                sipManager = sipManager,
                autoDial = incomingNumber == null
            )
        }

        // Центрированное меню по долгому нажатию на запись последних звонков
        val menuRec = menuRecord
        if (menuRec != null) {
            val mc = ContactsRepository.findContact(contacts, menuRec.number)
            RecordActionsMenu(
                title = mc?.name ?: displayAddress(menuRec.number),
                subtitle = if (mc != null) displayAddress(menuRec.number) else null,
                onCall = {
                    menuRecord = null
                    startCall(menuRec.number)
                },
                onOpenContact = mc?.let { c ->
                    {
                        menuRecord = null
                        pendingOpenContactId = c.id
                        selectedTab = TAB_CONTACTS
                    }
                },
                onDeleteRecord = {
                    menuRecord = null
                    callHistoryRepository.deleteRecord(menuRec.id)
                },
                onDeleteContact = mc?.let { c ->
                    {
                        menuRecord = null
                        contactsRepository.deleteContact(c.id)
                    }
                },
                onDismiss = { menuRecord = null }
            )
        }
    }
}

@Composable
private fun TopTabs(selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tabs = listOf("Вызов" to TAB_CALL, "Контакты" to TAB_CONTACTS, "Настройки" to TAB_SETTINGS)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        tabs.forEach { (label, index) ->
            val isSelected = selected == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) scheme.primary else scheme.onSurfaceVariant
                )
                // Маленькая полоска под выбранной вкладкой
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) scheme.primary else Color.Transparent)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainPreview() {
    appTheme {
        MainScreen(themeMode = ThemeMode.SYSTEM, onThemeModeChange = {})
    }
}
