package com.example

import com.example.model.CallerCategory
import com.example.model.PhoneUtils
import com.example.model.VoicePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrismCallUtilsTest {
    @Test
    fun normalizeNumber_extractsLastTenDigits() {
        assertEquals("9840012345", PhoneUtils.normalizeNumber("+91 98400-12345"))
        assertEquals("5550199", PhoneUtils.normalizeNumber("555-0199"))
    }

    @Test
    fun t9Matching_matchesContactNameAndDigits() {
        assertTrue(PhoneUtils.matchesDialQuery("275", "Arjun", "+91 98400 12345"))
        assertTrue(PhoneUtils.matchesDialQuery("9840", "Arjun", "+91 98400 12345"))
        assertFalse(PhoneUtils.matchesDialQuery("999", "Arjun", "+91 98400 12345"))
    }

    @Test
    fun callerCategory_fromRawFallsBackSafely() {
        assertEquals(CallerCategory.DELIVERY, CallerCategory.fromRaw("Delivery"))
        assertEquals(CallerCategory.OTHER, CallerCategory.fromRaw("UNKNOWN_ENUM"))
        assertEquals(CallerCategory.OTHER, CallerCategory.fromRaw(null))
    }

    @Test
    fun voicePresets_coverAllRequestedModes() {
        val names = VoicePreset.entries.map { it.displayName }
        assertTrue(names.containsAll(listOf("Original", "Male", "Female", "Old", "Child", "Baby")))
    }
}
