package com.screensaver.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `常规 24 小时制时间可解析`() {
        assertEquals(0, parseMinute("00:00"))
        assertEquals(845, parseMinute("14:05"))
        assertEquals(1439, parseMinute("23:59"))
    }

    @Test
    fun `24点只允许作为一天结束时间`() {
        assertNull(parseMinute("24:00"))
        assertEquals(1440, parseMinute("24:00", allowEndOfDay = true))
        assertNull(parseMinute("24:01", allowEndOfDay = true))
    }

    @Test
    fun `非法格式返回 null`() {
        assertNull(parseMinute("9:xx"))
        assertNull(parseMinute("12"))
        assertNull(parseMinute("23:60"))
    }
}
