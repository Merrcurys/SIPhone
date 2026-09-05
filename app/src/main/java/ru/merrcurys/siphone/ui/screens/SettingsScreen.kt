package ru.merrcurys.siphone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.merrcurys.siphone.BuildConfig
import ru.merrcurys.siphone.data.repositories.SettingsRepository
import ru.merrcurys.siphone.data.repositories.ThemeMode
import ru.merrcurys.siphone.ui.theme.appTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val APP_NAME = "SiPhone"
private const val APP_AUTHOR = "Себежко Александр Андреевич"
private const val PROJECT_SOURCE_URL = "https://github.com/Merrcurys/SiPhone"

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var serverIp by remember { mutableStateOf(settingsRepository.getServerIp() ?: "") }
    var sipId by remember { mutableStateOf(settingsRepository.getSipId() ?: "") }
    var sipPassword by remember { mutableStateOf(settingsRepository.getSipPassword() ?: "") }
    var sipPasswordVisible by remember { mutableStateOf(false) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    var isMockServer by remember { mutableStateOf(settingsRepository.isMockServer()) }
    var showAboutSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                    center = Offset(size.width * 0.98f, size.height * 0.06f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            scheme.secondary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension * 0.5f,
                    center = Offset(size.width * 0.02f, size.height * 0.55f)
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = scheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Настройки",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground
                    )
                    Text(
                        text = "SIP-соединение и оформление",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            SectionLabel(text = "Внешний вид")

            SettingsCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(icon = {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        })
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Тема оформления",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "По умолчанию — как в системе",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ThemeSegmentedControl(
                        selected = themeMode,
                        onSelect = onThemeModeChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(text = "Сервер")

            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBox(icon = {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    })
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mock-сервер",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isMockServer) {
                                "Тестовые звонки без реального SIP-сервера"
                            } else {
                                "Звонки через реальный SIP-сервер"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isMockServer,
                        onCheckedChange = { checked ->
                            isMockServer = checked
                            settingsRepository.setMockServer(checked)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(text = "SIP-аккаунт")

            SettingsCard {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingsTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it },
                        label = "IP сервера",
                        icon = Icons.Default.Dns,
                        leadingContentDescription = "IP сервера"
                    )
                    SettingsTextField(
                        value = sipId,
                        onValueChange = { sipId = it },
                        label = "SIP ID",
                        icon = Icons.Default.AccountCircle,
                        leadingContentDescription = "SIP ID"
                    )
                    OutlinedTextField(
                        value = sipPassword,
                        onValueChange = { sipPassword = it },
                        label = { Text("SIP Password") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "SIP Password"
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { sipPasswordVisible = !sipPasswordVisible }) {
                                Icon(
                                    imageVector = if (sipPasswordVisible) {
                                        Icons.Default.Visibility
                                    } else {
                                        Icons.Default.VisibilityOff
                                    },
                                    contentDescription = if (sipPasswordVisible) {
                                        "Скрыть пароль"
                                    } else {
                                        "Показать пароль"
                                    }
                                )
                            }
                        },
                        visualTransformation = if (sipPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(18.dp),
                        spotColor = scheme.primary,
                        ambientColor = scheme.primary
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(scheme.primary, scheme.secondary)
                        )
                    )
                    .clickable {
                        settingsRepository.saveServerIp(serverIp.trim())
                        settingsRepository.saveSipId(sipId.trim())
                        settingsRepository.saveSipPassword(sipPassword)
                        showSaveSuccess = true
                        scope.launch {
                            delay(2000)
                            showSaveSuccess = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Сохранить",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimary
                    )
                }
            }

            AnimatedVisibility(
                visible = showSaveSuccess,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = scheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Настройки SIP успешно сохранены",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            SectionLabel(text = "О приложении")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = scheme.surface.copy(alpha = 0.9f),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showAboutSheet = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "О приложении",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showAboutSheet) {
        AboutSheet(onDismiss = { showAboutSheet = false })
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = scheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "О приложении",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            AboutRow(label = "Название", value = APP_NAME)
            AboutRow(label = "Версия", value = "v${BuildConfig.VERSION_NAME}")
            AboutRow(label = "Автор", value = APP_AUTHOR)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "$APP_NAME — SIP-звонилка.\n\n" +
                    "Код приложения распространяется под лицензией MIT.\n\n" +
                    "VoIP-функции реализованы на Linphone SDK (linphone-sdk-android), " +
                    "который распространяется под GNU AGPL-3.0.\n\n" +
                    "Весь дистрибутив приложения распространяется под GNU AGPL-3.0. " +
                    "Исходный код доступен: $PROJECT_SOURCE_URL.\n\n" +
                    "Полные тексты лицензий:\n" +
                    "• MIT: https://opensource.org/licenses/MIT\n" +
                    "• GNU AGPL-3.0: https://www.gnu.org/licenses/agpl-3.0.html",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ThemeSegmentedControl(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val options = listOf(
        ThemeMode.SYSTEM to "Система",
        ThemeMode.LIGHT to "Светлая",
        ThemeMode.DARK to "Тёмная"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp)
    ) {
        options.forEach { (mode, label) ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (isSelected) scheme.primary else Color.Transparent
                    )
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        scheme.onPrimary
                    } else {
                        scheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun IconBox(icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    leadingContentDescription: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = leadingContentDescription
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun SettingsScreenPreview() {
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

    appTheme(
        darkTheme = when (themeMode) {
            ThemeMode.SYSTEM -> false
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    ) {
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = { themeMode = it }
        )
    }
}
