package ru.merrcurys.siphone.sip

import kotlinx.coroutines.flow.StateFlow

// Контракт контроллера звонков, единый для реального Linphone и mock-режима.
interface SipCallController {
    val callState: StateFlow<String>
    val isInCall: StateFlow<Boolean>
    val isMuted: StateFlow<Boolean>
    val isSpeakerOn: StateFlow<Boolean>
    val isCallEnded: StateFlow<Boolean>

    // true, когда аккаунт зарегистрирован на сервере и доступен для входящих
    val isRegistered: StateFlow<Boolean>

    // Номер/SIP-имя звонящего, пока идёт входящий звонок; null, если входящего нет
    val incomingCaller: StateFlow<String?>

    fun initCore()

    // Регистрация на сервере, пока приложение открыто (входящие звонки)
    suspend fun startRegistration()

    // Снятие с регистрации при уходе приложения в фон/закрытии
    suspend fun stopRegistration()

    suspend fun makeCall(phoneNumber: String, sipId: String?, sipPassword: String?): Boolean

    suspend fun endCall()

    fun acceptIncomingCall()

    fun declineIncomingCall()

    fun toggleMute()

    fun toggleSpeaker()
}
