package com.geno1024.ai.inspire.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class TokenFormat(val id: String, val example: String) {
    RAW("raw", "1234567"),
    COMPACT_0("compact_0", "1M"),
    COMPACT_1("compact_1", "1.2M"),
    COMPACT_2("compact_2", "1.23M"),
    COMPACT_3("compact_3", "1.234M"),
    GROUP_3("group_3", "1,234,567"),
    GROUP_4("group_4", "123,4567");

    companion object {
        val DEFAULT: TokenFormat = COMPACT_1

        val ALL: List<TokenFormat> = values().toList()

        fun fromId(id: String): TokenFormat = values().firstOrNull { it.id == id } ?: DEFAULT

        val SI: List<Pair<String, Long>> = listOf(
            "E" to 1_000_000_000_000_000_000L,
            "P" to 1_000_000_000_000_000L,
            "T" to 1_000_000_000_000L,
            "G" to 1_000_000_000L,
            "M" to 1_000_000L,
            "k" to 1_000L,
        )
    }

    fun format(count: Long): String = when (this) {
        RAW -> count.toString()
        COMPACT_0 -> compact(count, 0)
        COMPACT_1 -> compact(count, 1)
        COMPACT_2 -> compact(count, 2)
        COMPACT_3 -> compact(count, 3)
        GROUP_3 -> grouped(count, 3)
        GROUP_4 -> grouped(count, 4)
    }
}

private fun compact(count: Long, digits: Int): String {
    if (count < 1_000L) return count.toString()
    for ((suffix, divisor) in TokenFormat.SI) {
        if (count >= divisor) {
            return "%.${digits}f%s".format(Locale.ROOT, count.toDouble() / divisor, suffix)
        }
    }
    return count.toString()
}

private fun grouped(count: Long, size: Int): String {
    val df = DecimalFormat("#,##0")
    df.isGroupingUsed = true
    df.groupingSize = size
    df.decimalFormatSymbols = DecimalFormatSymbols(Locale.ROOT)
    return df.format(count)
}