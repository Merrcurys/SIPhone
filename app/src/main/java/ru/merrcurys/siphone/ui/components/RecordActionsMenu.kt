package ru.merrcurys.siphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Центрированное меню действий по записи последнего звонка.
// Панель — непрозрачная поверхность темы (не «стекло»).
@Composable
fun RecordActionsMenu(
    title: String,
    subtitle: String?,
    onCall: () -> Unit,
    onOpenContact: (() -> Unit)?,
    onDeleteRecord: () -> Unit,
    onDeleteContact: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surface)
                // не даём тапам по самой панели закрывать её
                .clickable(onClick = {})
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
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
            }

            if (onOpenContact != null) {
                RecordMenuActionRow(
                    label = "Карточка контакта",
                    icon = Icons.Default.Person,
                    tint = scheme.onSurface
                ) {
                    onOpenContact()
                }
            }
            RecordMenuActionRow(
                label = "Позвонить",
                icon = Icons.Default.Call,
                tint = scheme.primary
            ) {
                onCall()
            }
            RecordMenuActionRow(
                label = "Удалить из истории звонков",
                icon = Icons.Default.History,
                tint = scheme.onSurface
            ) {
                onDeleteRecord()
            }
            if (onDeleteContact != null) {
                RecordMenuActionRow(
                    label = "Удалить контакт",
                    icon = Icons.Default.Delete,
                    tint = scheme.error
                ) {
                    onDeleteContact()
                }
            }
        }
    }
}

@Composable
private fun RecordMenuActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}
