package com.dealmate.app.domain.util

/** Vietnamese đồng formatting: 315000 -> "315.000đ". Money is always integer VND. */
object Money {
    fun format(amount: Long): String = group(amount) + "đ"

    /** Vietnamese digit grouping without a unit: 35200 -> "35.200" (counts such as "sold"). */
    fun group(value: Long): String {
        val negative = value < 0
        val digits = (if (negative) -value else value).toString()
        val sb = StringBuilder()
        for ((i, c) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return (if (negative) "-" else "") + sb
    }

    /** Parses user input such as "250.000", "250,000đ" or "250000". Null when not a positive amount. */
    fun parse(input: String): Long? {
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 11) return null
        return digits.toLongOrNull()?.takeIf { it > 0 }
    }
}
