package com.zzupocket.app.ui.nav

/**
 * 导航路由的**唯一来源**。
 *
 * 桌面小组件、课前通知、系统设置跳转均统一从这里引用路由常量，避免多处字面量写歪。
 */
object AppRoutes {

    /** 底部四栏 Dock Tab */
    const val TODAY = "today"
    const val SCHEDULE = "schedule"
    const val CAMPUS = "campus"
    const val RADAR = "radar"

    /** 二级功能页（全屏栈内页，自动隐藏底部 Dock） */
    const val FOCUS = "focus"
    const val SETTINGS = "settings"
    const val ARTICLE_DETAIL = "article_detail"
    const val ELECTRICITY = "electricity"
    const val CARD = "campus_card"
    const val GRADES = "academic_grades"
    const val EXAMS = "academic_exams"
    const val STUDENT_STATUS = "student_status"
    const val TRAINING_PLAN = "training_plan"

    /**
     * 小组件或通知带过来的路由 extra。
     *
     * MainActivity 读到后立即清掉，防止旋转屏幕/深浅色切换重复跳转。
     */
    const val EXTRA_ROUTE = "com.zzupocket.app.extra.ROUTE"
}
