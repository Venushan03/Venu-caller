package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.ScreeningCache
import com.example.model.CallerCategory
import com.example.model.CallerTag
import com.example.model.NumberNote
import com.example.model.PhoneUtils
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

@Entity(tableName = "local_number_notes")
data class LocalNumberNoteEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val contactName: String?,
    val title: String,
    val body: String,
    val tagsJoined: String,
    val pinned: Boolean,
    val createdAtSeconds: Long,
    val updatedAtSeconds: Long
)

@Entity(tableName = "local_caller_tags")
data class LocalCallerTagEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val displayName: String,
    val category: String,
    val blocked: Boolean,
    val aiSummary: String?,
    val createdAtSeconds: Long,
    val updatedAtSeconds: Long
)

@Dao
interface PrismLocalDao {
    @Query("SELECT * FROM local_number_notes ORDER BY pinned DESC, updatedAtSeconds DESC")
    fun observeNotes(): Flow<List<LocalNumberNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: LocalNumberNoteEntity)

    @Query("SELECT * FROM local_number_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): LocalNumberNoteEntity?

    @Query("UPDATE local_number_notes SET pinned = :pinned, updatedAtSeconds = :updatedAt WHERE id = :id")
    suspend fun updatePin(id: String, pinned: Boolean, updatedAt: Long)

    @Query("DELETE FROM local_number_notes WHERE id = :id")
    suspend fun deleteNote(id: String)

    @Query("SELECT * FROM local_caller_tags ORDER BY updatedAtSeconds DESC")
    fun observeCallerTags(): Flow<List<LocalCallerTagEntity>>

    @Query("SELECT * FROM local_caller_tags WHERE id = :id LIMIT 1")
    suspend fun getTagById(id: String): LocalCallerTagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: LocalCallerTagEntity)

    @Query("DELETE FROM local_caller_tags WHERE id = :id")
    suspend fun deleteTag(id: String)
}

@Database(
    entities = [LocalNumberNoteEntity::class, LocalCallerTagEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PrismLocalDatabase : RoomDatabase() {
    abstract fun prismLocalDao(): PrismLocalDao

    companion object {
        @Volatile
        private var INSTANCE: PrismLocalDatabase? = null

        fun getInstance(context: Context): PrismLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrismLocalDatabase::class.java,
                    "prismcall_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class LocalNotesRepository(context: Context) {
    private val dao = PrismLocalDatabase.getInstance(context).prismLocalDao()
    private val screeningCache = ScreeningCache(context.applicationContext)

    fun observeNumberNotes(): Flow<List<NumberNote>> =
        dao.observeNotes().map { list ->
            list.map { entity ->
                NumberNote(
                    id = entity.id,
                    userId = "local",
                    phoneNumber = entity.phoneNumber,
                    normalizedNumber = entity.normalizedNumber,
                    contactName = entity.contactName,
                    title = entity.title,
                    body = entity.body,
                    tags = entity.tagsJoined.split("|").filter { it.isNotBlank() },
                    pinned = entity.pinned,
                    createdAt = Timestamp(entity.createdAtSeconds, 0),
                    updatedAt = Timestamp(entity.updatedAtSeconds, 0)
                )
            }
        }

    fun observeCallerTags(): Flow<List<CallerTag>> =
        dao.observeCallerTags().map { list ->
            val mapped = list.map { entity ->
                CallerTag(
                    id = entity.id,
                    userId = "local",
                    phoneNumber = entity.phoneNumber,
                    normalizedNumber = entity.normalizedNumber,
                    displayName = entity.displayName,
                    category = entity.category,
                    blocked = entity.blocked,
                    aiSummary = entity.aiSummary,
                    createdAt = Timestamp(entity.createdAtSeconds, 0),
                    updatedAt = Timestamp(entity.updatedAtSeconds, 0)
                )
            }
            screeningCache.syncTags(mapped)
            mapped
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
        val cleanPhone = phoneNumber.trim().take(40)
        val cleanTitle = title.trim().take(100)
        if (cleanPhone.isEmpty() || cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone number and title are required."))
        }
        return try {
            val nowSec = System.currentTimeMillis() / 1000L
            val existing = existingId?.takeIf { it.isNotBlank() }?.let { dao.getNoteById(it) }
            val entity = LocalNumberNoteEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                phoneNumber = cleanPhone,
                normalizedNumber = PhoneUtils.normalizeNumber(cleanPhone).ifBlank { "unknown" }.take(32),
                contactName = contactName?.trim()?.takeIf { it.isNotBlank() }?.take(80),
                title = cleanTitle,
                body = body.trim().take(1500),
                tagsJoined = tags.map { it.trim().take(30) }.filter { it.isNotBlank() }.take(10).joinToString("|"),
                pinned = pinned,
                createdAtSeconds = existing?.createdAtSeconds ?: nowSec,
                updatedAtSeconds = nowSec
            )
            dao.upsertNote(entity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun togglePinNote(noteId: String, pinned: Boolean): Result<Unit> = try {
        dao.updatePin(noteId, pinned, System.currentTimeMillis() / 1000L)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteNumberNote(noteId: String): Result<Unit> = try {
        dao.deleteNote(noteId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun upsertCallerTag(
        phoneNumber: String,
        displayName: String,
        category: CallerCategory,
        blocked: Boolean,
        aiSummary: String? = null
    ): Result<Unit> {
        val cleanPhone = phoneNumber.trim().take(40)
        val cleanName = displayName.trim().take(80)
        if (cleanPhone.isEmpty() || cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone number and caller name are required."))
        }
        return try {
            val nowSec = System.currentTimeMillis() / 1000L
            val normalized = PhoneUtils.normalizeNumber(cleanPhone).ifBlank { "unknown" }.take(32)
            val docId = PhoneUtils.sanitizeTagDocId(normalized)
            val existing = dao.getTagById(docId)
            dao.upsertTag(
                LocalCallerTagEntity(
                    id = docId,
                    phoneNumber = cleanPhone,
                    normalizedNumber = normalized,
                    displayName = cleanName,
                    category = category.label,
                    blocked = blocked,
                    aiSummary = aiSummary?.trim()?.takeIf { it.isNotBlank() }?.take(500),
                    createdAtSeconds = existing?.createdAtSeconds ?: nowSec,
                    updatedAtSeconds = nowSec
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCallerTag(tagId: String): Result<Unit> = try {
        dao.deleteTag(tagId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
