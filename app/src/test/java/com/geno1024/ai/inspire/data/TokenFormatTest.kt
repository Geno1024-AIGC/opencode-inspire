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
    fun `chinese format below ten thousand stays plain`() {
        assertEquals("9999", TokenFormat.CN_1.format(9999))
        assertEquals("0", TokenFormat.CN_1.format(0))
    }

    @Test
    fun `chinese format scales through wan yi and wanyi`() {
        assertEquals("3.5 万", TokenFormat.CN_1.format(35_000))
        assertEquals("1.7 亿", TokenFormat.CN_1.format(170_000_000))
        assertEquals("1.5 万亿", TokenFormat.CN_1.format(1_500_000_000_000L))
    }

    @Test
    fun `chinese digits control precision`() {
        assertEquals("1 万", TokenFormat.CN_0.format(12_340))
        assertEquals("1.2 万", TokenFormat.CN_1.format(12_340))
        assertEquals("1.23 万", TokenFormat.CN_2.format(12_340))
        assertEquals("1.234 万", TokenFormat.CN_3.format(12_340))
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
        assertEquals(TokenFormat.CN_2, TokenFormat.fromId("cn_2"))
    }
}