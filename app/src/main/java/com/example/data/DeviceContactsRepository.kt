package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.CallDirection
import com.example.model.CallLogEntry
import com.example.model.DeviceContact
import com.example.model.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DeviceContactsRepo"

class DeviceContactsRepository(private val context: Context) {

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasReadCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadContacts(limit: Int = 2000): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!hasReadContactsPermission()) return@withContext emptyList()
        val contactsMap = LinkedHashMap<String, DeviceContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val starIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                while (cursor.moveToNext() && contactsMap.size < limit) {
                    val contactId = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim() ?: "" else ""
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx)?.trim() ?: "" else ""
                    val photoUri = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    val starred = if (starIdx >= 0) cursor.getInt(starIdx) == 1 else false

                    if (rawNumber.isNotBlank()) {
                        val normalized = PhoneUtils.normalizeNumber(rawNumber)
                        val dedupeKey = "${contactId}_${normalized}"
                        if (!contactsMap.containsKey(dedupeKey)) {
                            contactsMap[dedupeKey] = DeviceContact(
                                id = dedupeKey,
                                name = name.ifBlank { rawNumber },
                                phoneNumber = rawNumber,
                                normalizedNumber = normalized,
                                photoUri = photoUri,
                                starred = starred
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query contacts", e)
        }
        contactsMap.values.toList()
    }

    @SuppressLint("MissingPermission")
    suspend fun loadCallLogs(limit: Int = 80): List<CallLogEntry> = withContext(Dispatchers.IO) {
        if (!hasReadCallLogPermission()) return@withContext emptyList()
        val entries = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "$count" else "$count"
                    val number = if (numIdx >= 0) cursor.getString(numIdx)?.trim() ?: "" else ""
                    val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim() else null
                    val rawType = if (typeIdx >= 0) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L

                    val direction = when (rawType) {
                        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallDirection.REJECTED
                        else -> CallDirection.INCOMING
                    }

                    if (number.isNotBlank()) {
                        entries.add(
                            CallLogEntry(
                                id = id,
                                number = number,
                                normalizedNumber = PhoneUtils.normalizeNumber(number),
                                cachedName = cachedName?.takeIf { it.isNotBlank() },
                                direction = direction,
                                timestampMillis = date,
                                durationSeconds = duration
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query call log", e)
        }
        entries
    }

    fun lookupContactName(phoneNumber: String): String? {
        if (!hasReadContactsPermission() || phoneNumber.isBlank()) return null
        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "PhoneLookup failed for number", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun placeCall(phoneNumber: String): Boolean {
        val clean = phoneNumber.trim()
        if (clean.isBlank()) return false
        val uri = Uri.fromParts("tel", clean, null)
        return try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val isDefaultDialer = telecom?.defaultDialerPackage == context.packageName
            if (isDefaultDialer && hasCallPhonePermission()) {
                telecom.placeCall(uri, android.os.Bundle())
                true
            } else if (hasCallPhonePermission()) {
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place call", e)
            false
        }
    }

    fun openAddContactIntent(phoneNumber: String, suggestedName: String? = null) {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            if (!suggestedName.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.NAME, suggestedName)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open Contacts insert activity", e)
        }
    }
}
