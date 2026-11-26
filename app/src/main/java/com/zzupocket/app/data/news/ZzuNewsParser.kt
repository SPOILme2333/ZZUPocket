package com.zzupocket.app.data.news

import java.net.URI
import java.nio.charset.Charset
import java.util.Locale
import kotlinx.serialization.Serializable

/** 郑大公开校园资讯栏目 */
enum class NewsChannel(
    val label: String,
    val listUrl: String,
) {
    NOTICE("通知公告", "https://www.zzu.edu.cn/index/tzgg.htm"),
    HEADLINE("要闻速递", "https://news.zzu.edu.cn/ywsd.htm"),
    ACADEMIC("教学科研", "https://news.zzu.edu.cn/jxky.htm"),
    CAMPUS_LIFE("基层动态", "https://news.zzu.edu.cn/jcdt.htm"),
    MEDIA("媒体郑大", "https://news.zzu.edu.cn/mtzd.htm"),
    ;

    companion object {
        fun of(name: String?): NewsChannel = entries.firstOrNull { it.name == name } ?: NOTICE
    }
}

/** 详情页底部附件条目 */
@Serializable
data class NewsAttachment(
    val name: String,
    val url: String,
)

/** 列表解析出的新闻条目 */
data class ParsedNewsItem(
    val url: String,
    val channel: NewsChannel,
    val title: String,
    val publishDate: String,
    val summarySnippet: String,
)

/** 列表页解析结果（含下一页分页链接） */
data class ParsedNewsListPage(
    val items: List<ParsedNewsItem>,
    val nextPageUrl: String?,
)

/** 详情页解析结果 */
data class ParsedNewsDetail(
    val title: String,
    val publishDate: String,
    val sourceName: String = "",
    val contentText: String,
    val attachments: List<NewsAttachment> = emptyList(),
)

/**
 * 郑州大学官网 / 新闻网 HTML 解析器（纯函数，无 Android 依赖）。
 *
 * 覆盖两套已实测的学校官网模板：
 * 1. 主站通知公告（`www.zzu.edu.cn/index/tzgg.htm`）：`.notice-item` + `.notice-link-text` + `.notice-link-time`
 * 2. 新闻网专栏（`news.zzu.edu.cn` 各栏目页）：`.new-item` 整卡（内含 `.new-center` 标题/摘要与 `.new-date` 日期），
 *    并兼容仅含 `.new-center` 卡片的精简结构与侧边列表兜底。
 */
object ZzuNewsParser {

    private val NOTICE_ITEM_REGEX = Regex(
        """<li[^>]*class="[^"]*notice-item[^"]*"[^>]*>([\s\S]*?)</li>""",
        RegexOption.IGNORE_CASE,
    )
    private val LINK_REGEX = Regex(
        """<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
        RegexOption.IGNORE_CASE,
    )
    private val NOTICE_TITLE_REGEX = Regex(
        """<div[^>]*class="[^"]*notice-link-text[^"]*"[^>]*>([\s\S]*?)</div>""",
        RegexOption.IGNORE_CASE,
    )
    private val NOTICE_TIME_REGEX = Regex(
        """<div[^>]*class="[^"]*notice-link-time[^"]*"[^>]*>([\s\S]*?)</div>""",
        RegexOption.IGNORE_CASE,
    )

    private val NEW_ITEM_SPLIT_REGEX = Regex(
        """<div[^>]*class="[^"]*\bnew-item\b[^"]*"[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val NEWS_CARD_REGEX = Regex(
        """<div[^>]*class="[^"]*new-center[^"]*"[^>]*>([\s\S]*?)</div>""",
        RegexOption.IGNORE_CASE,
    )
    private val NEW_DATE_REGEX = Regex(
        """<div[^>]*class="[^"]*new-date[^"]*"[^>]*>([\s\S]*?)</div>""",
        RegexOption.IGNORE_CASE,
    )
    private val H3_LINK_REGEX = Regex(
        """<h3[^>]*>[\s\S]*?<a[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>[\s\S]*?</h3>""",
        RegexOption.IGNORE_CASE,
    )
    private val TITLE_ATTR_REGEX = Regex("""title="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val PARA_TEXT_REGEX = Regex("""<p[^>]*>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)

    private val ISO_DATE_REGEX = Regex("""\b(20\d{2})[-/](\d{1,2})[-/](\d{1,2})\b""")
    private val CN_DATE_REGEX = Regex("""(20\d{2})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""")

    private val GENERIC_LI_REGEX = Regex(
        """<li[^>]*>([\s\S]{10,600}?info/\d+/\d+\.htm[\s\S]{0,300}?)</li>""",
        RegexOption.IGNORE_CASE,
    )

    private val NEXT_PAGE_SPAN_REGEX = Regex(
        """<span[^>]*class="[^"]*p_next[^"]*"[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val NEXT_PAGE_TEXT_REGEX = Regex(
        """<a[^>]*href="([^"]+)"[^>]*>\s*(?:下页|下一页)\s*</a>""",
        RegexOption.IGNORE_CASE,
    )

    private val DETAIL_TITLE_H3 = Regex(
        """<h3[^>]*class="[^"]*notice-detail-title[^"]*"[^>]*>([\s\S]*?)</h3>""",
        RegexOption.IGNORE_CASE,
    )
    private val DETAIL_TITLE_HIDDEN_H1 = Regex(
        """<h1[^>]*align="center"[^>]*>([\s\S]*?)</h1>""",
        RegexOption.IGNORE_CASE,
    )
    private val HTML_TITLE = Regex("""<title>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
    private val DETAIL_INFO_BLOCK = Regex(
        """<div[^>]*class="[^"]*notice-detail-info[^"]*"[^>]*>([\s\S]*?)</div>""",
        RegexOption.IGNORE_CASE,
    )
    private val PUBLISH_TIME_TEXT = Regex("""发布时间[：:]\s*([^\n<]+)""")
    private val SOURCE_NAME_TEXT = Regex("""信息来源[：:]\s*([^\n<]+)""")

    private val CONTENT_CHARSET_REGEX = Regex("""charset\s*=\s*["']?([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val ATTACHMENT_EXT_REGEX = Regex(
        """\.(?:pdf|docx?|xlsx?|pptx?|zip|rar|7z|mp4)(?:$|[?#])""",
        RegexOption.IGNORE_CASE,
    )

    /** 保持向后兼容的列表解析入口 */
    fun parseList(html: String, channel: NewsChannel): List<ParsedNewsItem> =
        parseListPage(html, channel, channel.listUrl).items

    /** 解析栏目列表页 HTML（含下一页分页链接），自动按规范化后的 HTTPS URL 去重。 */
    fun parseListPage(
        html: String,
        channel: NewsChannel,
        pageUrl: String = channel.listUrl,
    ): ParsedNewsListPage {
        val out = LinkedHashMap<String, ParsedNewsItem>()

        if (channel == NewsChannel.NOTICE) {
            for (m in NOTICE_ITEM_REGEX.findAll(html)) {
                val block = m.groupValues[1]
                val linkMatch = LINK_REGEX.find(block) ?: continue
                val url = normalizeUrl(linkMatch.groupValues[1], pageUrl) ?: continue
                val inner = linkMatch.groupValues[2]
                val rawTitle = NOTICE_TITLE_REGEX.find(inner)?.groupValues?.get(1)
                    ?: TITLE_ATTR_REGEX.find(linkMatch.value)?.groupValues?.get(1)
                    ?: inner
                val title = cleanText(rawTitle)
                if (title.isBlank()) continue
                val date = NOTICE_TIME_REGEX.find(inner)?.groupValues?.get(1)?.let(::cleanText)
                    ?.let(::extractIsoDate)
                    ?.takeIf { it.isNotEmpty() }
                    ?: extractIsoDate(cleanText(block))
                out.putIfAbsent(url, ParsedNewsItem(url, channel, title, date, ""))
            }
        } else {
            val itemBlocks = NEW_ITEM_SPLIT_REGEX.split(html)
            if (itemBlocks.size > 1) {
                for (block in itemBlocks.drop(1)) {
                    val h3 = H3_LINK_REGEX.find(block) ?: continue
                    val url = normalizeUrl(h3.groupValues[1], pageUrl) ?: continue
                    val titleAttr = TITLE_ATTR_REGEX.find(h3.value)?.groupValues?.get(1)
                    val title = cleanText(titleAttr ?: h3.groupValues[2])
                    if (title.isBlank()) continue
                    val cardHtml = NEWS_CARD_REGEX.find(block)?.groupValues?.get(1) ?: block
                    val snippet = PARA_TEXT_REGEX.find(cardHtml)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
                    val date = NEW_DATE_REGEX.find(block)?.groupValues?.get(1)?.let(::cleanText)
                        ?.let(::extractIsoDate)
                        ?.takeIf { it.isNotEmpty() }
                        ?: extractIsoDate(cleanText(block))
                    out.putIfAbsent(url, ParsedNewsItem(url, channel, title, date, snippet))
                }
            } else {
                for (m in NEWS_CARD_REGEX.findAll(html)) {
                    val block = m.groupValues[1]
                    val h3 = H3_LINK_REGEX.find(block) ?: continue
                    val url = normalizeUrl(h3.groupValues[1], pageUrl) ?: continue
                    val titleAttr = TITLE_ATTR_REGEX.find(h3.value)?.groupValues?.get(1)
                    val title = cleanText(titleAttr ?: h3.groupValues[2])
                    if (title.isBlank()) continue
                    val snippet = PARA_TEXT_REGEX.find(block)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
                    val date = extractIsoDate(cleanText(block))
                    out.putIfAbsent(url, ParsedNewsItem(url, channel, title, date, snippet))
                }
            }
        }

        // 若页面改版或遇到侧边栏补充列表，兜底抓取标准 info/xxxx/xxxx.htm 条目
        if (out.isEmpty()) {
            for (m in GENERIC_LI_REGEX.findAll(html)) {
                val block = m.groupValues[1]
                val linkMatch = LINK_REGEX.find(block) ?: continue
                val href = linkMatch.groupValues[1]
                if (!href.contains("info/")) continue
                val url = normalizeUrl(href, pageUrl) ?: continue
                val titleAttr = TITLE_ATTR_REGEX.find(linkMatch.value)?.groupValues?.get(1)
                val title = cleanText(titleAttr ?: linkMatch.groupValues[2])
                if (title.length < 4) continue
                val date = extractIsoDate(cleanText(block))
                out.putIfAbsent(url, ParsedNewsItem(url, channel, title, date, ""))
            }
        }

        return ParsedNewsListPage(
            items = out.values.toList(),
            nextPageUrl = extractNextPageUrl(html, pageUrl),
        )
    }

    /** 解析博达站群分页中的「下页」链接，排除自循环链接。 */
    internal fun extractNextPageUrl(html: String, pageUrl: String): String? {
        val rawHref = NEXT_PAGE_SPAN_REGEX.find(html)?.groupValues?.get(1)
            ?: NEXT_PAGE_TEXT_REGEX.find(html)?.groupValues?.get(1)
            ?: return null
        val normalized = normalizeUrl(rawHref, pageUrl) ?: return null
        return normalized.takeIf { it != normalizeUrl(pageUrl, pageUrl) && it != pageUrl }
    }

    /** 解析详情页标题、发布日期、信息来源、正文与附件清单。 */
    fun parseDetail(
        html: String,
        fallbackTitle: String = "",
        pageUrl: String = "https://www.zzu.edu.cn/",
    ): ParsedNewsDetail {
        val rawTitle = DETAIL_TITLE_H3.find(html)?.groupValues?.get(1)
            ?: DETAIL_TITLE_HIDDEN_H1.find(html)?.groupValues?.get(1)
            ?: HTML_TITLE.find(html)?.groupValues?.get(1)?.substringBefore("-郑州大学")
            ?: fallbackTitle
        val title = cleanText(rawTitle).ifBlank { fallbackTitle }

        val rawInfoHtml = DETAIL_INFO_BLOCK.find(html)?.groupValues?.get(1).orEmpty()
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<i\b[^>]*>[\s\S]*?</i>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</(?:span|div|p|li)>""", RegexOption.IGNORE_CASE), "\n")
        val infoText = rawInfoHtml.lines()
            .joinToString("\n") { cleanText(it) }
        val publishDate = PUBLISH_TIME_TEXT.find(infoText)?.groupValues?.get(1)?.let(::extractIsoDate)
            ?.takeIf { it.isNotEmpty() }
            ?: PUBLISH_TIME_TEXT.find(html.take(12000))?.groupValues?.get(1)?.let(::extractIsoDate)
                ?.takeIf { it.isNotEmpty() }
            ?: extractIsoDate(infoText).takeIf { it.isNotEmpty() }
            ?: extractIsoDate(html.take(12000))

        val rawSource = SOURCE_NAME_TEXT.find(infoText)?.groupValues?.get(1)
            ?: SOURCE_NAME_TEXT.find(cleanText(html.take(12000)))?.groupValues?.get(1)
            ?: ""
        val sourceName = cleanText(rawSource)
            .replace(Regex("""[\uE000-\uF8FF]"""), "")
            .substringBefore("_showDynClicks")
            .substringBefore("浏览")
            .substringBefore("点击")
            .trim()
            .take(40)

        val contentHtml = extractContentContainer(html) ?: ""
        val contentText = htmlToReadableText(contentHtml)
        val attachments = extractAttachments(html, pageUrl)

        return ParsedNewsDetail(
            title = title,
            publishDate = publishDate,
            sourceName = sourceName,
            contentText = contentText,
            attachments = attachments,
        )
    }

    /** 从全文抽取官方附件下载链接（实测位于 `vsb_content` 闭合之后），按规范化 URL 去重。 */
    internal fun extractAttachments(html: String, pageUrl: String): List<NewsAttachment> {
        val out = LinkedHashMap<String, NewsAttachment>()
        for (m in LINK_REGEX.findAll(html)) {
            val href = m.groupValues[1].trim()
            val isAttach = href.contains("download.jsp", ignoreCase = true) ||
                href.contains("DownloadAttachUrl", ignoreCase = true) ||
                ATTACHMENT_EXT_REGEX.containsMatchIn(href)
            if (!isAttach) continue
            val url = normalizeUrl(href, pageUrl) ?: continue
            val rawName = cleanText(m.groupValues[2])
                .ifBlank { TITLE_ATTR_REGEX.find(m.value)?.groupValues?.get(1)?.let(::cleanText).orEmpty() }
                .ifBlank { url.substringAfterLast('/').substringBefore('?') }
                .ifBlank { "附件下载" }
            out.putIfAbsent(url, NewsAttachment(name = rawName, url = url))
        }
        return out.values.toList()
    }

    /**
     * 提取详情正文容器：优先匹配 `id="vsb_content..."` 或 `class="v_news_content"`，
     * 统计嵌套 `<div>` 深度确保完整闭合。
     */
    internal fun extractContentContainer(html: String): String? {
        val marker = Regex(
            """<div[^>]*(?:id="vsb_content[^"]*"|class="[^"]*v_news_content[^"]*")[^>]*>""",
            RegexOption.IGNORE_CASE,
        ).find(html) ?: return null

        val start = marker.range.last + 1
        var depth = 1
        var cursor = start
        val tagRegex = Regex("""<(/?)div\b[^>]*>""", RegexOption.IGNORE_CASE)
        for (m in tagRegex.findAll(html, start)) {
            if (m.groupValues[1] == "/") {
                depth--
                if (depth == 0) {
                    return html.substring(start, m.range.first)
                }
            } else {
                depth++
            }
            cursor = m.range.last + 1
        }
        return html.substring(start, cursor.coerceAtLeast(start))
    }

    /** 将正文 HTML 转换为段落与表格行分明的纯文本。 */
    internal fun htmlToReadableText(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""
        var text = rawHtml
            .replace(Regex("""<!--[\s\S]*?-->"""), "")
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</t[dh]>""", RegexOption.IGNORE_CASE), "  ")
            .replace(Regex("""</tr>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</(?:p|div|li|h[1-6])>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""<[^>]+>"""), "")
        text = unescapeEntities(text)
            .replace("\u200B", "")
            .replace("\uFEFF", "")
        return text.lines()
            .map { it.trim().replace(Regex("[ \\t\\u00A0]+"), " ") }
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /** 统一提取 `yyyy-MM-dd`、`yyyy/M/d` 或 `yyyy年M月d日` 为补零的 `yyyy-MM-dd`。 */
    internal fun extractIsoDate(text: String): String {
        val iso = ISO_DATE_REGEX.find(text)
        if (iso != null) {
            return formatIsoDate(iso.groupValues[1], iso.groupValues[2], iso.groupValues[3])
        }
        val cn = CN_DATE_REGEX.find(text)
        if (cn != null) {
            return formatIsoDate(cn.groupValues[1], cn.groupValues[2], cn.groupValues[3])
        }
        return ""
    }

    private fun formatIsoDate(y: String, m: String, d: String): String {
        val year = y.toIntOrNull() ?: return ""
        val month = m.toIntOrNull()?.takeIf { it in 1..12 } ?: return ""
        val day = d.toIntOrNull()?.takeIf { it in 1..31 } ?: return ""
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    /** 按 HTTP Content-Type 或 HTML meta 声明解码字节流，兼容 UTF-8 与 GB18030/GBK/GB2312。 */
    internal fun decodeHtmlBytes(bytes: ByteArray, contentTypeHeader: String? = null): String {
        if (bytes.isEmpty()) return ""
        val fromHeader = contentTypeHeader?.let { CONTENT_CHARSET_REGEX.find(it)?.groupValues?.get(1) }
        val headAscii = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val fromMeta = CONTENT_CHARSET_REGEX.find(headAscii)?.groupValues?.get(1)
        val charsetName = (fromHeader ?: fromMeta)?.trim()?.lowercase(Locale.US)
        val charset = when (charsetName) {
            "gbk", "gb2312", "gb18030" -> runCatching { Charset.forName("GB18030") }.getOrDefault(Charsets.UTF_8)
            null, "" -> Charsets.UTF_8
            else -> runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
        }
        return String(bytes, charset)
    }

    /** 将相对 URL 补全为绝对 URL，并将非明文白名单的学校 HTTP 域名统一升级为 HTTPS。 */
    internal fun normalizeUrl(href: String, baseUrl: String): String? {
        val raw = href.trim()
        if (raw.isEmpty() || raw.startsWith("javascript:", ignoreCase = true) || raw.startsWith("#")) {
            return null
        }
        val resolved = runCatching { URI(baseUrl).resolve(raw).toString() }.getOrNull() ?: return null
        val uri = runCatching { URI(resolved) }.getOrNull() ?: return resolved
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val isCleartextAllowed = host == "order.lib.zzu.edu.cn" || host == "gmsstu.zzu.edu.cn"
        return if (uri.scheme.equals("http", ignoreCase = true) &&
            (host == "zzu.edu.cn" || host.endsWith(".zzu.edu.cn")) &&
            !isCleartextAllowed
        ) {
            "https://" + resolved.removePrefix("http://").removePrefix("HTTP://")
        } else {
            resolved
        }
    }

    internal fun cleanText(htmlOrText: String): String =
        unescapeEntities(htmlOrText.replace(Regex("""<[^>]+>"""), ""))
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun unescapeEntities(input: String): String = input
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace("&lsquo;", "‘")
        .replace("&rsquo;", "’")
        .replace("&mdash;", "—")
        .replace("&hellip;", "…")
        .replace(Regex("""&#(\d+);""")) { m ->
            m.groupValues[1].toIntOrNull()?.let { code -> Char(code).toString() } ?: m.value
        }
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { code -> Char(code).toString() } ?: m.value
        }
}
