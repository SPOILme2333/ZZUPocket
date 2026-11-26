package com.zzupocket.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 倒数日计算与排序规则单测 */
class CountdownModelsTest {

    @Test
    fun `天数差与文案与过期判定正确`() {
        val today = LocalDate.of(2026, 6, 10)
        val future = CountdownItem(title = "CET-6", targetDate = LocalDate.of(2026, 6, 13))
        val onDay = CountdownItem(title = "今天交卷", targetDate = today)
        val past = CountdownItem(title = "高数期中", targetDate = LocalDate.of(2026, 6, 8))

        assertEquals(3, future.daysUntil(today))
        assertEquals("还有 3 天", future.countdownText(today))
        assertFalse(future.isExpired(today))

        assertEquals(0, onDay.daysUntil(today))
        assertEquals("就在今天", onDay.countdownText(today))
        assertFalse(onDay.isExpired(today))

        assertEquals(-2, past.daysUntil(today))
        assertEquals("已过去 2 天", past.countdownText(today))
        assertTrue(past.isExpired(today))
    }

    @Test
    fun `排序遵循未过期优先于已过期且置顶排在前面`() {
        val today = LocalDate.of(2026, 6, 10)
        val expiredYesterday = CountdownItem(id = 1, title = "已考完", targetDate = LocalDate.of(2026, 6, 9), pinned = true)
        val near = CountdownItem(id = 2, title = "近期考试", targetDate = LocalDate.of(2026, 6, 12), pinned = false)
        val pinnedFar = CountdownItem(id = 3, title = "置顶考研", targetDate = LocalDate.of(2026, 12, 20), pinned = true)
        val far = CountdownItem(id = 4, title = "期末总复习", targetDate = LocalDate.of(2026, 7, 1), pinned = false)

        val sorted = CountdownRules.sortForDisplay(
            listOf(expiredYesterday, far, near, pinnedFar),
            today = today,
        )
        assertEquals(listOf(3L, 2L, 4L, 1L), sorted.map { it.id })
    }
}
