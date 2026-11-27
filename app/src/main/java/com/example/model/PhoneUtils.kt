package com.example.model

object PhoneUtils {
    fun normalizeNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun sanitizeTagDocId(normalized: String): String {
        val clean = normalized.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        return if (clean.isBlank()) "num_unknown" else "tag_$clean"
    }

    fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    private val t9Map = mapOf(
        'A' to '2', 'B' to '2', 'C' to '2',
        'D' to '3', 'E' to '3', 'F' to '3',
        'G' to '4', 'H' to '4', 'I' to '4',
        'J' to '5', 'K' to '5', 'L' to '5',
        'M' to '6', 'N' to '6', 'O' to '6',
        'P' to '7', 'Q' to '7', 'R' to '7', 'S' to '7',
        'T' to '8', 'U' to '8', 'V' to '8',
        'W' to '9', 'X' to '9', 'Y' to '9', 'Z' to '9'
    )

    fun toT9Digits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.uppercase()) {
            when {
                ch.isDigit() -> sb.append(ch)
                t9Map.containsKey(ch) -> sb.append(t9Map[ch])
            }
        }
        return sb.toString()
    }

    fun matchesDialQuery(query: String, name: String, number: String): Boolean {
        if (query.isBlank()) return false
        val normalizedQuery = query.filter { it.isDigit() || it == '+' }
        val numberDigits = number.filter { it.isDigit() }
        if (normalizedQuery.isNotBlank() && numberDigits.contains(normalizedQuery.trimStart('+'))) {
            return true
        }
        val t9Name = toT9Digits(name)
        val digitsOnly = query.filter { it.isDigit() }
        if (digitsOnly.isNotEmpty() && t9Name.contains(digitsOnly)) {
            return true
        }
        return name.contains(query, ignoreCase = true)
    }
}
