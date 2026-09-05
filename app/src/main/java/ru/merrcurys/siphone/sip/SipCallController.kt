package ru.merrcurys.siphone.sip

import kotlinx.coroutines.flow.StateFlow

// Контракт контроллера звонков, единый для реального Linphone и mock-режима.
interface SipCallController {
    val callState: StateFlow<String>
    val isMuted: StateFlow<Boolean>
    val isSpeakerOn: StateFlow<Boolean>
    val isCallEnded: StateFlow<Boolean>

    fun initCore()

    suspend fun makeCall(phoneNumber: String, sipId: String?, sipPassword: String?): Boolean

    suspend fun endCall()

    fun toggleMute()

    fun toggleSpeaker()
}
