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

    private fun t9DigitOrNull(ch: Char): Char? = when (ch) {
        'a', 'b', 'c', 'A', 'B', 'C' -> '2'
        'd', 'e', 'f', 'D', 'E', 'F' -> '3'
        'g', 'h', 'i', 'G', 'H', 'I' -> '4'
        'j', 'k', 'l', 'J', 'K', 'L' -> '5'
        'm', 'n', 'o', 'M', 'N', 'O' -> '6'
        'p', 'q', 'r', 's', 'P', 'Q', 'R', 'S' -> '7'
        't', 'u', 'v', 'T', 'U', 'V' -> '8'
        'w', 'x', 'y', 'z', 'W', 'X', 'Y', 'Z' -> '9'
        else -> null
    }

    fun toT9Digits(text: String): String {
        val sb = StringBuilder(text.length)
        for (i in 0 until text.length) {
            val ch = text[i]
            if (ch in '0'..'9') {
                sb.append(ch)
            } else {
                t9DigitOrNull(ch)?.let { sb.append(it) }
            }
        }
        return sb.toString()
    }

    fun matchesDialQuery(query: String, name: String, number: String): Boolean {
        if (query.isBlank()) return false
        val qDigits = buildString(query.length) {
            for (i in 0 until query.length) {
                val c = query[i]
                if (c in '0'..'9') append(c)
            }
        }
        if (qDigits.isNotEmpty()) {
            val numDigits = buildString(number.length) {
                for (i in 0 until number.length) {
                    val c = number[i]
                    if (c in '0'..'9') append(c)
                }
            }
            if (numDigits.contains(qDigits)) return true
            if (toT9Digits(name).contains(qDigits)) return true
        }
        return name.contains(query, ignoreCase = true)
    }
}
