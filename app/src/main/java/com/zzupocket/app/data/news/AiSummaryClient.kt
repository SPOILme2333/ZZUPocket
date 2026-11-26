package com.zzupocket.app.data.news

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 可选 BYOK（自带密钥）AI 校园资讯摘要客户端，兼容 OpenAI `/v1/chat/completions` 协议。
 *
 * 默认关闭；仅当用户主动开启并填入自己的 Base URL / API Key / Model 后才发请求。
 * 直接使用 `buildJsonObject` 与 `Json.parseToJsonElement`，不依赖反射序列化以兼容 R8 混淆。
 */
class AiSummaryClient(
    private val client: OkHttpClient,
    private val json: Json,
) {

    data class Config(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
    )

    /** 提炼单篇文章核心要点 */
    suspend fun summarizeArticle(
        config: Config,
        title: String,
        content: String,
    ): String = chat(
        config = config,
        systemPrompt = "你是郑州大学校园资讯助手。请用简洁客观的中文，提取这篇文章最关键的 3 条要点（含截止时间、对象范围、办理地点或入口），每条不超过 30 字。",
        userPrompt = "标题：$title\n\n正文：\n${content.take(4500)}",
    )

    /** 为最近几条校园资讯生成「今日校园速览」 */
    suspend fun summarizeDigest(
        config: Config,
        items: List<Pair<String, String>>,
    ): String {
        val bulletText = items.take(6).mapIndexed { index, (title, snippet) ->
            "${index + 1}. $title" + if (snippet.isNotBlank()) "（${snippet.take(90)}）" else ""
        }.joinToString("\n")
        return chat(
            config = config,
            systemPrompt = "你是郑州大学校园资讯助手。请根据以下最新校园通知与新闻标题，生成一段不超过 180 字的「今日速览」，突出与在校生直接相关的通知与讲座。",
            userPrompt = bulletText,
        )
    }

    private suspend fun chat(
        config: Config,
        systemPrompt: String,
        userPrompt: String,
    ): String = withContext(Dispatchers.IO) {
        val endpoint = normalizeEndpoint(config.baseUrl)
        require(config.apiKey.isNotBlank()) { "请先填写 AI API Key" }
        require(config.model.isNotBlank()) { "请先填写模型名称" }

        val payload = buildJsonObject {
            put("model", config.model.trim())
            put("temperature", 0.3)
            put("max_tokens", 500)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", userPrompt)
                        },
                    )
                },
            )
        }.toString()

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("无法连接 AI 接口：${e.message}", e)
        }

        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("AI 接口返回 HTTP ${resp.code}：${extractError(raw)}")
            }
            extractContent(raw)
        }
    }

    internal fun extractContent(raw: String): String {
        val root = json.parseToJsonElement(raw).jsonObject
        val choices = root["choices"]?.jsonArray ?: JsonArray(emptyList())
        val first = choices.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("AI 响应缺少 choices")
        val content = first["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw IllegalStateException("AI 响应缺少 message.content")
        return content.trim()
    }

    private fun extractError(raw: String): String = runCatching {
        val obj = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching raw.take(120)
        val msg = obj["error"]?.jsonObject?.get("message")?.let {
            (it as? JsonPrimitive)?.content
        }
        msg ?: raw.take(120)
    }.getOrDefault(raw.take(120))

    internal fun normalizeEndpoint(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/')
        require(clean.startsWith("https://") || clean.startsWith("http://")) {
            "Base URL 需以 https:// 开头"
        }
        return when {
            clean.endsWith("/chat/completions") -> clean
            clean.endsWith("/v1") -> "$clean/chat/completions"
            else -> "$clean/v1/chat/completions"
        }
    }
}
