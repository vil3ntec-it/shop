package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSupportAccess
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.data.repo.SupportAccessRepository
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.shell.SupportAccessNoticeContent
import ir.vil3ntec.tohid.ui.v3.shell.SupportAccessWatch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  TEN-04 — نوارِ «پشتیبانی در حالِ مشاهده» ِ پوسته (`SupportAccessNoticeContent`) روی سرورِ ساختگیِ 127.0.0.1 با همان `shape` ِ
 *  `lib/support-access.js`:
 *   • اجازهٔ زنده با رضایتِ دکان ⇒ نوار با «چه کسی، چرا، چند دقیقه» و «بستنِ دسترسی» ⇒ یک POST ِ revoke و نوار می‌رود؛
 *   • «شکستنِ شیشه» ⇒ نوارِ خطا با همان توضیح و **بی** دکمهٔ بستن؛
 *   • درخواستِ بی‌پاسخ ⇒ «دیدن و پاسخ» ⇒ برگه ⇒ «پذیرفتن» یک POST ِ accept (تأییدِ دوباره را `ReauthHost` ِ برنامه می‌پرسد)؛
 *   • غیرِ صاحب ⇒ هیچ درخواست و هیچ نوار؛ ۴۰۳ ِ سرور ⇒ دیگر پرسیده نمی‌شود.
 */
class SupportAccessNoticeUiTest {

  @get:Rule val rule = createComposeRule()
  private lateinit var server: TestHttpServer
  private val root = "/api/v1/shop/support-access"
  private val now = 2_000_000_000_000L

  @Before fun start() {
    I18n.source = { Lang.FA }
    server = TestHttpServer()
    reset()
  }

  @After fun stop() { server.close(); reset() }

  private fun reset() {
    SupportAccessWatch.grants.value = emptyList()
    SupportAccessWatch.checkedAt = 0
    SupportAccessWatch.off = false
  }

  private fun repo(): SupportAccessRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = 0
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return SupportAccessRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  private fun grant(id: String, status: String, consent: String = "tenant", extra: String = "") =
    """{"id":"$id","scope":"read_private","consent":"$consent","status":"$status","reason":"بررسیِ فاکتورِ گم‌شده","ticketId":"",
      "minutes":30,"requestedBy":"پشتیبانِ یک","grantedBy":"","revokedBy":"","revokeReason":"","createdAt":${now - 60_000},
      "decidedAt":null,"startsAt":${now - 60_000},"expiresAt":${now + 25 * 60_000},"revokedAt":null,"notifiedAt":null,"uses":2$extra}"""

  private fun show(enabled: Boolean = true) {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { SupportAccessNoticeContent(repo(), now = { now }, enabled = enabled) }
      }
    }
  }

  private fun count(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size
  private fun waitFor(text: String, substring: Boolean = false) = rule.waitUntil(10_000) { count(text, substring) > 0 }

  @Test fun liveTenantGrantShowsWhoWhyHowLongAndCloses() {
    var revoked = false
    server.on(root) { 200 to """{"grants":[${if (revoked) grant("agr_1", "revoked") else grant("agr_1", "active")}],"policy":{}}""" }
    server.on("$root/agr_1/revoke") { r -> assertEquals("POST", r.method); revoked = true; 200 to """{"grant":${grant("agr_1", "revoked")}}""" }
    show()
    waitFor(StrSupportAccess.watching(25))
    assertTrue(count("پشتیبانِ یک", substring = true) > 0)
    assertTrue(count("بررسیِ فاکتورِ گم‌شده", substring = true) > 0)
    rule.onNodeWithText(StrSupportAccess.close).performClick()
    rule.waitUntil(10_000) { server.hits("$root/agr_1/revoke") == 1 }
    rule.waitUntil(10_000) { count(StrSupportAccess.watching(25)) == 0 }
  }

  @Test fun breakGlassSaysItAndHasNoCloseButton() {
    server.on(root) { 200 to """{"grants":[${grant("agr_bg", "active", consent = "break_glass")}]}""" }
    show()
    waitFor(StrSupportAccess.watching(25))
    assertTrue(count(StrSupportAccess.breakGlass, substring = true) > 0)
    assertEquals(0, count(StrSupportAccess.close))
  }

  @Test fun pendingRequestOpensSheetAndAccepts() {
    var accepted = false
    server.on(root) {
      200 to """{"grants":[${if (accepted) grant("agr_p", "active") else grant("agr_p", "pending", extra = ",\"requestExpiresAt\":${now + 3_600_000}")}]}"""
    }
    server.on("$root/agr_p/accept") { accepted = true; 200 to """{"grant":${grant("agr_p", "active")}}""" }
    server.on("$root/agr_p/deny") { 200 to """{"grant":${grant("agr_p", "denied")}}""" }
    show()
    waitFor(StrSupportAccess.request)
    assertEquals("تا پاسخ نیامده هیچ «در حالِ مشاهده»", 0, count(StrSupportAccess.watching(25)))
    rule.onNodeWithText(StrSupportAccess.answer).performClick()
    waitFor(StrSupportAccess.accept)
    rule.onNodeWithText(StrSupportAccess.accept).performClick()
    rule.waitUntil(10_000) { server.hits("$root/agr_p/accept") == 1 }
    assertEquals(0, server.hits("$root/agr_p/deny"))
    rule.waitUntil(10_000) { count(StrSupportAccess.watching(25)) > 0 }
  }

  @Test fun notOwnerAsksNothingAndForbiddenStopsAsking() {
    server.on(root) { 403 to """{"error":{"code":"owner_only","message":"فقط صاحب"}}""" }
    show(enabled = false)
    rule.waitForIdle()
    Thread.sleep(300)
    assertEquals(0, server.hits(root))
    assertEquals(0, count(StrSupportAccess.request))
  }

  @Test fun forbiddenTurnsTheWatchOff() {
    server.on(root) { 403 to """{"error":{"code":"owner_only","message":"فقط صاحب"}}""" }
    show(enabled = true)
    rule.waitUntil(10_000) { SupportAccessWatch.off }
    assertEquals(1, server.hits(root))
    assertEquals(0, count(StrSupportAccess.request))
  }
}
