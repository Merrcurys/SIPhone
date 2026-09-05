package ru.merrcurys.siphone.sip

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import ru.merrcurys.siphone.data.repositories.SettingsRepository
import ru.merrcurys.siphone.sip.mock.MockSipController

// Фасад: выбирает реальную или mock-реализацию звонков по настройкам.
// Публичный API сохранен, поэтому экраны не зависят от выбранного режима.
class SipManager(context: Context) : SipCallController {

    private val delegate: SipCallController =
        if (SettingsRepository(context).isMockServer()) {
            MockSipController(context)
        } else {
            LinphoneSipController(context)
        }

    override val callState: StateFlow<String> get() = delegate.callState
    override val isMuted: StateFlow<Boolean> get() = delegate.isMuted
    override val isSpeakerOn: StateFlow<Boolean> get() = delegate.isSpeakerOn
    override val isCallEnded: StateFlow<Boolean> get() = delegate.isCallEnded

    override fun initCore() = delegate.initCore()

    override suspend fun makeCall(
        phoneNumber: String,
        sipId: String?,
        sipPassword: String?
    ): Boolean = delegate.makeCall(phoneNumber, sipId, sipPassword)

    override suspend fun endCall() = delegate.endCall()

    override fun toggleMute() = delegate.toggleMute()

    override fun toggleSpeaker() = delegate.toggleSpeaker()
}
