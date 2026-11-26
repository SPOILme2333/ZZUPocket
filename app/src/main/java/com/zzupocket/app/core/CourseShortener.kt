package com.zzupocket.app.core

/**
 * 课程名称智能简称映射（Issue #14 延伸）。
 *
 * 将高校常见的超长公共课、思政课与基础课名称精简为短小易读的称呼，
 * 同时保留末尾分级/罗马数字后缀（如 `(一)`、`Ⅱ`、`A`），避免不同学期课程混淆。
 */
object CourseShortener {

    private data class Rule(val pattern: Regex, val shortName: String)

    private val SUFFIX_REGEX = Regex(
        "([\\(（](?:[一二三四五六七八九十0-9]+|[A-Za-z]|[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+)[\\)）]|[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+|[A-Za-z0-9]+)$",
    )

    private val RULES = listOf(
        Rule(Regex("习近平新时代中国特色社会主义思想概论"), "习概"),
        Rule(Regex("新时代中国特色社会主义理论与实践"), "新时代中特"),
        Rule(Regex("中国特色社会主义理论与实践研究"), "中特"),
        Rule(Regex("毛泽东思想和中国特色社会主义理论体系概论"), "毛概"),
        Rule(Regex("中国近现代史纲要"), "近代史"),
        Rule(Regex("思想道德与法治|思想道德修养与法律基础"), "思修"),
        Rule(Regex("马克思主义基本原理(?:概论)?"), "马原"),
        Rule(Regex("自然辩证法概论"), "自然辩证法"),
        Rule(Regex("形势与政策"), "形策"),
        Rule(Regex("大学生心理健康教育|大学生心理健康"), "心理健康"),
        Rule(Regex("大学生职业发展与就业指导|职业发展与就业指导"), "职规就业"),
        Rule(Regex("创新创业基础|创新与创业教育"), "双创"),
        Rule(Regex("大学物理实验"), "大物实验"),
        Rule(Regex("大学物理"), "大物"),
        Rule(Regex("高等数学"), "高数"),
        Rule(Regex("线性代数"), "线代"),
        Rule(Regex("概率论与数理统计"), "概率论"),
        Rule(Regex("复变函数与积分变换"), "复变积分"),
        Rule(Regex("大学计算机基础|大学计算机"), "大学计算机"),
        Rule(Regex("大学英语听说"), "大英听说"),
        Rule(Regex("大学英语读写"), "大英读写"),
        Rule(Regex("大学英语"), "大英"),
        Rule(Regex("大学体育"), "体育"),
        Rule(Regex("军事理论"), "军理"),
        Rule(Regex("程序设计基础"), "程设基础"),
        Rule(Regex("面向对象程序设计"), "面向对象"),
        Rule(Regex("数据结构与算法"), "数据结构"),
        Rule(Regex("数据库系统原理|数据库原理及应用"), "数据库"),
        Rule(Regex("计算机组成原理"), "计组"),
        Rule(Regex("计算机网络"), "计网"),
        Rule(Regex("软件工程导论"), "软工导论"),
    )

    /**
     * 按配置返回适合显示的课程名。
     *
     * @param rawName 原始课程名称
     * @param enabled 是否开启智能简称；关闭时原样返回
     */
    fun shorten(rawName: String, enabled: Boolean): String {
        val trimmed = rawName.trim()
        if (!enabled || trimmed.isEmpty()) return trimmed

        // 提取并保留末尾分期标记（例如“高等数学（一）”→“高数(一)”）
        val suffixMatch = SUFFIX_REGEX.find(trimmed)
        val suffix = suffixMatch?.value.orEmpty()
        val body = if (suffix.isNotEmpty()) trimmed.removeSuffix(suffix).trim() else trimmed

        for (rule in RULES) {
            if (rule.pattern.containsMatchIn(body)) {
                val cleanSuffix = suffix
                    .replace('（', '(')
                    .replace('）', ')')
                return rule.shortName + cleanSuffix
            }
        }
        return trimmed
    }
}
