package com.zzupocket.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** 课程名称智能简称映射单测 */
class CourseShortenerTest {

    @Test
    fun `关闭开关时原样保留去除首尾空格`() {
        assertEquals("习近平新时代中国特色社会主义思想概论", CourseShortener.shorten("  习近平新时代中国特色社会主义思想概论 ", false))
        assertEquals("高等数学（一）", CourseShortener.shorten("高等数学（一）", false))
    }

    @Test
    fun `开启开关后常见超长公共课精简且保留后缀`() {
        assertEquals("习概", CourseShortener.shorten("习近平新时代中国特色社会主义思想概论", true))
        assertEquals("毛概", CourseShortener.shorten("毛泽东思想和中国特色社会主义理论体系概论", true))
        assertEquals("近代史", CourseShortener.shorten("中国近现代史纲要", true))
        assertEquals("马原", CourseShortener.shorten("马克思主义基本原理概论", true))
        assertEquals("高数(一)", CourseShortener.shorten("高等数学（一）", true))
        assertEquals("大物Ⅱ", CourseShortener.shorten("大学物理Ⅱ", true))
        assertEquals("大物实验", CourseShortener.shorten("大学物理实验", true))
        assertEquals("线代B", CourseShortener.shorten("线性代数B", true))
    }

    @Test
    fun `普通专业短课程名不受影响`() {
        assertEquals("编译原理", CourseShortener.shorten("编译原理", true))
        assertEquals("操作系统", CourseShortener.shorten("操作系统", true))
    }
}
