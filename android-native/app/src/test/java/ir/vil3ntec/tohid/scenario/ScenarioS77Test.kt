package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.sync.v1.LiveSocket
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 *  S77 — قطعِ کانالِ زنده (§۱۹.۱۲): «WebSocket قطع شود؛ reconnect ِ کنترل‌شده کار کند، نشانِ زنده به آفلاین تغییر کند و
 *  پس از وصلِ دوباره وضعیتِ نهایی از API تطبیق داده شود».
 *
 *  همان سوکتِ برنامه (`sync/v1/LiveSocket.kt`) با سرور، ساعت و زمان‌بندِ جعلی ([FakeLivePort]): نشانِ «زنده/آفلاین»
 *  (`SyncV1Engine.liveConnected` و `LiveSocket.connected` ِ کارتِ «دستگاه‌ها»)، فاصلهٔ فزایندهٔ ۴ ثانیه تا ۵ دقیقه،
 *  ۱ ثانیهٔ 4403 ِ سرِ موعد، Pull با `hello` ِ پس از وصل، و `disconnect` که پایان است.
 *
 *  ماندهٔ سناریو (pending، نه سرخ): کارِ دوره‌ایِ ۱۵ دقیقه‌ای (`SyncV1Worker.schedule`، WorkManager) و قطعِ واقعیِ شبکه روی
 *  دستگاه این‌جا نیستند؛ رنگِ نشان در صفحه هم (فقط مقدارِ آن) سنجیده نمی‌شود.
 */
class ScenarioS77Test {

  private val port = FakeLivePort()
  private var pulls = 0
  private val live = port.socket { pulls++ }

  @After fun reset() { LiveSocket.current = null }

  @Test fun `قطعِ شبکه ⇒ نشان آفلاین و وصلِ دوباره با فاصلهٔ فزاینده تا سقفِ پنج دقیقه، و وصلِ موفق فاصله را از نو می‌کند`() {
    live.connect()
    assertSame(live, LiveSocket.current)
    port.last.opens()
    assertTrue(live.connected)
    assertEquals(listOf(true), port.lamp)

    port.last.drops()
    assertFalse(live.connected)
    assertEquals(listOf(true, false), port.lamp)
    assertEquals(2, port.publishes)
    //  سوکت‌هایی که پیش از باز شدن می‌افتند (شبکه هنوز نیست)
    repeat(9) {
      port.advance(port.waits.last())
      port.last.drops()
    }
    assertEquals(listOf(4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 128_000L, 256_000L, 300_000L, 300_000L, 300_000L), port.waits)
    assertEquals(10, port.sockets.size)
    assertEquals(1, port.timers.size)

    //  شبکه برگشت: باز شد ⇒ نشان روشن و Pull با hello؛ افتادنِ بعدی دوباره از ۴ ثانیه
    port.advance(port.waits.last())
    port.last.opens()
    port.last.says("""{"event":"hello","cursor":42}""")
    assertTrue(live.connected)
    assertEquals(true, port.lamp.last())
    assertEquals(1, pulls)
    port.last.drops()
    assertEquals(4_000L, port.waits.last())
  }

  @Test fun `بلیت نشد یا اینترنت نیست ⇒ هیچ سوکتی ساخته نمی‌شود و همان راهِ فزاینده`() {
    port.ticket = { throw IOException("offline") }
    live.connect()
    assertTrue(port.sockets.isEmpty())
    assertEquals(listOf(false), port.lamp)
    assertEquals(listOf(4_000L), port.waits)
    port.advance(4_000)
    assertEquals(listOf(4_000L, 8_000L), port.waits)

    //  سرور فقط ۴۰۱/۵۰۰ داد (بلیتِ تهی) ⇒ همان
    port.ticket = { null }
    port.advance(8_000)
    assertEquals(16_000L, port.waits.last())
    assertTrue(port.sockets.isEmpty())

    port.ticket = { "ticket" to "T-ok" }
    port.advance(16_000)
    assertEquals(1, port.sockets.size)
    assertTrue(port.last.url.contains("ticket=T-ok"))
    assertFalse("توکن هرگز در نشانیِ سوکت نیست (F0-11)", port.last.url.contains("ACCESS-TOKEN"))
  }

  @Test fun `4403 ِ سرِ موعد روی سوکتِ ده‌ثانیه‌زنده یک ثانیه بعد با بلیتِ تازه برمی‌گردد و 4403 ِ فوری راهِ فزاینده را می‌رود`() {
    live.connect()
    port.last.opens()
    port.advance(10_000)
    port.last.serverCloses(4403)
    //  پاسخِ بستن همان لحظه رفت و onClosed ِ پس از onClosing وصلِ دومی نخواست
    assertEquals(listOf(1000), port.last.closes)
    assertEquals(listOf(1_000L), port.waits)
    assertEquals(listOf(true, false), port.lamp)

    port.advance(1_000)
    assertEquals(2, port.sockets.size)
    assertTrue(port.last.url.contains("ticket=T2"))
    port.last.opens()
    port.advance(500)
    port.last.serverCloses(4403)
    assertEquals(listOf(1_000L, 4_000L), port.waits)
    assertEquals(1, port.timers.size)
  }

  @Test fun `connect ِ دوباره وسطِ گرفتنِ بلیت سوکتِ دوم نمی‌سازد`() {
    val gate = CompletableDeferred<Pair<String, String>?>()
    port.ticket = { gate.await() }
    live.connect()
    live.connect()
    live.connect()
    assertTrue(port.sockets.isEmpty())
    gate.complete("ticket" to "T-1")
    assertEquals(1, port.sockets.size)
    port.last.opens()
    live.connect()
    assertEquals(1, port.sockets.size)
  }

  @Test fun `بی حساب یا در حالتِ آموزشی نه سوکت نه حلقهٔ وصلِ دوباره`() {
    port.reachable = false
    live.connect()
    assertTrue(port.sockets.isEmpty())
    assertTrue(port.timers.isEmpty())
    assertTrue(port.lamp.isEmpty())
  }

  @Test fun `disconnect پایان است و بستنِ سرور پس از آن وصلِ دوباره نمی‌خواهد`() {
    live.connect()
    port.last.opens()
    live.disconnect()
    assertEquals(listOf(1000), port.last.closes)
    assertFalse(live.connected)
    assertEquals(listOf(true, false), port.lamp)
    port.last.serverCloses(1000)
    assertTrue(port.timers.isEmpty())
    port.advance(10 * 60_000L)
    assertEquals(1, port.sockets.size)
  }

  /**
   *  باگ (پیدا‌شده با همین آزمون): «دوباره وصل شو» ِ زمان‌بندی‌شده پیش از `disconnect` پس از آن هم اجرا می‌شد،
   *  `connect()` پرچمِ «ما بستیم» را پاک می‌کرد و سوکتِ تازه‌ای باز می‌شد که دیگر هیچ صفحه‌ای مالکش نبود (اندروید:
   *  `MainActivity.onDestroy` در صبرِ وصلِ دوباره، فرایند زنده) — سوکتِ زامبی با ping ِ هر ۳۰ ثانیه تا پایانِ فرایند، هر خبر
   *  دو بار (دو Pull، اسکنِ «گوشی به‌عنوانِ بارکدخوان» دو بار) و `LiveSocket.current` که به همان نمونهٔ رهاشده برمی‌گشت.
   */
  @Test fun `disconnect وسطِ صبرِ وصلِ دوباره سوکتِ رهاشده را زنده نمی‌کند ولی connect ِ صریح دوباره وصل می‌کند`() {
    live.connect()
    port.last.opens()
    port.last.drops()
    assertEquals(1, port.timers.size)

    live.disconnect()
    val next = port.socket { pulls++ }
    next.connect()
    next.connect()
    assertEquals(2, port.sockets.size)
    port.last.opens()

    port.advance(10 * 60_000L)
    assertEquals("هیچ سوکتِ تازه‌ای برای نمونهٔ رهاشده", 2, port.sockets.size)
    assertFalse(live.connected)
    assertSame(next, LiveSocket.current)

    //  همان نمونه با connect ِ صریح دوباره وصل می‌شود
    live.connect()
    assertEquals(3, port.sockets.size)
  }
}
