package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.server.ServerIdentity
import ir.vil3ntec.tohid.core.server.ServerProfile
import ir.vil3ntec.tohid.core.server.ServerProfiles
import ir.vil3ntec.tohid.platform.ServerSwitchAndroid
import java.net.InetAddress
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  برنامهٔ دکان و حالتِ پیکِ آن (یک برنامه، `AppMode.COURIER`) با چند نشانیِ یک سرور، روی HTTP ِ واقعی (SERVERS §۳):
 *  پرچمِ خاموش ⇒ همان نشانیِ زمانِ ساخت و هیچ پرسشی؛ نشانیِ نرسیده ⇒ نشانیِ بعدیِ **همان** `serverId`؛ آخرین نشانیِ
 *  سالم می‌ماند؛ سرورِ دیگر پشتِ نشانیِ دوم ⇒ بسته، هرگز برگزیده.
 */
class ServerFailoverDesktopTest {

  private val ctx = DesktopContext
  private val prefs get() = ctx.getSharedPreferences("tohid-servers", 0)
  private val idA = "srv_0123456789abcdef0123"
  private val idB = "srv_fedcba9876543210fedc"
  private val servers = ArrayList<TestHttpServer>()

  private fun info(id: String) = "{\"ok\":true,\"kind\":\"tohid-server\",\"serverId\":\"$id\",\"name\":\"آزمون\"," +
    "\"version\":\"2.14.5\",\"wire\":{\"min\":1,\"max\":1},\"api\":[\"v1\"],\"features\":[\"sync.v1\"],\"publicUrl\":\"\"}"

  private fun server(id: String): TestHttpServer = TestHttpServer().also { s ->
    s.on("/api/v1/config/server") { 200 to info(id) }
    servers += s
  }

  /** نشانیِ درگاهی که کسی رویش گوش نمی‌دهد ⇒ «نرسید» (وصل نشد) */
  private fun deadUrl(): String = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { "http://127.0.0.1:${it.localPort}" }

  private fun activate(urls: List<String>, id: String?) {
    val base = ServerSwitchAndroid.profiles(ctx)
    val p = ServerProfile(id = "p-failover", name = "سرورِ آزمون", urls = urls, serverId = id, lan = true)
    val added = (base.add(p) as ServerProfiles.Change.Ok).profiles
    ServerSwitchAndroid.save(ctx, (added.activate(p.id) as ServerProfiles.Change.Ok).profiles)
  }

  @After
  fun clean() {
    ServerSwitchAndroid.setEnabledRaw(ctx, false)
    prefs.edit().clear().commit()
    ServerSwitchAndroid.setEnabledRaw(ctx, false)
    servers.forEach { it.close() }
  }

  @Test
  fun flagOffKeepsBuildTimeUrlAndNeverAsksAnyServer() {
    val a = server(idA)
    ServerSwitchAndroid.setEnabledRaw(ctx, false)
    activate(listOf(a.baseUrl), idA)
    assertNull(ServerSwitchAndroid.activeUrl(ctx))
    assertEquals(AppConfig.buildTimeBaseUrl, AppConfig.baseUrl(ctx))
    assertEquals("", ServerSwitchAndroid.scope(ctx))
    Thread.sleep(300)
    assertEquals("پرچمِ خاموش ⇒ هیچ سنجشی", 0, a.hits("/api/v1/config/server"))
  }

  @Test
  fun unreachableUrlFallsBackToSameServerAndLastGoodUrlIsKept() {
    val dead = deadUrl()
    val b = server(idA)
    ServerSwitchAndroid.setEnabledRaw(ctx, true)
    activate(listOf(dead, b.baseUrl), idA)
    ServerSwitchAndroid.recheck(ctx)
    assertEquals(b.baseUrl, ServerSwitchAndroid.activeUrl(ctx))
    assertEquals(b.baseUrl, AppConfig.baseUrl(ctx))
    assertNull("نشانیِ برگزیده درِ شبکه را باز دارد", ServerSwitchAndroid.reject(b.baseUrl, false))
    //  نشانیِ نخست هم برگشت (همان سرور): آخرین نشانیِ سالم سرِ جایش می‌ماند تا خودش نرسد
    val a = server(idA)
    activateAgainWith(listOf(a.baseUrl, b.baseUrl))
    prefs.edit().putString("chosen:p-failover", b.baseUrl).commit()
    ServerSwitchAndroid.recheck(ctx)
    assertEquals(b.baseUrl, ServerSwitchAndroid.activeUrl(ctx))
    assertEquals("نشانیِ سالمِ پیشین اول پرسیده شد و بس", 0, a.hits("/api/v1/config/server"))
  }

  private fun activateAgainWith(urls: List<String>) {
    prefs.edit().remove("profiles").commit()
    activate(urls, idA)
  }

  @Test
  fun foreignServerIdBehindAlternateIsNeverChosenAndProfileIsBlocked() {
    val dead = deadUrl()
    val other = server(idB)
    ServerSwitchAndroid.setEnabledRaw(ctx, true)
    activate(listOf(dead, other.baseUrl), idA)
    ServerSwitchAndroid.recheck(ctx)
    assertEquals(1, other.hits("/api/v1/config/server"))
    val url = ServerSwitchAndroid.activeUrl(ctx)
    assertTrue("⛔ سرورِ دیگر برگزیده نشد: $url", url != other.baseUrl)
    val refused = ServerSwitchAndroid.reject(other.baseUrl, false)
    assertTrue(refused is ApiFailure.BadConfiguration)
    assertEquals(ServerIdentity.MISMATCH_MESSAGE, refused?.message)
  }
}
