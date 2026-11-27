package com.example.ai

import android.util.Log
import com.example.model.CallerCategory
import com.example.model.CallerLookupResult
import com.example.model.DialerIntentType
import com.example.model.GeminiDialerAction
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiDialerRepo"
private const val FAST_MODEL_NAME = "gemini-3.5-flash-lite"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val TIMEOUT_MS = 60_000L

class GeminiDialerRepository {

    private val lookupSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "suggestedLabel" to Schema.string(
                    description = "Concise caller display label based on prefix/pattern (never invent private individual names)"
                ),
                "numberType" to Schema.string(
                    description = "Type such as Mobile, Toll-Free, Business Line, VoIP, Short Code, or International"
                ),
                "regionHint" to Schema.string(
                    description = "Country/region/area hint derived from dialing code or prefix"
                ),
                "category" to Schema.enumeration(
                    values = listOf("VIP", "Work", "Delivery", "Business", "Friend", "Spam", "Other"),
                    description = "Best matching caller category"
                ),
                "spamRisk" to Schema.enumeration(
                    values = listOf("Low", "Moderate", "High", "Unknown"),
                    description = "Estimated spam risk based on STIR/SHAKEN and number pattern"
                ),
                "summary" to Schema.string(
                    description = "2-sentence helpful explanation of the number prefix, carrier/region clues, and verification status"
                ),
                "suggestedGreeting" to Schema.string(
                    description = "Short polite screening greeting to say or send"
                )
            )
        )
    }

    private val commandSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "intentType" to Schema.enumeration(
                    values = listOf(
                        "CALL_CONTACT",
                        "OPEN_NOTE",
                        "CREATE_NOTE",
                        "LOOKUP_NUMBER",
                        "COMPOSE_REPLY",
                        "VOICE_STUDIO",
                        "ANSWER_INFO"
                    ),
                    description = "The primary dialer action requested by the user"
                ),
                "targetNumber" to Schema.string(
                    description = "Matched or extracted phone number if any",
                    nullable = true
                ),
                "targetName" to Schema.string(
                    description = "Matched contact or caller name if any",
                    nullable = true
                ),
                "noteDraft" to Schema.string(
                    description = "Drafted note text or SMS reply text if applicable",
                    nullable = true
                ),
                "voicePresetName" to Schema.string(
                    description = "Voice preset if user asked to open voice changer (ORIGINAL, MALE, FEMALE, OLD, CHILD, BABY)",
                    nullable = true
                ),
                "assistantMessage" to Schema.string(
                    description = "Friendly concise response explaining what PrismCall will do or answering the query"
                )
            ),
            optionalProperties = listOf("targetNumber", "targetName", "noteDraft", "voicePresetName")
        )
    }

    private val quickRepliesSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "replies" to Schema.array(
                    items = Schema.string(),
                    description = "3 short, polite SMS reply templates"
                )
            )
        )
    }

    private val lookupModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = lookupSchema
                temperature = 0.2f
                maxOutputTokens = 512
            }
        )
    }

    private val commandModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = commandSchema
                temperature = 0.2f
                maxOutputTokens = 512
            }
        )
    }

    private val quickRepliesModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quickRepliesSchema
                temperature = 0.4f
                maxOutputTokens = 256
            }
        )
    }

    private val textModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                maxOutputTokens = 256
            }
        )
    }

    suspend fun analyzeUnknownNumber(
        phoneNumber: String,
        verificationStatus: String = "Unverified"
    ): Result<CallerLookupResult> = try {
        val prompt = """
            You are PrismCall's Caller Intelligence engine. Analyze this unknown phone number: "$phoneNumber".
            Network STIR/SHAKEN verification status: "$verificationStatus".
            Identify the country/region code, likely number type (e.g., Toll-Free, Mobile, Landline, Short Code, International), and any known public/pattern clues.
            IMPORTANT: Never fabricate a private individual's personal full name if it cannot be verified from the number pattern; instead give an honest descriptive label like "India Mobile (+91)" or "US Toll-Free Line" or "Potential Robocall Pattern".
        """.trimIndent()

        val raw = withContext(Dispatchers.IO) {
            withTimeout(TIMEOUT_MS) {
                lookupModel.generateContent(prompt).text
            }
        } ?: throw IllegalStateException("Empty response from Gemini")
        val json = JSONObject(raw)
        Result.success(
            CallerLookupResult(
                phoneNumber = phoneNumber,
                suggestedLabel = json.optString("suggestedLabel", "Unknown Caller"),
                numberType = json.optString("numberType", "Unknown"),
                regionHint = json.optString("regionHint", "Region undetected"),
                category = CallerCategory.fromRaw(json.optString("category", "Other")),
                spamRisk = json.optString("spamRisk", "Unknown"),
                summary = json.optString("summary", "No directory details available."),
                suggestedGreeting = json.optString("suggestedGreeting", "Hello, who is calling please?")
            )
        )
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Unknown number lookup timed out after ${TIMEOUT_MS}ms", e)
        Result.failure(Exception("Caller lookup timed out. Check connection and try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Unknown number lookup failed", e)
        Result.failure(e)
    }

    suspend fun interpretDialerCommand(
        query: String,
        contactsSummary: String,
        notesSummary: String
    ): Result<GeminiDialerAction> = try {
        val prompt = """
            You are PrismCall's smart dialer assistant. Interpret the user's request and map it to one action.
            User request: "$query"

            Available device contacts (subset):
            ${contactsSummary.ifBlank { "None" }}

            Saved number notes (subset):
            ${notesSummary.ifBlank { "None" }}

            Rules:
            - If the user asks to call someone or a number, return CALL_CONTACT with targetNumber and targetName.
            - If the user asks to find or open a note, return OPEN_NOTE with targetNumber/targetName.
            - If the user asks to save/write a note for a number, return CREATE_NOTE with targetNumber and noteDraft.
            - If the user asks to identify or check an unknown number, return LOOKUP_NUMBER with targetNumber.
            - If the user asks to draft an SMS/reply, return COMPOSE_REPLY with targetNumber, targetName, and noteDraft.
            - If the user mentions voice changer (male, female, old, child, baby), return VOICE_STUDIO with voicePresetName.
            - Otherwise return ANSWER_INFO with a helpful assistantMessage.
        """.trimIndent()

        val raw = withContext(Dispatchers.IO) {
            withTimeout(TIMEOUT_MS) {
                commandModel.generateContent(prompt).text
            }
        } ?: throw IllegalStateException("Empty response from Gemini")
        val json = JSONObject(raw)
        val rawIntent = json.optString("intentType", "ANSWER_INFO")
        val intent = DialerIntentType.entries.firstOrNull { it.name == rawIntent }
            ?: DialerIntentType.ANSWER_INFO

        Result.success(
            GeminiDialerAction(
                intentType = intent,
                targetNumber = json.optString("targetNumber", "").takeIf { it.isNotBlank() && it != "null" },
                targetName = json.optString("targetName", "").takeIf { it.isNotBlank() && it != "null" },
                noteDraft = json.optString("noteDraft", "").takeIf { it.isNotBlank() && it != "null" },
                voicePresetName = json.optString("voicePresetName", "").takeIf { it.isNotBlank() && it != "null" },
                assistantMessage = json.optString("assistantMessage", "Ready.")
            )
        )
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Dialer command timed out after ${TIMEOUT_MS}ms", e)
        Result.failure(Exception("Gemini assistant timed out. Check connection and try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Dialer command failed", e)
        Result.failure(e)
    }

    suspend fun summarizeAndPolishNote(rawNote: String, callerLabel: String): Result<String> = try {
        val prompt = """
            Polish and organize this quick phone call note for "$callerLabel" into a clean, concise note with key details and any action items (under 80 words):
            "$rawNote"
        """.trimIndent()
        val text = withContext(Dispatchers.IO) {
            withTimeout(TIMEOUT_MS) {
                val sb = StringBuilder()
                textModel.generateContentStream(prompt).collect { chunk ->
                    sb.append(chunk.text.orEmpty())
                }
                sb.toString().trim()
            }
        }
        if (text.isBlank()) {
            Result.failure(IllegalStateException("Gemini returned an empty summary."))
        } else {
            Result.success(text)
        }
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Note polish timed out after ${TIMEOUT_MS}ms", e)
        Result.failure(Exception("Note polish timed out. Check connection and try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Note polish failed", e)
        Result.failure(e)
    }

    suspend fun generateQuickReplies(callerLabel: String, noteContext: String): Result<List<String>> = try {
        val prompt = """
            Generate 3 short, natural SMS quick-replies (each under 12 words) when declining or screening an incoming call from "$callerLabel".
            Context from saved notes: "${noteContext.ifBlank { "None" }}"
        """.trimIndent()
        val raw = withContext(Dispatchers.IO) {
            withTimeout(TIMEOUT_MS) {
                quickRepliesModel.generateContent(prompt).text
            }
        } ?: throw IllegalStateException("Empty response from Gemini")
        val json = JSONObject(raw)
        val arr = json.optJSONArray("replies")
        val list = mutableListOf<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotBlank()) list.add(item)
            }
        }
        if (list.isEmpty()) {
            list.addAll(
                listOf(
                    "In a meeting right now, will call you back shortly.",
                    "Can't pick up right now — please text me the details.",
                    "Driving now, I'll reach out in 15 minutes."
                )
            )
        }
        Result.success(list)
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Quick replies timed out after ${TIMEOUT_MS}ms", e)
        Result.failure(Exception("Quick reply generation timed out. Check connection and try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Quick replies failed", e)
        Result.failure(e)
    }
}
