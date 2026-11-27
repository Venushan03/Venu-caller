package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.example.R
import com.example.ai.GeminiDialerRepository
import com.example.data.DeviceContactsRepository
import com.example.data.ScreeningCache
import com.example.model.CallerLookupResult
import com.example.model.PhoneUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "CallCoordinator"
private const val CHANNEL_ID = "prismcall_incoming_calls"
private const val NOTIFICATION_ID = 7001

enum class CallPhase {
    RINGING,
    DIALING,
    ACTIVE,
    HOLDING,
    DISCONNECTED
}

data class ActiveCallUiState(
    val phoneNumber: String,
    val normalizedNumber: String,
    val displayName: String,
    val isUnknownCaller: Boolean,
    val verificationBadge: String,
    val phase: CallPhase,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
    val isSimulated: Boolean = false,
    val cachedCategory: String? = null,
    val aiLookup: CallerLookupResult? = null,
    val isLookingUp: Boolean = false
)

object CallCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var inCallService: InCallService? = null
    private var activeTelecomCall: Call? = null
    private var timerJob: Job? = null
    private var strobeJob: Job? = null

    private val _callState = MutableStateFlow<ActiveCallUiState?>(null)
    val callState: StateFlow<ActiveCallUiState?> = _callState.asStateFlow()

    private val _quickReplies = MutableStateFlow<List<String>>(
        listOf(
            "In a meeting right now, I'll call you back soon.",
            "Can't pick up right now — please text me.",
            "Driving now, will reach out shortly."
        )
    )
    val quickReplies: StateFlow<List<String>> = _quickReplies.asStateFlow()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updatePhaseFromTelecom(state)
        }
    }

    fun attachService(service: InCallService) {
        inCallService = service
    }

    fun detachService() {
        inCallService = null
    }

    fun onTelecomCallAdded(context: Context, call: Call) {
        activeTelecomCall?.unregisterCallback(callCallback)
        activeTelecomCall = call
        call.registerCallback(callCallback)

        val rawNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val normalized = PhoneUtils.normalizeNumber(rawNumber)
        val contactName = DeviceContactsRepository(context).lookupContactName(rawNumber)
        val cachedTag = ScreeningCache(context).getCachedName(rawNumber)
        val cachedCat = ScreeningCache(context).getCachedCategory(rawNumber)
        val resolvedName = contactName ?: cachedTag ?: "Unknown Caller"
        val isUnknown = contactName == null && cachedTag == null

        val verification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (call.details?.callerNumberVerificationStatus) {
                android.telecom.Connection.VERIFICATION_STATUS_PASSED -> "Verified Network ID"
                android.telecom.Connection.VERIFICATION_STATUS_FAILED -> "Unverified / Risk"
                else -> "Standard Carrier"
            }
        } else {
            "Standard Carrier"
        }

        val initialPhase = mapTelecomState(call.state)
        _callState.value = ActiveCallUiState(
            phoneNumber = rawNumber,
            normalizedNumber = normalized,
            displayName = resolvedName,
            isUnknownCaller = isUnknown,
            verificationBadge = verification,
            phase = initialPhase,
            isSimulated = false,
            cachedCategory = cachedCat
        )

        if (initialPhase == CallPhase.ACTIVE) {
            startCallTimer()
        } else if (initialPhase == CallPhase.RINGING) {
            postIncomingNotification(context, resolvedName, rawNumber)
            launchInCallScreen(context)
        }

        if (isUnknown && rawNumber.isNotBlank()) {
            triggerUnknownNumberIntelligence(rawNumber, verification, resolvedName)
        }
    }

    fun onTelecomCallRemoved(context: Context, call: Call) {
        if (activeTelecomCall == call) {
            call.unregisterCallback(callCallback)
            activeTelecomCall = null
            stopCallTimer()
            stopStrobe(context)
            cancelNotification(context)
            _callState.value = _callState.value?.copy(phase = CallPhase.DISCONNECTED)
            scope.launch {
                delay(1200)
                if (activeTelecomCall == null) {
                    _callState.value = null
                }
            }
        }
    }

    fun startSimulatedIncomingCall(
        context: Context,
        phoneNumber: String,
        presetName: String? = null,
        verificationBadge: String = "Standard Carrier",
        enableStrobe: Boolean = false
    ) {
        stopCallTimer()
        val normalized = PhoneUtils.normalizeNumber(phoneNumber)
        val deviceName = DeviceContactsRepository(context).lookupContactName(phoneNumber)
        val cachedName = ScreeningCache(context).getCachedName(phoneNumber)
        val cachedCat = ScreeningCache(context).getCachedCategory(phoneNumber)
        val resolvedName = presetName ?: deviceName ?: cachedName ?: "Unknown Caller"
        val isUnknown = presetName == null && deviceName == null && cachedName == null

        _callState.value = ActiveCallUiState(
            phoneNumber = phoneNumber,
            normalizedNumber = normalized,
            displayName = resolvedName,
            isUnknownCaller = isUnknown,
            verificationBadge = verificationBadge,
            phase = CallPhase.RINGING,
            isSimulated = true,
            cachedCategory = cachedCat
        )

        if (enableStrobe) {
            startStrobe(context)
        }
        launchInCallScreen(context)

        if (isUnknown) {
            triggerUnknownNumberIntelligence(phoneNumber, verificationBadge, resolvedName)
        }
        loadSmartReplies(resolvedName, cachedCat ?: "")
    }

    private fun triggerUnknownNumberIntelligence(
        phoneNumber: String,
        verificationStatus: String,
        fallbackLabel: String
    ) {
        _callState.value = _callState.value?.copy(isLookingUp = true)
        scope.launch {
            val result = GeminiDialerRepository().analyzeUnknownNumber(phoneNumber, verificationStatus)
            val current = _callState.value ?: return@launch
            if (current.phoneNumber == phoneNumber) {
                result.onSuccess { lookup ->
                    _callState.value = current.copy(
                        displayName = if (current.isUnknownCaller) lookup.suggestedLabel else current.displayName,
                        aiLookup = lookup,
                        isLookingUp = false
                    )
                }.onFailure {
                    _callState.value = current.copy(
                        displayName = fallbackLabel,
                        isLookingUp = false
                    )
                }
            }
        }
    }

    private fun loadSmartReplies(callerLabel: String, noteContext: String) {
        scope.launch {
            GeminiDialerRepository().generateQuickReplies(callerLabel, noteContext)
                .onSuccess { list -> _quickReplies.value = list }
        }
    }

    fun answerCall(context: Context) {
        stopStrobe(context)
        cancelNotification(context)
        val current = _callState.value ?: return
        if (current.isSimulated) {
            _callState.value = current.copy(phase = CallPhase.ACTIVE, durationSeconds = 0L)
            startCallTimer()
        } else {
            activeTelecomCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    fun rejectOrEndCall(context: Context) {
        stopStrobe(context)
        cancelNotification(context)
        stopCallTimer()
        val current = _callState.value ?: return
        if (current.isSimulated) {
            _callState.value = current.copy(phase = CallPhase.DISCONNECTED)
            scope.launch {
                delay(900)
                if (_callState.value?.isSimulated == true && _callState.value?.phase == CallPhase.DISCONNECTED) {
                    _callState.value = null
                }
            }
        } else {
            val call = activeTelecomCall
            if (call != null) {
                if (current.phase == CallPhase.RINGING) {
                    call.reject(false, null)
                } else {
                    call.disconnect()
                }
            }
        }
    }

    fun toggleMute() {
        val current = _callState.value ?: return
        val next = !current.isMuted
        _callState.value = current.copy(isMuted = next)
        if (!current.isSimulated) {
            inCallService?.setMuted(next)
        }
    }

    fun toggleSpeaker() {
        val current = _callState.value ?: return
        val next = !current.isSpeaker
        _callState.value = current.copy(isSpeaker = next)
        if (!current.isSimulated) {
            val route = if (next) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            inCallService?.setAudioRoute(route)
        }
    }

    fun toggleHold() {
        val current = _callState.value ?: return
        if (current.isSimulated) {
            val nextPhase = if (current.phase == CallPhase.HOLDING) CallPhase.ACTIVE else CallPhase.HOLDING
            _callState.value = current.copy(phase = nextPhase)
        } else {
            if (current.phase == CallPhase.HOLDING) activeTelecomCall?.unhold() else activeTelecomCall?.hold()
        }
    }

    fun sendDtmf(digit: Char) {
        if (_callState.value?.isSimulated == false) {
            activeTelecomCall?.playDtmfTone(digit)
            scope.launch {
                delay(150)
                activeTelecomCall?.stopDtmfTone()
            }
        }
    }

    fun updateDisplayNameOverride(newName: String, category: String? = null) {
        val current = _callState.value ?: return
        _callState.value = current.copy(
            displayName = newName,
            isUnknownCaller = false,
            cachedCategory = category ?: current.cachedCategory
        )
    }

    private fun updatePhaseFromTelecom(state: Int) {
        val phase = mapTelecomState(state)
        val current = _callState.value ?: return
        _callState.value = current.copy(phase = phase)
        if (phase == CallPhase.ACTIVE && timerJob == null) {
            startCallTimer()
        } else if (phase == CallPhase.DISCONNECTED) {
            stopCallTimer()
        }
    }

    private fun mapTelecomState(state: Int): CallPhase = when (state) {
        Call.STATE_RINGING -> CallPhase.RINGING
        Call.STATE_DIALING, Call.STATE_CONNECTING -> CallPhase.DIALING
        Call.STATE_ACTIVE -> CallPhase.ACTIVE
        Call.STATE_HOLDING -> CallPhase.HOLDING
        Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> CallPhase.DISCONNECTED
        else -> CallPhase.RINGING
    }

    private fun startCallTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val current = _callState.value
                if (current != null && current.phase == CallPhase.ACTIVE) {
                    _callState.value = current.copy(durationSeconds = current.durationSeconds + 1L)
                }
            }
        }
    }

    private fun stopCallTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun launchInCallScreen(context: Context) {
        val intent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch InCallActivity", e)
        }
    }

    private fun postIncomingNotification(context: Context, title: String, number: String) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(
                    ringtoneUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            mgr.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Incoming call • $number")
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        try {
            mgr.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted", e)
        }
    }

    private fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun startStrobe(context: Context) {
        strobeJob?.cancel()
        strobeJob = scope.launch(Dispatchers.Default) {
            val camMgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return@launch
            val flashCamId = try {
                camMgr.cameraIdList.firstOrNull { id ->
                    camMgr.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            } catch (_: Exception) { null } ?: return@launch

            var on = false
            try {
                while (isActive && _callState.value?.phase == CallPhase.RINGING) {
                    on = !on
                    camMgr.setTorchMode(flashCamId, on)
                    delay(260L)
                }
            } catch (_: Exception) {
            } finally {
                try { camMgr.setTorchMode(flashCamId, false) } catch (_: Exception) {}
            }
        }
    }

    private fun stopStrobe(context: Context) {
        strobeJob?.cancel()
        strobeJob = null
        try {
            val camMgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val flashCamId = camMgr.cameraIdList.firstOrNull { id ->
                camMgr.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            camMgr.setTorchMode(flashCamId, false)
        } catch (_: Exception) {}
    }
}
