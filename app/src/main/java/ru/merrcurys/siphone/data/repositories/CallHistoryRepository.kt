package ru.merrcurys.siphone.data.repositories

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import ru.merrcurys.siphone.data.models.CallRecord
import ru.merrcurys.siphone.data.models.CallType

// Журнал звонков, хранится локально в SharedPreferences (JSON).
class CallHistoryRepository private constructor(context: Context) {

    private val prefs =
        context.getSharedPreferences("call_history_prefs", Context.MODE_PRIVATE)

    private val _records = MutableStateFlow(loadFromPrefs())
    val records: StateFlow<List<CallRecord>> = _records.asStateFlow()

    private fun loadFromPrefs(): List<CallRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                CallRecord(
                    id = obj.getLong("id"),
                    number = obj.getString("number"),
                    type = CallType.valueOf(obj.getString("type")),
                    startedAt = obj.getLong("startedAt"),
                    durationSeconds = obj.optInt("duration")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<CallRecord>) {
        val array = JSONArray()
        list.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("number", r.number)
                    .put("type", r.type.name)
                    .put("startedAt", r.startedAt)
                    .put("duration", r.durationSeconds)
            )
        }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun nextId(): Long {
        val id = prefs.getLong(KEY_NEXT_ID, 1L)
        prefs.edit().putLong(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    // Добавляет запись в начало журнала (самые свежие сверху).
    fun addRecord(number: String, type: CallType, startedAt: Long): CallRecord {
        val record = CallRecord(
            id = nextId(),
            number = number,
            type = type,
            startedAt = startedAt
        )
        val next = listOf(record) + _records.value
        _records.value = next
        persist(next)
        return record
    }

    fun updateDuration(recordId: Long?, durationSeconds: Int) {
        if (recordId == null) return
        val next = _records.value.map {
            if (it.id == recordId) it.copy(durationSeconds = durationSeconds) else it
        }
        _records.value = next
        persist(next)
    }

    // Удаляет одну конкретную запись из журнала (не все звонки абонента)
    fun deleteRecord(recordId: Long) {
        val next = _records.value.filterNot { it.id == recordId }
        _records.value = next
        persist(next)
    }

    companion object {
        private const val KEY_RECORDS = "records_json"
        private const val KEY_NEXT_ID = "next_id"

        @Volatile
        private var instance: CallHistoryRepository? = null

        // Единый экземпляр на процесс, чтобы контроллер звонков и UI видели
        // одни и те же данные (StateFlow общий).
        fun getInstance(context: Context): CallHistoryRepository =
            instance ?: synchronized(this) {
                instance ?: CallHistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}
