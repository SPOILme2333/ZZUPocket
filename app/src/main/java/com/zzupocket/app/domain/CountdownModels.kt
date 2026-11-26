package com.zzupocket.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 倒数日分类 */
enum class CountdownCategory(val label: String) {
    EXAM("考试"),
    EVENT("日程"),
    HOLIDAY("假期"),
    ;

    companion object {
        fun of(raw: String?): CountdownCategory =
            entries.firstOrNull { it.name == raw } ?: EVENT
    }
}

/** 倒数日领域模型 */
data class CountdownItem(
    val id: Long = 0L,
    val title: String,
    val targetDate: LocalDate,
    val category: CountdownCategory = CountdownCategory.EVENT,
    val location: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
) {
    /** 距离 [today] 的天数：正数表示未来，0 表示今天，负数表示已过期 */
    fun daysUntil(today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, targetDate).toInt()

    /** 人类可读的倒数文本 */
    fun countdownText(today: LocalDate): String {
        val days = daysUntil(today)
        return when {
            days > 0 -> "还有 $days 天"
            days == 0 -> "就在今天"
            else -> "已过去 ${-days} 天"
        }
    }

    fun isExpired(today: LocalDate): Boolean = daysUntil(today) < 0
}

/** 倒数日排序与分组纯函数 */
object CountdownRules {

    /**
     * 展示排序规则：
     * 1. 未过期优先于已过期；
     * 2. 未过期中，置顶项优先；
     * 3. 均按距离今天更近优先（已过期则最近刚过期的靠前）。
     */
    fun sortForDisplay(items: List<CountdownItem>, today: LocalDate): List<CountdownItem> =
        items.sortedWith(
            compareBy<CountdownItem> { it.isExpired(today) }
                .thenByDescending { !it.isExpired(today) && it.pinned }
                .thenBy { kotlin.math.abs(it.daysUntil(today)) }
                .thenBy { it.createdAt },
        )
}
