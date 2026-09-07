package ru.merrcurys.siphone.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.merrcurys.siphone.data.models.CallType
import ru.merrcurys.siphone.data.models.Contact
import ru.merrcurys.siphone.data.repositories.CallHistoryRepository
import ru.merrcurys.siphone.data.repositories.ContactsRepository
import ru.merrcurys.siphone.ui.components.ContactAvatar
import ru.merrcurys.siphone.ui.components.appDecorativeBackground
import ru.merrcurys.siphone.ui.components.displayAddress
import ru.merrcurys.siphone.ui.components.formatCallTimestamp
import ru.merrcurys.siphone.ui.components.formatDurationText

// Вкладка «Контакты»: поиск сверху, список, внизу кнопка добавления контакта.
@Composable
fun ContactsTabScreen(
    contactsRepository: ContactsRepository,
    callHistoryRepository: CallHistoryRepository,
    onStartCall: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefillAddress: String? = null,
    onPrefillConsumed: () -> Unit = {},
    openContactId: String? = null,
    onOpenContactConsumed: () -> Unit = {}
) {
    var selectedContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var isAdding by rememberSaveable { mutableStateOf(false) }
    var addAddress by rememberSaveable { mutableStateOf("") }
    val contacts by contactsRepository.contacts.collectAsState()

    // Переход на добавление с заранее заполненным адресом (из «Добавить в контакты»)
    LaunchedEffect(prefillAddress) {
        if (prefillAddress != null) {
            addAddress = prefillAddress
            isAdding = true
            onPrefillConsumed()
        }
    }

    // Открытие карточки контакта из другого экрана (например, «Карточка контакта» в меню)
    LaunchedEffect(openContactId) {
        if (openContactId != null) {
            selectedContactId = openContactId
            isAdding = false
            onOpenContactConsumed()
        }
    }

    when {
        selectedContactId != null -> {
            ContactDetailScreen(
                contactsRepository = contactsRepository,
                callHistoryRepository = callHistoryRepository,
                contactId = selectedContactId!!,
                onStartCall = onStartCall,
                onBack = { selectedContactId = null },
                modifier = modifier
            )
        }
        isAdding -> {
            AddContactScreen(
                contactsRepository = contactsRepository,
                initialAddress = addAddress,
                onDone = { isAdding = false },
                onBack = { isAdding = false },
                modifier = modifier
            )
        }
        else -> {
            ContactsListScreen(
                contacts = contacts,
                onContactClick = { selectedContactId = it.id },
                onAddClick = {
                    addAddress = ""
                    isAdding = true
                },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ContactsListScreen(
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val filtered = contacts
        .sortedBy { it.name.lowercase() }
        .filter {
            q.isEmpty() ||
                it.name.lowercase().contains(q) ||
                it.sipAddress.lowercase().contains(q)
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .appDecorativeBackground()
    ) {
        // Поиск сверху
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск по контактам") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (contacts.isEmpty()) "Контактов пока нет" else "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { contact ->
                    ContactListRow(
                        contact = contact,
                        onClick = { onContactClick(contact) }
                    )
                }
            }
        }

        // Кнопка добавления контакта — в самом низу
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            OutlinedButton(
                onClick = onAddClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = scheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Добавить контакт",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ContactListRow(
    contact: Contact,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(
            avatarPath = contact.avatarPath,
            size = 48.dp,
            backgroundColor = scheme.primaryContainer
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = displayAddress(contact.sipAddress),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Форма добавления контакта
@Composable
private fun AddContactScreen(
    contactsRepository: ContactsRepository,
    initialAddress: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    val canSave = name.trim().isNotEmpty() && address.trim().isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .appDecorativeBackground()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = scheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Новый контакт",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Имя") },
            placeholder = { Text("Василий") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Номер или SIP-адрес") },
            placeholder = { Text("call2sip05318201@call2sip.onlinepbx.ru") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (canSave) scheme.primary else scheme.primary.copy(alpha = 0.4f))
                .clickable(enabled = canSave) {
                    contactsRepository.addContact(name = name, sipAddress = address)
                    onDone()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Сохранить",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Аватарку можно добавить позже в карточке контакта",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}

// Карточка контакта: информация, аватарка (WEBP), история звонков
@Composable
private fun ContactDetailScreen(
    contactsRepository: ContactsRepository,
    callHistoryRepository: CallHistoryRepository,
    contactId: String,
    onStartCall: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val contacts by contactsRepository.contacts.collectAsState()
    val records by callHistoryRepository.records.collectAsState()
    val contact = contacts.firstOrNull { it.id == contactId }
    if (contact == null) {
        // Контакт удалён — просто ничего не показываем, список вернётся сам
        return
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val path = contactsRepository.saveAvatar(contact.id, uri, context.contentResolver)
            if (path != null) {
                contactsRepository.updateContact(contact.copy(avatarPath = path))
            }
        }
    }

    // Режим редактирования имени / SIP-адреса
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(contact.name) }
    var editAddress by remember { mutableStateOf(contact.sipAddress) }

    val history = records.filter {
        ContactsRepository.addressMatches(contact.sipAddress, it.number)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .appDecorativeBackground()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Шапка
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = scheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Контакт",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                editName = contact.name
                editAddress = contact.sipAddress
                isEditing = true
            }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Редактировать контакт",
                    tint = scheme.primary
                )
            }
            IconButton(onClick = {
                contactsRepository.deleteContact(contact.id)
                onBack()
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить контакт",
                    tint = scheme.error
                )
            }
        }

        // Аватарка с возможностью смены
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        ) {
            ContactAvatar(
                avatarPath = contact.avatarPath,
                size = 112.dp,
                backgroundColor = scheme.primaryContainer
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .clickable {
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Выбрать аватарку",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isEditing) {
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("Имя") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = editAddress,
                onValueChange = { editAddress = it },
                label = { Text("Номер или SIP-адрес") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            val canSave = editName.trim().isNotEmpty() && editAddress.trim().isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (canSave) scheme.primary else scheme.primary.copy(alpha = 0.4f)
                    )
                    .clickable(enabled = canSave) {
                        contactsRepository.updateContact(
                            contact.copy(
                                name = editName.trim(),
                                sipAddress = editAddress.trim()
                            )
                        )
                        isEditing = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Сохранить",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimary
                )
            }
        } else {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Text(
                text = displayAddress(contact.sipAddress),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Звонок
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF2EAF50))
                .clickable { onStartCall(contact.sipAddress) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Позвонить",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "История звонков",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (history.isEmpty()) {
            Text(
                text = "Звонков с этим контактом не было",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        } else {
            history.forEach { record ->
                val arrow = when (record.type) {
                    CallType.OUTGOING -> "↗"
                    CallType.INCOMING -> "↙"
                    CallType.MISSED -> "✕"
                }
                val arrowColor = when (record.type) {
                    CallType.OUTGOING -> Color(0xFF2EAF50)
                    CallType.INCOMING -> Color(0xFF1E88E5)
                    CallType.MISSED -> scheme.error
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = arrow,
                        color = arrowColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatCallTimestamp(record.startedAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface
                        )
                        val duration = formatDurationText(record.durationSeconds)
                        if (duration.isNotEmpty()) {
                            Text(
                                text = "Длительность: $duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}
