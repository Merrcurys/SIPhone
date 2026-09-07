package ru.merrcurys.siphone.sip

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import ru.merrcurys.siphone.data.repositories.SettingsRepository

// Реальная реализация звонков через ядро Linphone.
// Аккаунт регистрируется, пока приложение открыто — тогда приходят входящие звонки.
// Как только приложение уходит в фон/закрывается, регистрация снимается (stopRegistration),
// поэтому позвонить в закрытое приложение нельзя.
class LinphoneSipController(context: Context) : SipCallController {

    private val appContext = context.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Сериализует регистрацию/снятие с регистрации, чтобы не было гонок между
    // фоновой регистрацией (ON_START) и регистрацией при исходящем звонке.
    private val registrationMutex = Mutex()

    private var core: Core? = null
    private var account: Account? = null
    private var registeredUri: String? = null
    private var currentCall: Call? = null
    private var ringPlayer: MediaPlayer? = null

    private val _callState = MutableStateFlow("Звонок...")
    override val callState: StateFlow<String> = _callState

    // true, когда собеседник ответил и разговор идёт (показываем таймер длительности)
    private val _isInCall = MutableStateFlow(false)
    override val isInCall: StateFlow<Boolean> = _isInCall

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted

    private val _isSpeakerOn = MutableStateFlow(false)
    override val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn

    private val _isCallEnded = MutableStateFlow(false)
    override val isCallEnded: StateFlow<Boolean> = _isCallEnded

    private val _isRegistered = MutableStateFlow(false)
    override val isRegistered: StateFlow<Boolean> = _isRegistered

    // Номер/SIP-имя звонящего, пока идёт входящий звонок
    private val _incomingCaller = MutableStateFlow<String?>(null)
    override val incomingCaller: StateFlow<String?> = _incomingCaller

    private fun createCore(): Core {
        val isDebuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable) {
            Factory.instance().enableLogcatLogs(true)
        }

        return Factory.instance().createCore(null, null, appContext).apply {
            addListener(coreListener)
            isMicEnabled = true
            setRingback(RingbackTone.file(appContext).absolutePath)
            config.setBool("sound", "echocancellation", true)
            config.setBool("sound", "echo_limiter", true)

            val transports = this.transports
            transports.udpPort = 5060
            transports.tcpPort = 0
            transports.tlsPort = 0
            this.transports = transports
        }.also { core = it }
    }

    // createAddress — единственный способ распарсить Address из строки в SDK 5.5.8.
    @Suppress("DEPRECATION")
    private fun Core.createSipAddress(uri: String): Address? = createAddress(uri)

    // Регистрация на сервере при открытом приложении. Ошибки не показываем — экран
    // набора не сообщает о фоновой регистрации; исходящий звонок повторит её сам.
    override suspend fun startRegistration() {
        configureAndRegister(silent = true)
    }

    // Снятие с регистрации при уходе приложения в фон/закрытии. Заодно сбрасывает
    // входящий звонок и завершает активный вызов — вне приложения поток не держим.
    override suspend fun stopRegistration() {
        withContext(NonCancellable) {
            registrationMutex.withLock {
                try {
                    Log.d(TAG, "Приложение закрыто — снимаем регистрацию")
                    val hadActiveCall = currentCall != null
                    currentCall?.runCatching { terminate() }
                    currentCall = null
                    stopRingTone()
                    _incomingCaller.value = null

                    core?.runCatching {
                        clearAccounts()
                        clearAllAuthInfo()
                        stop()
                    }
                    core = null
                    account = null
                    registeredUri = null

                    resetAudioForIdle()
                    _isMuted.value = false
                    _isSpeakerOn.value = false
                    _isInCall.value = false
                    _isRegistered.value = false
                    if (hadActiveCall) {
                        _isCallEnded.value = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при снятии регистрации: ${e.localizedMessage}", e)
                }
            }
        }
    }

    // Регистрация аккаунта с ожиданием результата. silent=true — для фоновой
    // регистрации (без сообщений в callState), silent=false — при исходящем звонке.
    private suspend fun configureAndRegister(silent: Boolean): Boolean =
        registrationMutex.withLock {
            val sipId = settingsRepository.getSipId()
            val sipPassword = settingsRepository.getSipPassword()
            val serverIp = settingsRepository.getServerIp()

            if (sipId.isNullOrBlank() || sipPassword.isNullOrBlank()) {
                if (!silent) _callState.value = "SIP ID или пароль не указаны"
                return@withLock false
            }
            if (serverIp.isNullOrBlank()) {
                if (!silent) _callState.value = "SIP-домен (IP сервера) не настроен"
                return@withLock false
            }

            val identityUri = "sip:$sipId@$serverIp"
            if (account != null && registeredUri == identityUri) {
                // Уже зарегистрированы с теми же настройками
                _isRegistered.value = true
                return@withLock true
            }

            try {
                val core = core ?: createCore()
                core.start()

                core.clearAccounts()
                core.clearAllAuthInfo()

                val authInfo = Factory.instance().createAuthInfo(
                    sipId,
                    null,
                    sipPassword,
                    null,
                    serverIp,
                    serverIp
                )
                core.addAuthInfo(authInfo)

                val identityAddress = core.createSipAddress(identityUri)
                val serverAddress = core.createSipAddress("sip:$serverIp;transport=udp")
                if (identityAddress == null || serverAddress == null) {
                    if (!silent) _callState.value = "Неверный формат SIP-адресов"
                    return@withLock false
                }

                val accountParams = core.createAccountParams().apply {
                    this.identityAddress = identityAddress
                    this.serverAddress = serverAddress
                    isRegisterEnabled = true
                    setTransport(TransportType.Udp)
                }
                val created = core.createAccount(accountParams)
                core.addAccount(created)
                core.defaultAccount = created
                account = created
                registeredUri = identityUri
                _isRegistered.value = false

                Log.d(TAG, "Выполнение регистрации...")
                core.refreshRegisters()
                val deadline = System.currentTimeMillis() + REGISTRATION_TIMEOUT_MS
                while (created.state !in REGISTRATION_TERMINAL_STATES &&
                    System.currentTimeMillis() < deadline
                ) {
                    delay(REGISTRATION_POLL_MS)
                }

                return@withLock if (created.state == RegistrationState.Ok) {
                    Log.d(TAG, "Регистрация успешна ($identityUri)")
                    _isRegistered.value = true
                    true
                } else {
                    Log.w(TAG, "Регистрация не удалась: ${created.state}")
                    account = null
                    registeredUri = null
                    _isRegistered.value = false
                    if (!silent) {
                        _callState.value = when (created.state) {
                            RegistrationState.Failed -> registrationFailureText(created, "")
                            RegistrationState.Cleared -> "Регистрация отменена."
                            else ->
                                "Превышено время ожидания ответа сервера. Проверьте IP сервера и интернет."
                        }
                    }
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка регистрации: ${e.localizedMessage}", e)
                account = null
                registeredUri = null
                _isRegistered.value = false
                if (!silent) {
                    _callState.value = if (isMeaningless(e.localizedMessage)) {
                        "Не удалось зарегистрироваться на сервере."
                    } else {
                        "Ошибка: ${e.localizedMessage}"
                    }
                }
                false
            }
        }

    // Проверка доступности сети
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Инициирование исходящего вызова
    override suspend fun makeCall(phoneNumber: String): Boolean {
        val target = phoneNumber.trim()
        if (target.isEmpty()) {
            _callState.value = "Введите номер или SIP-адрес"
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
        _isInCall.value = false
        _isMuted.value = false
        _isSpeakerOn.value = false
        _callState.value = "Звонок..."
        stopRingTone()
        _incomingCaller.value = null

        if (!configureAndRegister(silent = false)) return false
        val core = core ?: return false

        try {
            Log.d(TAG, "Инициирование вызова...")
            // Убеждаемся, что микрофон не остался выключенным от прошлого вызова
            audioManager.isMicrophoneMute = false
            core.isMicEnabled = true
            val targetAddress = core.createSipAddress(toSipUri(target, serverIp))
            if (targetAddress == null) {
                _callState.value = "Неверный формат номера или SIP URI"
                return false
            }

            val call = core.inviteAddress(targetAddress)
            if (call == null) {
                _callState.value = "Не удалось начать вызов"
                return false
            }
            currentCall = call
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка: ${e.localizedMessage}", e)
            _callState.value = if (isMeaningless(e.localizedMessage)) {
                "Не удалось установить звонок. Попробуйте ещё раз."
            } else {
                "Ошибка: ${e.localizedMessage}"
            }
            return false
        }
    }

    // Завершение вызова. Регистрация сохраняется, чтобы на открытом приложении
    // продолжали приходить входящие звонки.
    override suspend fun endCall() {
        withContext(NonCancellable) {
            try {
                Log.d(TAG, "Завершение вызова...")
                currentCall?.terminate()
                currentCall = null
                delay(300)

                resetAudioForIdle()
                stopRingTone()
                _isMuted.value = false
                _isSpeakerOn.value = false
                _isInCall.value = false
                _isCallEnded.value = true
                Log.d(TAG, "Вызов завершен")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при завершении вызова: ${e.localizedMessage}", e)
            }
        }
    }

    // Принять входящий звонок
    override fun acceptIncomingCall() {
        stopRingTone()
        val call = currentCall ?: run {
            _incomingCaller.value = null
            return
        }
        _incomingCaller.value = null
        Log.d(TAG, "Принимаем входящий звонок")
        // Микрофон должен быть активен сразу после ответа
        _isMuted.value = false
        audioManager.isMicrophoneMute = false
        core?.isMicEnabled = true
        if (call.state == Call.State.IncomingReceived ||
            call.state == Call.State.IncomingEarlyMedia
        ) {
            runCatching { call.accept() }
                .onFailure { Log.e(TAG, "Не удалось принять вызов: ${it.localizedMessage}", it) }
        }
    }

    // Отклонить входящий звонок
    override fun declineIncomingCall() {
        stopRingTone()
        val call = currentCall
        _incomingCaller.value = null
        Log.d(TAG, "Отклоняем входящий звонок")
        if (call != null) {
            runCatching {
                if (call.state == Call.State.IncomingReceived) {
                    call.decline(Reason.Declined)
                } else {
                    call.terminate()
                }
            }
        }
        currentCall = null
    }

    // Управление микрофоном
    override fun toggleMute() {
        _isMuted.value = !_isMuted.value
        core?.isMicEnabled = !_isMuted.value
        audioManager.isMicrophoneMute = _isMuted.value
        Log.d(TAG, "Микрофон ${if (_isMuted.value) "выключен" else "включен"}")
    }

    // Управление громкой связью. Переключать звук на динамик напрямую через
    // AudioManager.isSpeakerphoneOn нельзя: ядро Linphone само владеет маршрутизацией
    // аудио и перезапишет такой выбор. Поэтому меняем устройство вывода через API ядра.
    override fun toggleSpeaker() {
        val enabled = !_isSpeakerOn.value
        if (setSpeakerEnabled(enabled)) {
            _isSpeakerOn.value = enabled
        }
    }

    // Переключение устройства вывода. Возвращает true, если маршрут применён
    // или менять ничего не требуется.
    private fun setSpeakerEnabled(enabled: Boolean): Boolean {
        val core = core ?: return false
        val target = if (enabled) {
            findOutputDevice(core, AudioDevice.Type.Speaker) ?: run {
                Log.w(TAG, "Динамик недоступен в списке аудиоустройств ядра")
                return false
            }
        } else {
            // Возврат к обычному устройству (наушник/BT/проводная гарнитура),
            // которое ядро выбрало бы по умолчанию.
            val defaultDevice = core.defaultOutputAudioDevice
            if (defaultDevice != null && defaultDevice.type != AudioDevice.Type.Speaker) {
                defaultDevice
            } else {
                findOutputDevice(core, AudioDevice.Type.Earpiece) ?: return true
            }
        }
        return try {
            val call = currentCall
            if (call != null) {
                call.outputAudioDevice = target
            } else {
                core.outputAudioDevice = target
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось переключить устройство вывода: ${e.localizedMessage}", e)
            false
        }
    }

    private fun findOutputDevice(core: Core, type: AudioDevice.Type): AudioDevice? =
        (core.extendedAudioDevices.asSequence() + core.audioDevices.asSequence())
            .firstOrNull {
                it.type == type && it.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
            }

    // Настройка аудио при установлении соединения
    private fun configureAudioForCall() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (!_isMuted.value) {
                // Сбрасываем возможный «залипший» mute от прошлого вызова, чтобы
                // микрофон точно был включён, когда начинается разговор.
                audioManager.isMicrophoneMute = false
                core?.isMicEnabled = true
                _isMuted.value = false
                Log.d(TAG, "Микрофон включен при установлении соединения")
            }
            // Если громкую связь включили до старта медиапотока, повторяем выбор
            // динамика: при запуске потока ядро выбирает маршрут вывода самостоятельно.
            if (_isSpeakerOn.value && !setSpeakerEnabled(true)) {
                _isSpeakerOn.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при настройке аудио: ${e.localizedMessage}", e)
        }
    }

    private fun resetAudioForIdle() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сбросе аудио: ${e.localizedMessage}", e)
        }
    }

    private fun startRingTone() {
        stopRingTone()
        ringPlayer = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(RingingTone.file(appContext).absolutePath)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось запустить рингтон: ${e.localizedMessage}", e)
            null
        }
    }

    private fun stopRingTone() {
        ringPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        ringPlayer = null
    }

    // Имя/номер звонящего для экрана входящего звонка
    private fun callerLabel(call: Call): String {
        val remote = call.remoteAddress
        val displayName = remote?.displayName
        if (!displayName.isNullOrBlank()) return displayName
        val username = remote?.username
        if (!username.isNullOrBlank()) return username
        return call.remoteAddressAsString?.takeIf { !isMeaningless(it) } ?: "Входящий звонок"
    }

    // Linphone отдает "None"/"null" вместо отсутствующего текста — считаем это пустотой.
    private fun isMeaningless(value: String?): Boolean =
        value.isNullOrBlank() || value == "None" || value == "null"

    // Приводит введённый текст к SIP URI для вызова:
    // номер или никнейм -> sip:<адрес>@<домен сервера>,
    // полный sip:/sips: URI оставляем как есть, "user@host" дополняем схемой.
    private fun toSipUri(target: String, domain: String): String {
        val trimmed = target.trim()
        if (trimmed.startsWith("sip:", ignoreCase = true) ||
            trimmed.startsWith("sips:", ignoreCase = true)
        ) {
            return trimmed
        }
        return if ('@' in trimmed) "sip:$trimmed" else "sip:$trimmed@$domain"
    }

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
            Reason.NotAcceptable -> "Сервер отклонил медиапоток (488): нет общего аудио-кодека"
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

            // Статус регистрации в callState не пишем: при открытом приложении экран
            // звонка может отсутствовать, а во время вызова текст затирал бы статус разговора.
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            val logMessage = when (state) {
                Call.State.Idle -> "Состояние вызова: Ожидание"
                Call.State.IncomingReceived -> "Входящий вызов получен"
                Call.State.IncomingEarlyMedia -> "Входящий вызов (медиа)"
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

            when (state) {
                Call.State.IncomingReceived, Call.State.IncomingEarlyMedia -> {
                    if (_isInCall.value || currentCall != null) {
                        // Уже заняты — отклоняем второй звонок (в режиме «абонент занят»)
                        Log.d(TAG, "Уже в разговоре, отклоняем новый входящий")
                        runCatching { call.decline(Reason.Busy) }
                    } else {
                        currentCall = call
                        _isCallEnded.value = false
                        _incomingCaller.value = callerLabel(call)
                        startRingTone()
                    }
                }
                Call.State.Connected, Call.State.StreamsRunning -> {
                    if (_incomingCaller.value != null) {
                        stopRingTone()
                        _incomingCaller.value = null
                    }
                    configureAudioForCall()
                }
                Call.State.End, Call.State.Error, Call.State.Released -> {
                    if (call == currentCall) {
                        stopRingTone()
                        _incomingCaller.value = null
                        currentCall = null
                    }
                }
                else -> Unit
            }

            _isInCall.value = state in IN_CALL_STATES
            _isCallEnded.value =
                state == Call.State.End || state == Call.State.Error || state == Call.State.Released
        }

        override fun onAudioDeviceChanged(core: Core, device: AudioDevice) {
            Log.d(TAG, "Аудиоустройство изменено: ${device.type}")
            _isSpeakerOn.value = device.type == AudioDevice.Type.Speaker
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
        // Состояния, при которых разговор уже идёт (таймер длительности звонка)
        private val IN_CALL_STATES = setOf(
            Call.State.Connected,
            Call.State.StreamsRunning,
            Call.State.Paused,
            Call.State.PausedByRemote,
            Call.State.Resuming,
            Call.State.UpdatedByRemote
        )
    }
}
