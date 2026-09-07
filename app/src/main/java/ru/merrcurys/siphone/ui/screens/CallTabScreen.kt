package ru.merrcurys.siphone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import ru.merrcurys.siphone.data.models.CallRecord
import ru.merrcurys.siphone.data.models.CallType
import ru.merrcurys.siphone.data.models.Contact
import ru.merrcurys.siphone.data.repositories.CallHistoryRepository
import ru.merrcurys.siphone.data.repositories.ContactsRepository
import ru.merrcurys.siphone.ui.components.ContactAvatar
import ru.merrcurys.siphone.ui.components.appDecorativeBackground
import ru.merrcurys.siphone.ui.components.displayAddress
import ru.merrcurys.siphone.ui.components.formatCallTimestamp
import ru.merrcurys.siphone.ui.components.formatDurationText

// Вкладка «Вызов»: сверху последние звонки (с живым поиском по вводу), снизу — набор.
@Composable
fun CallTabScreen(
    contactsRepository: ContactsRepository,
    callHistoryRepository: CallHistoryRepository,
    onStartCall: (String) -> Unit,
    onAddContact: (String) -> Unit,
    onOpenContact: (Contact) -> Unit,
    onLongPressRecord: (CallRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val contacts by contactsRepository.contacts.collectAsState()
    val records by callHistoryRepository.records.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var dialVisible by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // Открыта ли системная клавиатура (например, при фокусе на поле ввода)
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Прокрутка списка последних звонков скрывает клавиатуру
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (dialVisible && (index > 0 || offset > 40)) {
                dialVisible = false
            }
        }
    }

    // Живой поиск: фильтруем контакты и историю по введённому номеру/имени/SIP URI
    val query = input.trim()
    val queryLower = query.lowercase()
    val searchActive = query.isNotEmpty()

    val matchedContacts = if (searchActive) {
        contacts
            .filter {
                it.name.lowercase().contains(queryLower) ||
                    it.sipAddress.lowercase().contains(queryLower)
            }
            .sortedBy { it.name.lowercase() }
    } else {
        emptyList()
    }

    val matchedRecords = if (searchActive) {
        records.filter { record ->
            val relatedContact = ContactsRepository.findContact(contacts, record.number)
            val hit = record.number.lowercase().contains(queryLower) ||
                relatedContact?.name?.lowercase()?.contains(queryLower) == true
            hit && (relatedContact == null || matchedContacts.none { it.id == relatedContact.id })
        }
    } else {
        records
    }

    val noMatches = searchActive && matchedContacts.isEmpty() && matchedRecords.isEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .appDecorativeBackground()
    ) {
        Text(
            text = if (searchActive) "Результаты поиска" else "Последние звонки",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
        )

        // Список занимает свободное место
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (!searchActive && records.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Звонков пока нет",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (searchActive) {
                    items(matchedContacts, key = { "contact_${it.id}" }) { contact ->
                        ContactSearchRow(
                            contact = contact,
                            onClick = { onStartCall(contact.sipAddress) }
                        )
                    }
                    items(matchedRecords, key = { "record_${it.id}" }) { record ->
                        RecentCallRow(
                            record = record,
                            contact = ContactsRepository.findContact(contacts, record.number),
                            onClick = { onStartCall(record.number) },
                            onLongClick = { onLongPressRecord(record) }
                        )
                    }
                    if (noMatches) {
                        item(key = "add_contact") {
                            AddContactPromptRow(
                                value = query,
                                onAddContact = { onAddContact(query) }
                            )
                        }
                    }
                } else {
                    items(records, key = { it.id }) { record ->
                        RecentCallRow(
                            record = record,
                            contact = ContactsRepository.findContact(contacts, record.number),
                            onClick = { onStartCall(record.number) },
                            onLongClick = { onLongPressRecord(record) }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Поле ввода остаётся видимым, даже когда открыта системная клавиатура
            if (dialVisible || imeOpen) {
                DialInputField(
                    input = input,
                    onInputChange = { value -> input = sanitizeDialInput(value) },
                    onDelete = { input = input.dropLast(1) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (!imeOpen) {
                if (dialVisible) {
                    // Компактный блок цифровой клавиатуры + кнопка звонка
                    DialPadSection(
                        input = input,
                        onInputChange = { value -> input = sanitizeDialInput(value) },
                        onDelete = { input = input.dropLast(1) },
                        onCall = {
                            val target = input.trim()
                            if (target.isNotEmpty()) {
                                onStartCall(target)
                            } else {
                                // Пустое поле — звоним на последний звонок из истории
                                records.firstOrNull()?.let { onStartCall(it.number) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Кнопка возврата клавиатуры на всю ширину снизу
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.primary.copy(alpha = 0.12f))
                            .clickable { dialVisible = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Клавиатура",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary
                        )
                    }
                }
            }
        }
    }
}

// Строка контакта в результатах поиска набора
@Composable
private fun ContactSearchRow(contact: Contact, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(
            avatarPath = contact.avatarPath,
            size = 44.dp,
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

// «Добавить в контакты», если по введённому нет совпадений
@Composable
private fun AddContactPromptRow(value: String, onAddContact: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddContact)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Добавить в контакты",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary
            )
        }
    }
}

private fun sanitizeDialInput(value: String): String =
    value.filter { it.isLetterOrDigit() || it in "@._%+-:/#*" }

// Строка последнего звонка. Долгое нажатие открывает меню (в CallTabScreen — по центру экрана).
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RecentCallRow(
    record: CallRecord,
    contact: Contact?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val title = contact?.name ?: displayAddress(record.number)
    val subtitle = if (contact != null) displayAddress(record.number) else null
    val isMissed = record.type == CallType.MISSED
    val arrow = when (record.type) {
        CallType.OUTGOING -> "↗"
        CallType.INCOMING -> "↙"
        CallType.MISSED -> "✕"
    }
    // Цвета направления: исходящий — зелёный, входящий — синий, пропущенный — красный
    val arrowColor = when (record.type) {
        CallType.OUTGOING -> Color(0xFF2EAF50)
        CallType.INCOMING -> Color(0xFF1E88E5)
        CallType.MISSED -> scheme.error
    }
    val titleColor = if (isMissed) scheme.error else scheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(
            avatarPath = contact?.avatarPath,
            size = 44.dp,
            backgroundColor = scheme.surfaceVariant.copy(alpha = if (contact != null) 1f else 0.5f),
            placeholderIcon = Icons.Default.Person
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val duration = formatDurationText(record.durationSeconds)
            if (duration.isNotEmpty()) {
                Text(
                    text = duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = arrow,
                fontSize = 15.sp,
                color = arrowColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatCallTimestamp(record.startedAt),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

// Клавиши: цифра и подпись справа (как 2 АБВГ). 0 — «+», *, #
private val DIAL_ROWS: List<List<Pair<String, String>>> = listOf(
    listOf("1" to "", "2" to "АБВГ", "3" to "ДЕЖЗ"),
    listOf("4" to "ИЙКЛ", "5" to "МНОП", "6" to "РСТУ"),
    listOf("7" to "ФХЦЧ", "8" to "ШЩЪЫ", "9" to "ЬЭЮЯ"),
    listOf("*" to "", "0" to "+", "#" to "")
)

// Минималистичный блок набора: поле ввода, клавиши с виброоткликом и кнопка звонка
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DialPadSection(
    input: String,
    onInputChange: (String) -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val buzz: () -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    // «Стеклянный» стиль: полупрозрачная затемнённая заливка, сквозь которую виден фон
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val glassBackground =
        if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.14f)
    val glassBorder =
        if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.55f)

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Клавиши 3x4 на всю ширину, прямоугольные, с буквами справа
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DIAL_ROWS.forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowKeys.forEach { (digit, letters) ->
                        DialKey(
                            label = digit,
                            letters = letters,
                            glassBackground = glassBackground,
                            glassBorder = glassBorder,
                            onTap = {
                                buzz()
                                onInputChange(input + digit)
                            },
                            onLongPress = if (digit == "0") {
                                {
                                    buzz()
                                    onInputChange(input + "+")
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Широкая кнопка звонка — всегда активна. Если поле пустое, звонит
        // на последний звонок из истории.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF2EAF50))
                .clickable {
                    buzz()
                    onCall()
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Позвонить",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Позвонить",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

// Поле ввода номера. Показывается отдельно от цифровой клавиатуры, поэтому
// при открытии системной клавиатуры не скрывается вместе с ней.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DialInputField(
    input: String,
    onInputChange: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val buzz: () -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val hasInput = input.isNotEmpty()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val glassBackground =
        if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.14f)
    val glassBorder =
        if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(glassBackground)
            .border(1.dp, glassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!hasInput) {
                    Text(
                        text = "Номер или SIP-адрес",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary)
                )
            }
            if (hasInput) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(glassBackground)
                        .border(1.dp, glassBorder, CircleShape)
                        .combinedClickable(
                            onClick = {
                                buzz()
                                onDelete()
                            },
                            onLongClick = {
                                buzz()
                                onInputChange("")
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Удалить (долгое нажатие — очистить)",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    label: String,
    letters: String,
    glassBackground: Color,
    glassBorder: Color,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(glassBackground)
            .border(1.dp, glassBorder, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая колонка задаёт смещение цифры влево, а подписи букв
            // начинаются сразу после цифровой зоны — ближе к цифрам
            Spacer(modifier = Modifier.width(26.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
            }
            // Дополнительные символы справа (2 АБВГ, 0 +) — прижаты ближе к цифрам
            Box(
                modifier = Modifier.width(52.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (letters.isNotEmpty()) {
                    Text(
                        text = letters,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}
