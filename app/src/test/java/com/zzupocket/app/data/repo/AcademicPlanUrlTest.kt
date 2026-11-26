package com.zzupocket.app.data.repo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 培养方案官方入口发现 JSON 解析单测 */
class AcademicPlanUrlTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `嵌套数组与相对路径服务自动补全jwxt域名`() {
        val raw = """
            {
              "result": 0,
              "data": [
                {
                  "groupName": "常用服务",
                  "services": [
                    { "nameZh": "个人课表", "serviceUrl": "/eams-student-course-table-app/" },
                    { "nameZh": "培养方案查看", "serviceUrl": "/eams-student-program-app/" }
                  ]
                }
              ]
            }
        """.trimIndent()
        val found = AcademicRepository.extractPlanServiceUrl(json.parseToJsonElement(raw))
        assertEquals("https://jwxt.zzu.edu.cn/eams-student-program-app/", found)
    }

    @Test
    fun `绝对URL按关键词命中原样返回`() {
        val raw = """
            {
              "data": [
                { "title": "我的成绩", "url": "https://jwxt.zzu.edu.cn/grade" },
                { "title": "学业进度核查", "url": "https://jwxt.zzu.edu.cn/audit/index.html" }
              ]
            }
        """.trimIndent()
        val found = AcademicRepository.extractPlanServiceUrl(json.parseToJsonElement(raw))
        assertEquals("https://jwxt.zzu.edu.cn/audit/index.html", found)
    }

    @Test
    fun `无匹配培养方案入口时返回null`() {
        val raw = """{"data":[{"nameZh":"空教室查询","serviceUrl":"/room"}]}"""
        assertNull(AcademicRepository.extractPlanServiceUrl(json.parseToJsonElement(raw)))
    }
}
