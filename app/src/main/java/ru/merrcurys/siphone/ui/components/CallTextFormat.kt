package ru.merrcurys.siphone.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Отображаемый адрес: без схемы sip:/sips: и лишних пробелов
fun displayAddress(raw: String): String =
    raw.trim().removePrefix("sip:").removePrefix("sips:").trim()

// Короткое время записи: сегодня HH:mm, иначе dd.MM HH:mm
fun formatCallTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val sameDay = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date) ==
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
    val pattern = if (sameDay) "HH:mm" else "dd.MM HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}

fun formatDurationText(totalSeconds: Int): String {
    if (totalSeconds <= 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
