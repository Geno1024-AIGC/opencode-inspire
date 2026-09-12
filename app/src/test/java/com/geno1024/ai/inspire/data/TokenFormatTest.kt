package com.geno1024.ai.inspire.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenFormatTest {

    @Test
    fun `raw format is plain count`() {
        assertEquals("1234567", TokenFormat.RAW.format(1_234_567))
        assertEquals("0", TokenFormat.RAW.format(0))
        assertEquals("-12", TokenFormat.RAW.format(-12))
    }

    @Test
    fun `compact below one thousand stays plain`() {
        assertEquals("999", TokenFormat.COMPACT_1.format(999))
        assertEquals("0", TokenFormat.COMPACT_1.format(0))
    }

    @Test
    fun `compact one digit rounds to suffix`() {
        assertEquals("1M", TokenFormat.COMPACT_0.format(1_234_567))
        assertEquals("1G", TokenFormat.COMPACT_0.format(1_000_000_000))
    }

    @Test
    fun `compact digits control precision`() {
        assertEquals("1.2M", TokenFormat.COMPACT_1.format(1_234_567))
        assertEquals("1.23M", TokenFormat.COMPACT_2.format(1_234_567))
        assertEquals("1.5k", TokenFormat.COMPACT_1.format(1500))
    }

    @Test
    fun `grouped formats with grouping separators`() {
        assertEquals("1,234,567", TokenFormat.GROUP_3.format(1_234_567))
        assertEquals("123,4567", TokenFormat.GROUP_4.format(1_234_567))
    }

    @Test
    fun `fromId falls back to default for unknown ids`() {
        assertEquals(TokenFormat.COMPACT_1, TokenFormat.fromId("not-a-format"))
        assertEquals(TokenFormat.RAW, TokenFormat.fromId("raw"))
    }
}