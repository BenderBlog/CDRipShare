package io.github.benderblog.cdripshare.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorHexTest {
    @Test
    fun `parse rgb hex accepts optional hash and returns opaque argb`() {
        assertEquals(0xFF505A88.toInt(), ColorHex.parseRgbToArgb("#505A88"))
        assertEquals(0xFF505A88.toInt(), ColorHex.parseRgbToArgb("505A88"))
    }

    @Test
    fun `parse rgb hex normalizes lowercase input`() {
        assertEquals(0xFF0A0B0C.toInt(), ColorHex.parseRgbToArgb("#0a0b0c"))
    }

    @Test
    fun `parse rgb hex falls back for invalid input`() {
        assertEquals(0xFF3C3C3C.toInt(), ColorHex.parseRgbToArgb("not-a-color"))
        assertEquals(0xFF3C3C3C.toInt(), ColorHex.parseRgbToArgb("#12345"))
    }

    @Test
    fun `normalize rgb hex returns uppercase six digit value`() {
        assertEquals("0A0B0C", ColorHex.normalizeRgbHex("#0a0b0c"))
        assertEquals("3C3C3C", ColorHex.normalizeRgbHex("not-a-color"))
    }
}
