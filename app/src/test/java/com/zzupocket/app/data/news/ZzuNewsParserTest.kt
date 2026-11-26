package com.zzupocket.app.data.news

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 郑州大学官网 / 新闻网 HTML 解析器单测 */
class ZzuNewsParserTest {

    @Test
    fun `主站通知公告解析相对链接与日期并升级HTTPS`() {
        val html = """
            <ul>
              <li class="notice-item active">
                <a class="notice-link" href="../info/1442/89408.htm">
                  <div class="notice-link-text">关于 2026 年秋季学期选课退补选的通知</div>
                  <div class="notice-link-time">2026-11-13</div>
                </a>
              </li>
            </ul>
            <div class="pb_sys_common">
              <span class="p_pages"><span class="p_next p_fun"><a href="tzgg/69.htm">下页</a></span></span>
            </div>
        """.trimIndent()

        val page = ZzuNewsParser.parseListPage(html, NewsChannel.NOTICE)
        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals("https://www.zzu.edu.cn/info/1442/89408.htm", item.url)
        assertEquals("关于 2026 年秋季学期选课退补选的通知", item.title)
        assertEquals("2026-11-13", item.publishDate)
        assertEquals(NewsChannel.NOTICE, item.channel)
        assertEquals("https://www.zzu.edu.cn/index/tzgg/69.htm", page.nextPageUrl)
    }

    @Test
    fun `新闻网真实new-item模板解析外部new-date日期与分页链接`() {
        val html = """
            <div class="new-item n" id="line_u11_0">
              <div class="row">
                <div class="col-sm-6 col-xs-9">
                  <div class="new-center">
                    <h3>
                      <a href="http://www.zzu.edu.cn/info/1217/89480.htm" title="河南数智甲骨学研究中心成立暨座谈会在郑州大学举行">河南数智甲骨学研究中心成立...</a>
                    </h3>
                    <p>
                      <a href="http://www.zzu.edu.cn/info/1217/89480.htm">11月20日至22日，河南数智甲骨学研究中心成立暨座谈会在郑州大学举行。</a>
                    </p>
                  </div>
                  <div class="new-date">2026-11-25</div>
                </div>
              </div>
            </div>
            <div class="pb_sys_common">
              <span class="p_pages"><span class="p_next p_fun"><a href="ywsd/1182.htm">下页</a></span></span>
            </div>
        """.trimIndent()

        val page = ZzuNewsParser.parseListPage(html, NewsChannel.HEADLINE)
        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals("https://www.zzu.edu.cn/info/1217/89480.htm", item.url)
        assertEquals("河南数智甲骨学研究中心成立暨座谈会在郑州大学举行", item.title)
        assertEquals("2026-11-25", item.publishDate)
        assertTrue(item.summarySnippet.contains("11月20日至22日"))
        assertEquals("https://news.zzu.edu.cn/ywsd/1182.htm", page.nextPageUrl)
    }

    @Test
    fun `新闻网精简new-center卡片兼容解析且HTTP链接转HTTPS`() {
        val html = """
            <div class="new-center">
              <h3>
                <a href="http://www.zzu.edu.cn/info/1022/55432.htm" title="郑州大学召开本科教学工作会议（完整标题）">郑州大学召开本科教学...</a>
              </h3>
              <p>2026-11-18 我校在主校区综合管理中心召开会议，部署本学期课程评估与考试安排。</p>
            </div>
        """.trimIndent()

        val items = ZzuNewsParser.parseList(html, NewsChannel.HEADLINE)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("https://www.zzu.edu.cn/info/1022/55432.htm", item.url)
        assertEquals("郑州大学召开本科教学工作会议（完整标题）", item.title)
        assertEquals("2026-11-18", item.publishDate)
        assertTrue(item.summarySnippet.contains("课程评估"))
    }

    @Test
    fun `详情页解析中文日期、信息来源、表格正文及外部下载附件`() {
        val html = """
            <html>
              <body>
                <h3 class="notice-detail-title">关于组织参加第十一届高校廉洁教育系列活动的通知</h3>
                <div class="notice-detail-info">
                  <span class="notice-detail-info-text">发布时间：2026年9月29日</span>
                  <span class="notice-detail-info-text">信息来源：党委宣传部  校纪委</span>
                  <span class="notice-detail-info-text">
                    <i class="iconfont">&#xe666;</i>
                    <script>_showDynClicks("wbnews", 1653154221, 89073)</script>
                  </span>
                </div>
                <div id="vsb_content_4">
                  <div class="v_news_content">
                    <p>一、活动时间：10 月 1 日至 10 月 20 日。</p>
                    <table>
                      <tr><td>项目类别</td><td>报送方式</td></tr>
                      <tr><td>知识问答</td><td>线上提交</td></tr>
                    </table>
                  </div>
                </div>
                <ul>
                  <li>附件【<a href="/system/_content/download.jsp?urltype=news.DownloadAttachUrl&owner=165&wbfileid=101">附件1：知识问答参与步骤.pdf</a>】</li>
                  <li>附件【<a href="/system/_content/download.jsp?urltype=news.DownloadAttachUrl&owner=165&wbfileid=101">重复链接应去重</a>】</li>
                  <li>附件【<a href="../files/报名表.docx">附件2：报名表.docx</a>】</li>
                </ul>
              </body>
            </html>
        """.trimIndent()

        val detail = ZzuNewsParser.parseDetail(
            html = html,
            fallbackTitle = "兜底标题",
            pageUrl = "https://www.zzu.edu.cn/info/1442/89073.htm",
        )
        assertEquals("关于组织参加第十一届高校廉洁教育系列活动的通知", detail.title)
        assertEquals("2026-09-29", detail.publishDate)
        assertEquals("党委宣传部 校纪委", detail.sourceName)
        assertTrue(detail.contentText.contains("一、活动时间：10 月 1 日至 10 月 20 日。"))
        assertTrue(detail.contentText.contains("项目类别 报送方式"))
        assertEquals(2, detail.attachments.size)
        assertEquals(
            "https://www.zzu.edu.cn/system/_content/download.jsp?urltype=news.DownloadAttachUrl&owner=165&wbfileid=101",
            detail.attachments[0].url,
        )
        assertEquals("附件1：知识问答参与步骤.pdf", detail.attachments[0].name)
        assertEquals("https://www.zzu.edu.cn/info/files/报名表.docx", detail.attachments[1].url)
    }

    @Test
    fun `字节解码支持UTF-8与GB2312声明嗅探`() {
        val gbkBytes = """<meta charset="gb2312"><title>郑州大学教务公告</title>"""
            .toByteArray(Charset.forName("GB18030"))
        val decodedGbk = ZzuNewsParser.decodeHtmlBytes(gbkBytes, contentTypeHeader = "text/html")
        assertTrue(decodedGbk.contains("郑州大学教务公告"))

        val utf8Bytes = """<title>郑大要闻速递</title>""".toByteArray(Charsets.UTF_8)
        val decodedUtf8 = ZzuNewsParser.decodeHtmlBytes(utf8Bytes, "text/html; charset=utf-8")
        assertTrue(decodedUtf8.contains("郑大要闻速递"))
    }

    @Test
    fun `日期归一化与域名HTTPS升级排除明文白名单`() {
        assertEquals("2026-03-05", ZzuNewsParser.extractIsoDate("发布于 2026/3/5"))
        assertEquals("2026-11-09", ZzuNewsParser.extractIsoDate("2026年11月9日"))
        assertEquals(
            "https://news.zzu.edu.cn/info/100/200.htm",
            ZzuNewsParser.normalizeUrl("http://news.zzu.edu.cn/info/100/200.htm", "https://news.zzu.edu.cn/"),
        )
        assertEquals(
            "http://order.lib.zzu.edu.cn/seat",
            ZzuNewsParser.normalizeUrl("http://order.lib.zzu.edu.cn/seat", "https://www.zzu.edu.cn/"),
        )
        assertNull(ZzuNewsParser.normalizeUrl("javascript:void(0)", "https://www.zzu.edu.cn/"))
    }
}
