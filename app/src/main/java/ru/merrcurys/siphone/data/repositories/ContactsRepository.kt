package ru.merrcurys.siphone.data.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import ru.merrcurys.siphone.data.models.Contact
import java.io.File
import java.io.InputStream
import java.util.UUID

// Хранилище контактов: список в JSON (SharedPreferences), аватарки — файлы WEBP
// во внутреннем хранилище. Всё хранится локально на устройстве.
class ContactsRepository private constructor(context: Context) {

    private val prefs =
        context.getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)

    private val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }

    private val _contacts = MutableStateFlow(loadFromPrefs())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private fun loadFromPrefs(): List<Contact> {
        val raw = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Contact(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    sipAddress = obj.getString("address"),
                    avatarPath = obj.optString("avatar").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<Contact>) {
        val array = JSONArray()
        list.forEach { c ->
            array.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("address", c.sipAddress)
                    .put("avatar", c.avatarPath ?: "")
            )
        }
        prefs.edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    private fun update(transform: (List<Contact>) -> List<Contact>) {
        val next = transform(_contacts.value)
        _contacts.value = next
        persist(next)
    }

    fun addContact(name: String, sipAddress: String): Contact {
        val contact = Contact(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            sipAddress = sipAddress.trim()
        )
        update { it + contact }
        return contact
    }

    fun updateContact(contact: Contact) {
        update { list -> list.map { if (it.id == contact.id) contact else it } }
    }

    fun deleteContact(id: String) {
        val existing = _contacts.value.firstOrNull { it.id == id }
        existing?.avatarPath?.let { runCatching { File(it).delete() } }
        deleteAvatarFile(id)
        update { list -> list.filterNot { it.id == id } }
    }

    fun contactById(id: String?): Contact? =
        _contacts.value.firstOrNull { it.id == id }

    fun findContactByAddress(raw: String?): Contact? =
        findContact(_contacts.value, raw)

    // Сохраняет аватар как WEBP во внутреннем хранилище. Возвращает путь к файлу.
    fun saveAvatar(contactId: String, uri: Uri, resolver: android.content.ContentResolver): String? {
        return try {
            val input: InputStream = resolver.openInputStream(uri) ?: return null
            val original = input.use { BitmapFactory.decodeStream(it) } ?: return null
            val bitmap = scaleDown(original, MAX_AVATAR_SIZE)
            if (original !== bitmap) original.recycle()

            deleteAvatarFile(contactId)
            val file = File(avatarDir, "$contactId.webp")
            file.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.WEBP, AVATAR_QUALITY, out)) {
                    bitmap.recycle()
                    return null
                }
            }
            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deleteAvatarFile(contactId: String) {
        val old = File(avatarDir, "$contactId.webp")
        if (old.exists()) old.delete()
    }

    private fun scaleDown(source: Bitmap, maxSize: Int): Bitmap {
        val width = source.width
        val height = source.height
        val maxDim = maxOf(width, height)
        if (maxDim <= maxSize) return source
        val scale = maxSize.toFloat() / maxDim
        return Bitmap.createScaledBitmap(
            source,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    companion object {
        private const val KEY_CONTACTS = "contacts_json"
        private const val MAX_AVATAR_SIZE = 512
        private const val AVATAR_QUALITY = 85

        @Volatile
        private var instance: ContactsRepository? = null

        // Единый экземпляр на процесс, чтобы контроллер звонков и UI видели
        // одни и те же данные (StateFlow общий).
        fun getInstance(context: Context): ContactsRepository =
            instance ?: synchronized(this) {
                instance ?: ContactsRepository(context.applicationContext).also { instance = it }
            }

        private fun normalize(value: String): String =
            value.trim().lowercase().removePrefix("sip:")

        // Совпадает ли адрес контакта с номером/SIP-адресом из журнала или звонка
        fun addressMatches(contactAddress: String, raw: String?): Boolean {
            if (raw.isNullOrBlank()) return false
            val target = normalize(raw)
            val stored = normalize(contactAddress)
            if (stored == target) return true
            val targetUser = target.substringBefore('@')
            val storedUser = stored.substringBefore('@')
            return targetUser.isNotBlank() && storedUser == targetUser
        }

        // Ищет контакт по адресу, отдавая предпочтение точному совпадению
        fun findContact(contacts: List<Contact>, raw: String?): Contact? {
            if (raw.isNullOrBlank()) return null
            val normalized = normalize(raw)
            val candidates = contacts.filter { addressMatches(it.sipAddress, normalized) }
            if (candidates.isEmpty()) return null
            return candidates.firstOrNull { normalize(it.sipAddress) == normalized }
                ?: candidates.first()
        }
    }
}
