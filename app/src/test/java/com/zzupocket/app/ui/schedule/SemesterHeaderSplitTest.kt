package com.zzupocket.app.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemesterHeaderSplitTest {

    @Test
    fun `splits standard year and term display name`() {
        assertEquals("2026-2027" to "第一学期", splitSemesterHeader("2026-2027 第一学期"))
        assertEquals("2025-2026" to "第二学期", splitSemesterHeader("2025-2026 第二学期"))
    }

    @Test
    fun `splits raw semester code form`() {
        assertEquals("2026-2027" to "第一学期", splitSemesterHeader("2026-2027-1"))
        assertEquals("2025-2026" to "第二学期", splitSemesterHeader("2025-2026-2"))
        assertEquals("2025-2026" to "第三学期", splitSemesterHeader("2025-2026-3"))
    }

    @Test
    fun `falls back gracefully for plain labels`() {
        val (year, term) = splitSemesterHeader("选择学期")
        assertNull(year)
        assertEquals("选择学期", term)
    }
}
