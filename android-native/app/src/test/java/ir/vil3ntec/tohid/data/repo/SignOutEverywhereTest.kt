package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileHomeModel
import ir.vil3ntec.tohid.ui.v3.account.profile.SignOutModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/**
 *  AUTH-06 — «خروج از همهٔ دستگاه‌ها» ِ خودِ کاربر (`POST /api/auth/logout-all`، تأییدِ دوبارهٔ ورود در سرور):
 *  درخواست با نشستِ همین حساب می‌رود و شمارِ نشست‌های بسته برمی‌گردد؛ خودِ این فراخوان توکن را پاک نمی‌کند (خروجِ
 *  همین دستگاه پس از پذیرشِ سرور با همان `SafeSignOut.finish` است) و خطای سرور «خارج شدید» ِ دروغ نمی‌سازد.
 */
class SignOutEverywhereTest {

  private lateinit var server: TestHttpServer

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() = server.close()

  private class Memory : TokenStorage {
    var cleared = 0
    override var accessToken: String? = "open-session"
    override var refreshToken: String? = "ref-1"
    override var accessExpiresAt: Long = Long.MAX_VALUE
    override fun save(access: String?, refresh: String?, expiresAt: Long) { accessToken = access }
    override fun clear() { cleared++; accessToken = null; refreshToken = null }
  }

  private fun auth(tokens: TokenStorage) =
    AuthRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens), tokens)

  @Test fun `درخواست با نشستِ همین حساب به logout-all می‌رود و توکن را خودش پاک نمی‌کند`(): Unit = runBlocking {
    val seen = Collections.synchronizedList(ArrayList<TestHttpServer.Request>())
    server.on("/api/v1/auth/logout-all") { r -> seen += r; 200 to """{"ok":true,"sessions":3,"app":"shop","deviceKeys":0}""" }
    val tokens = Memory()
    val r = auth(tokens).signOutEverywhere()
    assertTrue(r is ApiResult.Success)
    assertEquals(3, (r as ApiResult.Success).value)
    val req = seen.single()
    assertEquals("POST", req.method)
    assertEquals("open-session", req.bearer)
    assertEquals(0, tokens.cleared)
    assertEquals("open-session", tokens.accessToken)
  }

  @Test fun `ردِ سرور خطا است و هیچ چیزی پاک نمی‌شود`(): Unit = runBlocking {
    server.on("/api/v1/auth/logout-all") { 503 to """{"error":{"code":"unavailable","message":"سرور در دسترس نیست"}}""" }
    val tokens = Memory()
    val r = auth(tokens).signOutEverywhere()
    assertTrue(r is ApiResult.Failure)
    assertEquals(0, tokens.cleared)
    //  برگه روی تأیید می‌ماند (با خطا)، نه «خارج شدید»
    assertEquals(SignOutModel.Stage.CONFIRM, SignOutModel.afterEverywhere(serverOk = false))
    assertNull(SignOutModel.afterEverywhere(serverOk = true))
  }

  @Test fun `«حساب من» — «خروج از همهٔ دستگاه‌ها» فقط برای حسابِ واردشده و پس از «خروج»`() {
    val full = ProfileHomeModel.menu(signedIn = true, canSettings = true)
    val items = full.map { it.item }
    assertEquals(items.indexOf(ProfileHomeModel.Item.SIGN_OUT) + 1, items.indexOf(ProfileHomeModel.Item.SIGN_OUT_ALL))
    assertEquals(AccountPaths.SIGN_OUT_ALL, full.single { it.item == ProfileHomeModel.Item.SIGN_OUT_ALL }.route)
    assertTrue(full.single { it.item == ProfileHomeModel.Item.SIGN_OUT_ALL }.danger)
    assertTrue(ProfileHomeModel.menu(signedIn = false, canSettings = true).none { it.item == ProfileHomeModel.Item.SIGN_OUT_ALL })
  }
}
