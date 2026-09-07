package ru.merrcurys.siphone.data.models

// Локальный контакт. Аватар хранится как файл WEBP во внутреннем хранилище
// (avatarPath), остальное — в JSON в SharedPreferences.
data class Contact(
    val id: String,
    val name: String,
    val sipAddress: String,
    val avatarPath: String? = null
)

enum class CallType {
    OUTGOING,
    INCOMING,
    MISSED
}

// Запись в журнале звонков (хранится локально).
data class CallRecord(
    val id: Long,
    val number: String,
    val type: CallType,
    val startedAt: Long,
    val durationSeconds: Int = 0
)
