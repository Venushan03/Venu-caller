package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.CallerCategory
import com.example.model.CallerTag
import com.example.model.NumberNote
import com.example.model.PhoneUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreNotesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val screeningCache: ScreeningCache? = null
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        screeningCache = ScreeningCache(context.applicationContext)
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to access cloud notes.")

    fun observeNumberNotes(userId: String): Flow<List<NumberNote>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/numberNotes"
        emitAll(
            db.collection("users").document(userId).collection("numberNotes")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        NumberNote::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<NumberNote> { it.pinned }
                            .thenByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCallerTags(userId: String): Flow<List<CallerTag>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/callerTags"
        emitAll(
            db.collection("users").document(userId).collection("callerTags")
                .snapshots()
                .map { snapshot ->
                    val tags = snapshot.toObjects(
                        CallerTag::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                    screeningCache?.syncTags(tags)
                    tags
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveNumberNote(
        existingId: String? = null,
        phoneNumber: String,
        contactName: String?,
        title: String,
        body: String,
        tags: List<String>,
        pinned: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanPhone = phoneNumber.trim().take(40)
        val normalized = PhoneUtils.normalizeNumber(cleanPhone).ifBlank { "unknown" }.take(32)
        val cleanTitle = title.trim().take(100)
        val cleanBody = body.trim().take(1500)
        val cleanContactName = contactName?.trim()?.takeIf { it.isNotBlank() }?.take(80)
        val cleanTags = tags.map { it.trim().take(30) }.filter { it.isNotBlank() }.take(10)

        if (cleanPhone.isEmpty() || cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone number and title are required."))
        }

        val collectionRef = db.collection("users").document(uid).collection("numberNotes")
        val isCreate = existingId.isNullOrBlank()
        val docRef = if (isCreate) collectionRef.document() else collectionRef.document(existingId!!)
        val op = if (isCreate) OperationType.CREATE else OperationType.UPDATE

        return try {
            requireUserId()
            if (isCreate) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "phoneNumber" to cleanPhone,
                    "normalizedNumber" to normalized,
                    "title" to cleanTitle,
                    "body" to cleanBody,
                    "tags" to cleanTags,
                    "pinned" to pinned,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanContactName != null) {
                    payload["contactName"] = cleanContactName
                }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "phoneNumber" to cleanPhone,
                    "normalizedNumber" to normalized,
                    "title" to cleanTitle,
                    "body" to cleanBody,
                    "tags" to cleanTags,
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanContactName != null) {
                    updates["contactName"] = cleanContactName
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, op, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun togglePinNote(noteId: String, pinned: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (noteId.isBlank()) return Result.failure(IllegalArgumentException("Blank note ID"))
        val docRef = db.collection("users").document(uid).collection("numberNotes").document(noteId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNumberNote(noteId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (noteId.isBlank()) return Result.failure(IllegalArgumentException("Blank note ID"))
        val docRef = db.collection("users").document(uid).collection("numberNotes").document(noteId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertCallerTag(
        phoneNumber: String,
        displayName: String,
        category: CallerCategory,
        blocked: Boolean,
        aiSummary: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanPhone = phoneNumber.trim().take(40)
        val normalized = PhoneUtils.normalizeNumber(cleanPhone).ifBlank { "unknown" }.take(32)
        val cleanName = displayName.trim().take(80)
        val cleanAi = aiSummary?.trim()?.takeIf { it.isNotBlank() }?.take(500)

        if (cleanPhone.isEmpty() || cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone number and caller name are required."))
        }

        val docId = PhoneUtils.sanitizeTagDocId(normalized)
        val docRef = db.collection("users").document(uid).collection("callerTags").document(docId)

        return try {
            requireUserId()
            val exists = docRef.get().await().exists()
            if (!exists) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "phoneNumber" to cleanPhone,
                    "normalizedNumber" to normalized,
                    "displayName" to cleanName,
                    "category" to category.label,
                    "blocked" to blocked,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanAi != null) {
                    payload["aiSummary"] = cleanAi
                }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "phoneNumber" to cleanPhone,
                    "normalizedNumber" to normalized,
                    "displayName" to cleanName,
                    "category" to category.label,
                    "blocked" to blocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanAi != null) {
                    updates["aiSummary"] = cleanAi
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCallerTag(tagId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (tagId.isBlank()) return Result.failure(IllegalArgumentException("Blank tag ID"))
        val docRef = db.collection("users").document(uid).collection("callerTags").document(tagId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}

class ScreeningCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "prismcall_screening_cache",
        Context.MODE_PRIVATE
    )

    fun syncTags(tags: List<CallerTag>) {
        val editor = prefs.edit().clear()
        for (tag in tags) {
            if (tag.normalizedNumber.isNotBlank()) {
                editor.putString("name_${tag.normalizedNumber}", tag.displayName)
                editor.putString("cat_${tag.normalizedNumber}", tag.category)
                editor.putBoolean("block_${tag.normalizedNumber}", tag.blocked)
            }
        }
        editor.apply()
    }

    fun isBlocked(number: String): Boolean {
        val norm = PhoneUtils.normalizeNumber(number)
        if (norm.isBlank()) return false
        return prefs.getBoolean("block_$norm", false)
    }

    fun getCachedName(number: String): String? {
        val norm = PhoneUtils.normalizeNumber(number)
        if (norm.isBlank()) return null
        return prefs.getString("name_$norm", null)
    }

    fun getCachedCategory(number: String): String? {
        val norm = PhoneUtils.normalizeNumber(number)
        if (norm.isBlank()) return null
        return prefs.getString("cat_$norm", null)
    }
}
