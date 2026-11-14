package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrV3Inbox
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.core.sync.V3InboxClient
import ir.vil3ntec.tohid.core.sync.V3InboxQueueStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.V3InboxHttp
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.shell.V3InboxModel
import ir.vil3ntec.tohid.ui.v3.shell.V3InboxPage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 *  SYN-07 / S28 — صفحهٔ «تعارض‌های همگام‌سازی» ِ کامپیوتر (`V3InboxPage`، همان پوستهٔ گوشیِ نسخهٔ ۳) روی سرورِ ساختگیِ
 *  127.0.0.1 با همان شکلِ `shapeConflict` ِ `server/src/lib/sync-v3.js` و همان `ApiClient` ِ برنامه:
 *   • پرچمِ `shop.sync_v3_inbox` خاموش (پاسخ بی `inbox`) ⇒ هیچ ردیفی، و تا ده دقیقه دوباره پرسیده نمی‌شود؛
 *   • روشن ⇒ نامِ کالا از دفتر، دو سو با مقدار و دستگاه، «مالِ من / مالِ دستگاهِ دیگر»، و «ادغام، فیلد به فیلد» ⇒
 *     یک POST با `{"action":"merge","fields":["salePriceMinor"]}` و ردیف می‌رود؛
 *   • بی‌اینترنت ⇒ «در صف» روی همان ردیف و پس از وصل یک بار فرستاده می‌شود (تکرار نه).
 *  نیمهٔ سرور (ادغام، تکرارِ بی‌اثر، ۴۰۳) در `server/test/sync-v3-inbox.test.js`؛ منطق در `core` ‏`V3InboxTest`/`V3InboxOutboxTest`.
 */
class V3InboxDesktopUiTest {

  @get:Rule val rule = createComposeRule()
  private lateinit var server: TestHttpServer
  private val root = "/api/v1/sync/v3/conflicts"
  private val bodies = Collections.synchronizedList(ArrayList<String>())
  @Volatile private var base = ""
  private var clock = 1_800_000_000_000L

  @Before fun start() {
    I18n.source = { Lang.FA }
    server = TestHttpServer()
    base = server.baseUrl
  }

  @After fun stop() { server.close(); I18n.source = { Lang.FA } }

  private class Mem : V3InboxQueueStore {
    @Volatile var text: String? = null
    override fun read(): String? = text
    override fun write(text: String) { this.text = text }
  }

  private fun model(disk: Mem = Mem()): V3InboxModel {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = 0
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    val api = ApiClient(HttpEngine(baseUrl = { base }, allowInsecure = true), tokens)
    return V3InboxModel(V3InboxClient(V3InboxHttp(api), disk) { clock }, me = "desk-c", lock = Mutex(), now = { clock })
  }

  private val data = ShopData(products = listOf(Product(id = "p-tea", name = "چای سبز")))

  private fun skew(status: String = "open") = """{"id":"cf_skew1","kind":"clock_skew","task":false,"revertible":${status == "open"},
    "table":"products","row_id":"p-tea","field":"*","winner_value":{"name":"چای سبز","salePriceMinor":10000},
    "loser_value":{"name":"چای سبزِ تازه","salePriceMinor":12500},"winner_hlc":"000001799999990000:000000:desk-a",
    "loser_hlc":"000001800000600000:000000:desk-c","winner_device":"desk-a","loser_device":"desk-c","detail":{},
    "status":"$status","at":1799999999000,"resolved_at":null,"resolved_by":"","resolution_op_id":""}"""

  private fun show(m: V3InboxModel) {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { V3InboxPage(m, data, onBack = null, refreshMs = 60_000L) }
      }
    }
  }

  private fun count(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size
  private fun waitFor(text: String, substring: Boolean = false) = rule.waitUntil(10_000) { count(text, substring) > 0 }

  @Test fun flagOffShowsNothingAndDoesNotAskAgainSoon() {
    server.on(root) { 200 to """{"ok":true,"conflicts":[${skew()}]}""" }
    val m = model()
    show(m)
    rule.waitUntil(10_000) { server.hits(root) == 1 }
    waitFor(StrV3Inbox.empty)
    assertEquals(0, count("چای سبز", substring = true))
    assertEquals(false, m.on.value)
    runBlocking { m.refresh() }
    assertEquals("پنجرهٔ خاموش: درخواستِ دوم نه", 1, server.hits(root))
  }

  @Test fun bothSidesMineTheirsAndMergeFieldByField() {
    var open = true
    server.on(root) { 200 to """{"ok":true,"conflicts":[${if (open) skew() else ""}],"inbox":{"v":1,"merge":true}}""" }
    server.on("$root/cf_skew1/resolve") { r ->
      bodies += r.body
      open = false
      200 to """{"ok":true,"conflict":${skew("reverted").replace("\"detail\":{}", "\"detail\":{\"merged\":[\"salePriceMinor\"]}")},"op":{"op_id":"srv.rv.cf_skew1"}}"""
    }
    show(model())
    waitFor(StrV3Inbox.kind("clock_skew"))
    assertTrue(count(StrV3Inbox.row(StrV3Inbox.table("products"), "چای سبز")) > 0)
    rule.onNodeWithText(StrV3Inbox.kind("clock_skew")).performClick()
    waitFor(StrV3Inbox.current)
    assertTrue(count(StrV3Inbox.other) > 0)
    assertTrue("مقدارِ هر دو سو", count(money(100.0)) > 0 && count(money(125.0)) > 0)
    assertTrue(count("چای سبزِ تازه") > 0)
    //  این دستگاه (desk-c) بازنده است ⇒ «مالِ من» زیرِ مقدارِ دیگر، «مالِ دستگاهِ دیگر» زیرِ فعلی
    assertTrue(count(StrV3Inbox.keepMine) > 0 && count(StrV3Inbox.keepTheirs) > 0)
    assertTrue(count(StrV3Inbox.thisDevice, substring = true) > 0)
    rule.onNodeWithText(StrV3Inbox.merge).performClick()
    waitFor(StrV3Inbox.mergeTitle)
    rule.onNodeWithText(StrV3Inbox.pickOther + " · " + money(125.0)).performClick()
    rule.onNodeWithText(StrV3Inbox.apply).performClick()
    rule.waitUntil(10_000) { server.hits("$root/cf_skew1/resolve") == 1 }
    assertEquals(listOf("""{"action":"merge","fields":["salePriceMinor"]}"""), bodies.toList())
    rule.waitUntil(10_000) { count(StrV3Inbox.kind("clock_skew")) == 0 }
    waitFor(StrV3Inbox.empty)
  }

  @Test fun offlineDecisionIsQueuedThenSentOnce() {
    server.on(root) { 200 to """{"ok":true,"conflicts":[${skew()}],"inbox":{"v":1,"merge":true}}""" }
    server.on("$root/cf_skew1/resolve") { r -> bodies += r.body; 200 to """{"ok":true,"conflict":${skew("kept")},"op":null}""" }
    val disk = Mem()
    val m = model(disk)
    show(m)
    waitFor(StrV3Inbox.kind("clock_skew"))
    //  شبکه رفت: سرورِ بسته
    val dead = TestHttpServer().also { it.close() }
    base = dead.baseUrl
    rule.onNodeWithText(StrV3Inbox.kind("clock_skew")).performClick()
    waitFor(StrV3Inbox.keepTheirs)
    rule.onNodeWithText(StrV3Inbox.keepTheirs).performClick()
    waitFor(StrV3Inbox.queued)
    assertEquals(0, server.hits("$root/cf_skew1/resolve"))
    assertTrue("تصمیم روی دیسک", disk.text.orEmpty().contains("cf_skew1"))
    //  شبکه برگشت
    base = server.baseUrl
    runBlocking { m.refresh(force = true) }
    rule.waitUntil(10_000) { server.hits("$root/cf_skew1/resolve") == 1 }
    assertEquals(listOf("""{"action":"keep"}"""), bodies.toList())
    assertEquals(emptyList<Any>(), m.queued.value)
    runBlocking { m.refresh(force = true) }
    assertEquals("یک بار، نه دو بار", 1, server.hits("$root/cf_skew1/resolve"))
  }
}
