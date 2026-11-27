package com.example.viewmodel

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.GeminiDialerRepository
import com.example.audio.VoiceChangerEngine
import com.example.data.DeviceContactsRepository
import com.example.data.FirestoreNotesRepository
import com.example.data.PrismSettings
import com.example.data.ScreeningCache
import com.example.data.SettingsRepository
import com.example.data.local.LocalNotesRepository
import com.example.model.CallLogEntry
import com.example.model.CallerCategory
import com.example.model.CallerLookupResult
import com.example.model.CallerTag
import com.example.model.DeviceContact
import com.example.model.DialerIntentType
import com.example.model.GeminiDialerAction
import com.example.model.NumberNote
import com.example.model.PhoneUtils
import com.example.model.UiState
import com.example.model.VoicePreset
import com.example.telecom.CallCoordinator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PrismCallVM"

class PrismCallViewModel(
    private val notesRepo: FirestoreNotesRepository,
    private val localNotesRepo: LocalNotesRepository,
    private val contactsRepo: DeviceContactsRepository,
    private val settingsRepo: SettingsRepository,
    private val geminiRepo: GeminiDialerRepository,
    private val voiceEngine: VoiceChangerEngine,
    private val currentUserId: String?
) : ViewModel() {

    private val toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_DTMF, 65)
    } catch (_: Exception) {
        null
    }

    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    private val _contacts = MutableStateFlow<List<DeviceContact>>(emptyList())
    val contacts: StateFlow<List<DeviceContact>> = _contacts.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callLogs: StateFlow<List<CallLogEntry>> = _callLogs.asStateFlow()

    private val _hasContactsPerm = MutableStateFlow(contactsRepo.hasReadContactsPermission())
    val hasContactsPerm: StateFlow<Boolean> = _hasContactsPerm.asStateFlow()

    private val _hasCallLogPerm = MutableStateFlow(contactsRepo.hasReadCallLogPermission())
    val hasCallLogPerm: StateFlow<Boolean> = _hasCallLogPerm.asStateFlow()

    private val _hasMicPerm = MutableStateFlow(voiceEngine.hasRecordAudioPermission())
    val hasMicPerm: StateFlow<Boolean> = _hasMicPerm.asStateFlow()

    val notesState: StateFlow<UiState<List<NumberNote>>> =
        (if (!currentUserId.isNullOrBlank()) {
            notesRepo.observeNumberNotes(currentUserId)
        } else {
            localNotesRepo.observeNumberNotes()
        })
            .map<List<NumberNote>, UiState<List<NumberNote>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing notes", root)
                emit(UiState.Error(root.message ?: "Failed to load notes"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val callerTagsState: StateFlow<UiState<List<CallerTag>>> =
        (if (!currentUserId.isNullOrBlank()) {
            notesRepo.observeCallerTags(currentUserId)
        } else {
            localNotesRepo.observeCallerTags()
        })
            .map<List<CallerTag>, UiState<List<CallerTag>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing caller tags", root)
                emit(UiState.Error(root.message ?: "Failed to load caller tags"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val settings: StateFlow<PrismSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PrismSettings())

    private val _geminiCommandState = MutableStateFlow<UiState<GeminiDialerAction?>>(UiState.Success(null))
    val geminiCommandState: StateFlow<UiState<GeminiDialerAction?>> = _geminiCommandState.asStateFlow()

    private val _callerLookupState = MutableStateFlow<UiState<CallerLookupResult?>>(UiState.Success(null))
    val callerLookupState: StateFlow<UiState<CallerLookupResult?>> = _callerLookupState.asStateFlow()

    private val _notePolishState = MutableStateFlow<UiState<String?>>(UiState.Success(null))
    val notePolishState: StateFlow<UiState<String?>> = _notePolishState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val matchingContacts: StateFlow<List<DeviceContact>> = combine(_dialedNumber, _contacts) { query, list ->
        if (query.isBlank()) emptyList()
        else list.filter { PhoneUtils.matchesDialQuery(query, it.name, it.phoneNumber) }.take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Voice Studio delegated state
    val voicePreset: StateFlow<VoicePreset> = voiceEngine.activePreset
    val isMonitoring: StateFlow<Boolean> = voiceEngine.isMonitoring
    val isRecording: StateFlow<Boolean> = voiceEngine.isRecording
    val isPlayingClip: StateFlow<Boolean> = voiceEngine.isPlayingClip
    val hasRecordedClip: StateFlow<Boolean> = voiceEngine.hasRecordedClip
    val amplitudeLevel: StateFlow<Float> = voiceEngine.amplitudeLevel
    val voiceError: StateFlow<String?> = voiceEngine.statusError

    init {
        refreshDeviceData()
    }

    fun refreshPermissionsAndData() {
        _hasContactsPerm.value = contactsRepo.hasReadContactsPermission()
        _hasCallLogPerm.value = contactsRepo.hasReadCallLogPermission()
        _hasMicPerm.value = voiceEngine.hasRecordAudioPermission()
        refreshDeviceData()
    }

    fun refreshDeviceData() {
        viewModelScope.launch {
            if (contactsRepo.hasReadContactsPermission()) {
                _contacts.value = contactsRepo.loadContacts()
            }
            if (contactsRepo.hasReadCallLogPermission()) {
                _callLogs.value = contactsRepo.loadCallLogs()
            }
        }
    }

    fun appendDigit(digit: String) {
        _dialedNumber.value += digit
        if (settings.value.dtmfTonesEnabled) {
            playDtmfForKey(digit.firstOrNull())
        }
    }

    fun setDialedNumber(number: String) {
        _dialedNumber.value = number
    }

    fun backspaceDigit() {
        val current = _dialedNumber.value
        if (current.isNotEmpty()) {
            _dialedNumber.value = current.dropLast(1)
        }
    }

    fun clearDialedNumber() {
        _dialedNumber.value = ""
    }

    fun placeCall(number: String = _dialedNumber.value) {
        val target = number.trim()
        if (target.isBlank()) {
            _toastMessage.value = "Enter a number to call"
            return
        }
        val launched = contactsRepo.placeCall(target)
        if (!launched) {
            _toastMessage.value = "No phone handler available on this device"
        }
    }

    fun openAddToDeviceContacts(number: String, suggestedName: String? = null) {
        contactsRepo.openAddContactIntent(number, suggestedName)
    }

    fun triggerSimulatedIncomingCall(
        context: Context,
        phoneNumber: String,
        presetName: String? = null,
        verificationBadge: String = "Standard Carrier"
    ) {
        CallCoordinator.startSimulatedIncomingCall(
            context = context,
            phoneNumber = phoneNumber,
            presetName = presetName,
            verificationBadge = verificationBadge,
            enableStrobe = settings.value.flashStrobeOnIncoming
        )
    }

    fun saveNote(
        existingId: String? = null,
        phoneNumber: String,
        contactName: String?,
        title: String,
        body: String,
        tags: List<String>,
        pinned: Boolean
    ) {
        viewModelScope.launch {
            val isCloud = !currentUserId.isNullOrBlank()
            val res = if (isCloud) {
                notesRepo.saveNumberNote(existingId, phoneNumber, contactName, title, body, tags, pinned)
            } else {
                localNotesRepo.saveNumberNote(existingId, phoneNumber, contactName, title, body, tags, pinned)
            }
            res.onSuccess {
                _toastMessage.value = when {
                    existingId != null -> "Note updated"
                    isCloud -> "Note saved to cloud"
                    else -> "Note saved locally"
                }
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to save note"
            }
        }
    }

    fun togglePinNote(note: NumberNote) {
        viewModelScope.launch {
            val res = if (!currentUserId.isNullOrBlank()) {
                notesRepo.togglePinNote(note.id, !note.pinned)
            } else {
                localNotesRepo.togglePinNote(note.id, !note.pinned)
            }
            res.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to update pin"
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            val res = if (!currentUserId.isNullOrBlank()) {
                notesRepo.deleteNumberNote(noteId)
            } else {
                localNotesRepo.deleteNumberNote(noteId)
            }
            res.onSuccess {
                _toastMessage.value = "Note deleted"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to delete note"
            }
        }
    }

    fun saveCallerTag(
        phoneNumber: String,
        displayName: String,
        category: CallerCategory,
        blocked: Boolean,
        aiSummary: String? = null
    ) {
        viewModelScope.launch {
            val res = if (!currentUserId.isNullOrBlank()) {
                notesRepo.upsertCallerTag(phoneNumber, displayName, category, blocked, aiSummary)
            } else {
                localNotesRepo.upsertCallerTag(phoneNumber, displayName, category, blocked, aiSummary)
            }
            res.onSuccess {
                _toastMessage.value = if (blocked) "Caller blocked & tagged" else "Caller tag saved"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to save caller tag"
            }
        }
    }

    fun deleteCallerTag(tagId: String) {
        viewModelScope.launch {
            val res = if (!currentUserId.isNullOrBlank()) {
                notesRepo.deleteCallerTag(tagId)
            } else {
                localNotesRepo.deleteCallerTag(tagId)
            }
            res.onSuccess {
                _toastMessage.value = "Caller tag removed"
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "Failed to remove tag"
            }
        }
    }

    fun lookupUnknownNumber(phoneNumber: String) {
        val target = phoneNumber.trim()
        if (target.isBlank()) {
            _toastMessage.value = "Type a phone number to identify"
            return
        }
        viewModelScope.launch {
            _callerLookupState.value = UiState.Loading
            geminiRepo.analyzeUnknownNumber(target, "Manual Lookup")
                .onSuccess { res -> _callerLookupState.value = UiState.Success(res) }
                .onFailure { err ->
                    _callerLookupState.value = UiState.Error(err.localizedMessage ?: "Lookup failed")
                }
        }
    }

    fun clearCallerLookup() {
        _callerLookupState.value = UiState.Success(null)
    }

    fun runGeminiCommand(query: String, onNavigateTab: (String) -> Unit, onOpenNoteForNumber: (String, String?) -> Unit) {
        val clean = query.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _geminiCommandState.value = UiState.Loading
            val contactsSummary = _contacts.value.take(25).joinToString("\n") { "${it.name}: ${it.phoneNumber}" }
            val notes = (notesState.value as? UiState.Success)?.data.orEmpty()
            val notesSummary = notes.take(20).joinToString("\n") { "${it.title} (${it.phoneNumber}): ${it.body.take(80)}" }

            geminiRepo.interpretDialerCommand(clean, contactsSummary, notesSummary)
                .onSuccess { action ->
                    _geminiCommandState.value = UiState.Success(action)
                    when (action.intentType) {
                        DialerIntentType.CALL_CONTACT -> {
                            action.targetNumber?.let { _dialedNumber.value = it }
                        }
                        DialerIntentType.LOOKUP_NUMBER -> {
                            val num = action.targetNumber ?: _dialedNumber.value
                            if (num.isNotBlank()) {
                                _dialedNumber.value = num
                                lookupUnknownNumber(num)
                            }
                        }
                        DialerIntentType.OPEN_NOTE -> onNavigateTab("notes")
                        DialerIntentType.CREATE_NOTE -> {
                            onOpenNoteForNumber(action.targetNumber ?: _dialedNumber.value, action.noteDraft)
                        }
                        DialerIntentType.VOICE_STUDIO -> {
                            val matchedPreset = VoicePreset.entries.firstOrNull {
                                it.name.equals(action.voicePresetName, ignoreCase = true)
                            }
                            if (matchedPreset != null) setVoicePreset(matchedPreset)
                            onNavigateTab("studio")
                        }
                        DialerIntentType.COMPOSE_REPLY, DialerIntentType.ANSWER_INFO -> Unit
                    }
                }
                .onFailure { err ->
                    _geminiCommandState.value = UiState.Error(err.localizedMessage ?: "Gemini request failed")
                }
        }
    }

    fun clearGeminiCommand() {
        _geminiCommandState.value = UiState.Success(null)
    }

    fun polishNoteWithGemini(rawNote: String, callerLabel: String, onPolished: (String) -> Unit) {
        if (rawNote.isBlank()) {
            _toastMessage.value = "Write a few words first to polish with Gemini"
            return
        }
        viewModelScope.launch {
            _notePolishState.value = UiState.Loading
            geminiRepo.summarizeAndPolishNote(rawNote, callerLabel)
                .onSuccess { polished ->
                    _notePolishState.value = UiState.Success(polished)
                    onPolished(polished)
                }
                .onFailure { err ->
                    _notePolishState.value = UiState.Error(err.localizedMessage ?: "Note polish failed")
                }
        }
    }

    // Voice Studio controls
    fun setVoicePreset(preset: VoicePreset) {
        voiceEngine.setPreset(preset)
        viewModelScope.launch { settingsRepo.setVoicePreset(preset) }
    }

    fun toggleLiveMonitor() {
        if (voiceEngine.isMonitoring.value) {
            voiceEngine.stopSession()
        } else {
            voiceEngine.startSession(liveMonitor = true, recordClip = false)
        }
    }

    fun toggleRecordFxClip() {
        if (voiceEngine.isRecording.value) {
            voiceEngine.stopSession()
        } else {
            voiceEngine.startSession(liveMonitor = false, recordClip = true)
        }
    }

    fun togglePlayClip() {
        if (voiceEngine.isPlayingClip.value) {
            voiceEngine.stopPlayback()
        } else {
            voiceEngine.playRecordedClip()
        }
    }

    fun clearVoiceError() {
        voiceEngine.clearError()
    }

    // Settings controls
    fun setHapticDialKeys(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setHapticDialKeys(enabled) }
    }

    fun setDtmfTones(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDtmfTones(enabled) }
    }

    fun setFlashStrobe(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setFlashStrobe(enabled) }
    }

    fun setAutoScreenSpam(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoScreenSpam(enabled) }
    }

    fun setSpeedDial(slot: Int, number: String) {
        viewModelScope.launch {
            settingsRepo.setSpeedDial(slot, number)
            _toastMessage.value = "Speed dial #$slot updated"
        }
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    private fun playDtmfForKey(ch: Char?) {
        val tone = when (ch) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        try {
            toneGenerator?.startTone(tone, 90)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        voiceEngine.stopSession()
        voiceEngine.stopPlayback()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
        super.onCleared()
    }

    companion object {
        fun provideFactory(currentUserId: String?) = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                PrismCallViewModel(
                    notesRepo = FirestoreNotesRepository(db = db, screeningCache = ScreeningCache(app)),
                    localNotesRepo = LocalNotesRepository(app),
                    contactsRepo = DeviceContactsRepository(app),
                    settingsRepo = SettingsRepository(app),
                    geminiRepo = GeminiDialerRepository(),
                    voiceEngine = VoiceChangerEngine(app),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
