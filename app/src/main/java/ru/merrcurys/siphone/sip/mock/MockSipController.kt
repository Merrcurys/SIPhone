package ru.merrcurys.siphone.sip.mock

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.merrcurys.siphone.sip.RingbackTone
import ru.merrcurys.siphone.sip.SipCallController

// Псевдо SIP-сервер: эмулирует звонок без реальной сети и Linphone.
class MockSipController(private val context: Context) : SipCallController {

    private val _callState = MutableStateFlow("Звонок...")
    override val callState: StateFlow<String> = _callState

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted

    private val _isSpeakerOn = MutableStateFlow(false)
    override val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn

    private val _isCallEnded = MutableStateFlow(false)
    override val isCallEnded: StateFlow<Boolean> = _isCallEnded

    private val mockScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mockCallJob: Job? = null

    override fun initCore() = Unit

    override suspend fun makeCall(
        phoneNumber: String,
        sipId: String?,
        sipPassword: String?
    ): Boolean {
        startMockCall(phoneNumber.trim())
        return true
    }

    override suspend fun endCall() {
        mockCallJob?.cancel()
        mockCallJob = null
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isCallEnded.value = true
    }

    override fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    override fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
    }

    private fun startMockCall(phoneNumber: String) {
        mockCallJob?.cancel()
        _isCallEnded.value = false
        _isMuted.value = false
        _isSpeakerOn.value = false
        _callState.value = "Подключение к mock-серверу..."

        mockCallJob = mockScope.launch {
            delay(MOCK_CONNECT_MS)
            _callState.value = "Вызов $phoneNumber..."
            val ringback = createRingbackPlayer()
            try {
                delay(MOCK_RING_MS)
            } finally {
                releaseRingback(ringback)
            }
            _callState.value = "Соединение установлено"
            delay(MOCK_ANSWER_MS)
            _callState.value = "Идёт mock-звонок"
            delay(MOCK_DURATION_MS)
            _callState.value = "Вызов завершен"
            _isCallEnded.value = true
        }
    }

    private fun createRingbackPlayer(): MediaPlayer? = try {
        MediaPlayer().apply {
            setDataSource(RingbackTone.file(context).absolutePath)
            isLooping = true
            setVolume(0.8f, 0.8f)
            prepare()
            start()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Не удалось запустить гудки: ${e.localizedMessage}", e)
        null
    }

    private fun releaseRingback(player: MediaPlayer?) {
        if (player == null) return
        runCatching {
            player.stop()
            player.release()
        }
    }

    companion object {
        private const val TAG = "MockSipController"
        private const val MOCK_CONNECT_MS = 500L
        private const val MOCK_RING_MS = 6_000L
        private const val MOCK_ANSWER_MS = 400L
        private const val MOCK_DURATION_MS = 6_000L
    }
}
