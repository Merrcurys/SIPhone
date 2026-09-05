package ru.merrcurys.siphone.sip

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import ru.merrcurys.siphone.data.repositories.SettingsRepository

// Реальная реализация звонков через ядро Linphone.
class LinphoneSipController(private val context: Context) : SipCallController {

    private val settingsRepository = SettingsRepository(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var core: Core? = null
    private var currentCall: Call? = null

    private val _callState = MutableStateFlow("Звонок...")
    override val callState: StateFlow<String> = _callState

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted

    private val _isSpeakerOn = MutableStateFlow(false)
    override val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn

    private val _isCallEnded = MutableStateFlow(false)
    override val isCallEnded: StateFlow<Boolean> = _isCallEnded

    // Инициализация SIP ядра
    override fun initCore() {
        if (core != null) return

        val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable) {
            Factory.instance().enableLogcatLogs(true)
        }

        core = Factory.instance().createCore(null, null, context).apply {
            addListener(coreListener)
            isMicEnabled = true
            setRingback(RingbackTone.file(context).absolutePath)
            config.setBool("sound", "echocancellation", true)
            config.setBool("sound", "echo_limiter", true)
        }
    }

    // createAddress — единственный способ распарсить Address из строки в SDK 5.5.8.
    @Suppress("DEPRECATION")
    private fun Core.createSipAddress(uri: String): Address? = createAddress(uri)

    // Проверка доступности сети
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Инициирование вызова
    override suspend fun makeCall(
        phoneNumber: String,
        sipId: String?,
        sipPassword: String?
    ): Boolean {
        val target = phoneNumber.trim()

        val core = core ?: run {
            _callState.value = "SIP не инициализирован"
            return false
        }

        if (sipId.isNullOrBlank() || sipPassword.isNullOrBlank()) {
            _callState.value = "SIP ID или пароль не указаны"
            return false
        }
        if (!isNetworkAvailable()) {
            _callState.value = "Нет интернет-соединения"
            return false
        }
        val serverIp = settingsRepository.getServerIp()
        if (serverIp.isNullOrBlank()) {
            _callState.value = "SIP-домен (IP сервера) не настроен"
            return false
        }

        _isCallEnded.value = false
        _isMuted.value = false

        try {
            Log.d(TAG, "Инициализация SIP ядра...")
            core.start()

            core.clearAccounts()
            core.clearAllAuthInfo()

            val transports = core.transports
            transports.udpPort = 5060
            transports.tcpPort = 0
            transports.tlsPort = 0
            core.transports = transports
            core.isNetworkReachable = true

            val authInfo = Factory.instance().createAuthInfo(
                sipId,
                null,
                sipPassword,
                null,
                serverIp,
                serverIp
            )
            core.addAuthInfo(authInfo)
            Log.d(TAG, "Данные аутентификации добавлены")

            val identityAddress = core.createSipAddress("sip:$sipId@$serverIp")
            val serverAddress = core.createSipAddress("sip:$serverIp;transport=udp")
            if (identityAddress == null || serverAddress == null) {
                _callState.value = "Неверный формат SIP-адресов"
                releaseCallResources(core)
                return false
            }

            val accountParams = core.createAccountParams().apply {
                this.identityAddress = identityAddress
                this.serverAddress = serverAddress
                isRegisterEnabled = true
                setTransport(TransportType.Udp)
            }

            val account = core.createAccount(accountParams)
            core.addAccount(account)
            core.defaultAccount = account
            Log.d(TAG, "Аккаунт успешно настроен")

            // Регистрация проходит в фоне; ждем терминального состояния вместо фиксированной задержки
            Log.d(TAG, "Выполнение регистрации...")
            core.refreshRegisters()
            val deadline = System.currentTimeMillis() + REGISTRATION_TIMEOUT_MS
            while (account.state !in REGISTRATION_TERMINAL_STATES && System.currentTimeMillis() < deadline) {
                delay(REGISTRATION_POLL_MS)
            }

            if (account.state != RegistrationState.Ok) {
                _callState.value = when (account.state) {
                    RegistrationState.Failed -> registrationFailureText(account, "")
                    RegistrationState.Cleared -> "Регистрация отменена."
                    else -> "Превышено время ожидания ответа сервера. Проверьте IP сервера и интернет."
                }
                releaseCallResources(core)
                return false
            }

            Log.d(TAG, "Инициирование вызова...")
            val targetAddress = core.createSipAddress("sip:$target@$serverIp")
            if (targetAddress == null) {
                _callState.value = "Неверный формат номера получателя"
                releaseCallResources(core)
                return false
            }

            currentCall = core.inviteAddress(targetAddress)
            if (currentCall == null) {
                _callState.value = "Не удалось начать вызов"
                releaseCallResources(core)
                return false
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка: ${e.localizedMessage}", e)
            _callState.value = if (isMeaningless(e.localizedMessage)) {
                "Не удалось установить звонок. Попробуйте ещё раз."
            } else {
                "Ошибка: ${e.localizedMessage}"
            }
            core.runCatching { releaseCallResources(this) }
            return false
        }
    }

    // Завершение вызова и освобождение ресурсов
    override suspend fun endCall() {
        // NonCancellable: завершаем очистку, даже если вызвавший coroutine-скоп уже отменен
        withContext(NonCancellable) {
            try {
                Log.d(TAG, "Очистка ресурсов...")
                currentCall?.terminate()
                currentCall = null
                delay(500)

                core?.runCatching { releaseCallResources(this) }

                resetAudioForIdle()
                _isMuted.value = false
                _isSpeakerOn.value = false
                _isCallEnded.value = true
                Log.d(TAG, "Ресурсы успешно освобождены")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при завершении вызова: ${e.localizedMessage}", e)
            }
        }
    }

    // Управление микрофоном
    override fun toggleMute() {
        _isMuted.value = !_isMuted.value
        core?.isMicEnabled = !_isMuted.value
        audioManager.isMicrophoneMute = _isMuted.value
        Log.d(TAG, "Микрофон ${if (_isMuted.value) "выключен" else "включен"}")
    }

    // Управление громкой связью
    override fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        setSpeakerphoneOn(_isSpeakerOn.value)
    }

    // Настройка аудио при установлении соединения
    private fun configureAudioForCall() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (!audioManager.isMicrophoneMute) {
                core?.isMicEnabled = true
                _isMuted.value = false
                Log.d(TAG, "Микрофон включен при установлении соединения")
            }
            setSpeakerphoneOn(_isSpeakerOn.value)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при настройке аудио: ${e.localizedMessage}", e)
        }
    }

    private fun resetAudioForIdle() {
        try {
            setSpeakerphoneOn(false)
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сбросе аудио: ${e.localizedMessage}", e)
        }
    }

    // isSpeakerphoneOn помечен deprecated в API 34; на Android 12+ используем
    // communication device (динамик), на старых версиях — прежний способ.
    private fun setSpeakerphoneOn(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                val speaker = audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
    }

    private fun releaseCallResources(core: Core) {
        core.clearAccounts()
        core.clearAllAuthInfo()
        core.stop()
    }

    // Linphone отдает "None"/"null" вместо отсутствующего текста — считаем это пустотой.
    private fun isMeaningless(value: String?): Boolean =
        value.isNullOrBlank() || value == "None" || value == "null"

    // Понятное сообщение при неудачной регистрации аккаунта.
    private fun registrationFailureText(account: Account, raw: String): String {
        val info = account.errorInfo
        val mapped = when (info.reason) {
            Reason.Forbidden, Reason.Unauthorized -> "Неверный SIP ID или пароль"
            Reason.NotFound, Reason.NoMatch -> "Аккаунт с таким SIP ID не найден на сервере"
            Reason.IOError, Reason.NoResponse, Reason.ServerTimeout ->
                "Нет связи с сервером: проверьте IP сервера и интернет"
            Reason.TemporarilyUnavailable, Reason.Busy, Reason.DoNotDisturb ->
                "Сервер временно недоступен, попробуйте позже"
            Reason.BadGateway -> "Ошибка на стороне сервера"
            Reason.NotImplemented, Reason.NotAcceptable, Reason.UnsupportedContent ->
                "Сервер отклонил запрос"
            else -> null
        }
        if (mapped != null) return mapped

        val phrase = info.phrase
        val detail = when {
            info.protocolCode > 0 -> " (код ${info.protocolCode})"
            !isMeaningless(phrase) -> ": ${phrase?.trim()}"
            !isMeaningless(raw) -> ": ${raw.trim()}"
            else -> ""
        }
        return "Не удалось зарегистрироваться на сервере$detail. " +
            "Проверьте SIP ID, пароль и IP сервера."
    }

    // Понятное сообщение, если звонок завершился ошибкой.
    private fun callFailureText(call: Call, raw: String): String {
        val info = call.errorInfo
        val mapped = when (info.reason) {
            Reason.Forbidden, Reason.Unauthorized -> "Нет прав на этот вызов"
            Reason.NotFound, Reason.NoMatch -> "Номер не найден на сервере"
            Reason.Busy -> "Абонент занят"
            Reason.Declined -> "Вызов отклонен"
            Reason.TemporarilyUnavailable, Reason.NotAnswered -> "Абонент не отвечает"
            Reason.DoNotDisturb -> "Абонент сейчас не принимает звонки"
            Reason.AddressIncomplete -> "Неверный формат номера"
            Reason.IOError, Reason.NoResponse, Reason.ServerTimeout ->
                "Нет связи с сервером: проверьте интернет"
            else -> null
        }
        if (mapped != null) return mapped

        val phrase = info.phrase
        val detail = when {
            info.protocolCode > 0 -> " (код ${info.protocolCode})"
            !isMeaningless(phrase) -> ": ${phrase?.trim()}"
            !isMeaningless(raw) -> ": ${raw.trim()}"
            else -> ""
        }
        return "Не удалось установить соединение$detail"
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            val logMessage = when (state) {
                RegistrationState.Ok -> "Успешная регистрация аккаунта"
                RegistrationState.Failed -> registrationFailureText(account, message)
                RegistrationState.Progress -> "Выполняется регистрация..."
                else -> "Неизвестное состояние регистрации: $state"
            }
            Log.d(TAG, "$logMessage для аккаунта ${account.params.identityAddress?.asStringUriOnly()}")

            // None/Cleared приходят, когда мы сами чистим аккаунт после ошибки —
            // не затираем ими понятное сообщение на экране.
            val text = when (state) {
                RegistrationState.Ok -> "Успешная регистрация"
                RegistrationState.Failed -> logMessage
                RegistrationState.Progress -> "Идет регистрация..."
                else -> null
            }
            if (text != null) {
                _callState.value = text
            }
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            val logMessage = when (state) {
                Call.State.Idle -> "Состояние вызова: Ожидание"
                Call.State.IncomingReceived -> "Входящий вызов получен"
                Call.State.OutgoingInit -> "Инициализация исходящего вызова..."
                Call.State.OutgoingProgress -> "Выполнение вызова..."
                Call.State.OutgoingRinging -> "Вызов осуществляется..."
                Call.State.Connected -> "Соединение установлено"
                Call.State.StreamsRunning -> "Медиапоток активирован"
                Call.State.Paused -> "Вызов на паузе"
                Call.State.Resuming -> "Возобновление вызова..."
                Call.State.Referred -> "Вызов переадресован"
                Call.State.Error -> callFailureText(call, message)
                Call.State.End -> "Вызов завершен"
                Call.State.Released -> "Ресурсы вызова освобождены"
                else -> "Неизвестное состояние вызова: $state"
            }
            Log.d(TAG, logMessage)

            _callState.value = logMessage

            if (state == Call.State.Connected || state == Call.State.StreamsRunning) {
                configureAudioForCall()
            }

            _isCallEnded.value = state == Call.State.End || state == Call.State.Error || state == Call.State.Released
        }
    }

    companion object {
        private const val TAG = "LinphoneSipController"
        private const val REGISTRATION_TIMEOUT_MS = 10_000L
        private const val REGISTRATION_POLL_MS = 100L
        private val REGISTRATION_TERMINAL_STATES = setOf(
            RegistrationState.Ok,
            RegistrationState.Failed,
            RegistrationState.Cleared
        )
    }
}
