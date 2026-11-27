package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CallerCategory(val label: String) {
    VIP("VIP"),
    WORK("Work"),
    DELIVERY("Delivery"),
    BUSINESS("Business"),
    FRIEND("Friend"),
    SPAM("Spam"),
    OTHER("Other");

    companion object {
        fun fromRaw(raw: String?): CallerCategory =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: OTHER
    }
}

@Keep
@IgnoreExtraProperties
data class NumberNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val phoneNumber: String = "",
    val normalizedNumber: String = "",
    val contactName: String? = null,
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallerTag(
    @DocumentId val id: String = "",
    val userId: String = "",
    val phoneNumber: String = "",
    val normalizedNumber: String = "",
    val displayName: String = "",
    val category: String = CallerCategory.OTHER.label,
    val blocked: Boolean = false,
    val aiSummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): CallerCategory = CallerCategory.fromRaw(category)
}

data class DeviceContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val photoUri: String? = null,
    val starred: Boolean = false
)

enum class CallDirection {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED
}

data class CallLogEntry(
    val id: String,
    val number: String,
    val normalizedNumber: String,
    val cachedName: String?,
    val direction: CallDirection,
    val timestampMillis: Long,
    val durationSeconds: Long
)

enum class VoicePreset(
    val displayName: String,
    val subtitle: String,
    val pitchFactor: Float,
    val tremoloDepth: Float = 0f,
    val warmthFilter: Float = 0f
) {
    ORIGINAL("Original", "Natural studio voice", 1.0f),
    MALE("Male", "Deep resonant baritone", 0.76f, warmthFilter = 0.25f),
    FEMALE("Female", "Bright crisp soprano", 1.28f),
    OLD("Old", "Warm vintage elder", 0.84f, tremoloDepth = 0.18f, warmthFilter = 0.45f),
    CHILD("Child", "Playful young timbre", 1.46f),
    BABY("Baby", "High bubbly pitch", 1.76f)
}

data class CallerLookupResult(
    val phoneNumber: String,
    val suggestedLabel: String,
    val numberType: String,
    val regionHint: String,
    val category: CallerCategory,
    val spamRisk: String,
    val summary: String,
    val suggestedGreeting: String
)

enum class DialerIntentType {
    CALL_CONTACT,
    OPEN_NOTE,
    CREATE_NOTE,
    LOOKUP_NUMBER,
    COMPOSE_REPLY,
    VOICE_STUDIO,
    ANSWER_INFO
}

data class GeminiDialerAction(
    val intentType: DialerIntentType,
    val targetNumber: String? = null,
    val targetName: String? = null,
    val noteDraft: String? = null,
    val voicePresetName: String? = null,
    val assistantMessage: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
