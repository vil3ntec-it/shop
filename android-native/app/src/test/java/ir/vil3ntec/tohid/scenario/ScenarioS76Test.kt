package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.sync.v1.LiveSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S76 — رویدادِ زندهٔ تکراری یا خارج از ترتیب (§۱۹.۱۲): «رویدادِ تکراری یا قدیمی فرستاده شود؛ کلاینت نسخهٔ جدید را به
 *  عقب نبرد و پس از شکافِ sequence با snapshot/cursor بازیابی شود».
 *
 *  سوکتِ برنامه (`sync/v1/LiveSocket.kt`) فقط نشانهٔ «چیزی عوض شد» است: `{"event":"changed","cursor":N}` و `hello` هر کدام
 *  یک Pull می‌سازند و cursor ِ پیام هرگز خوانده نمی‌شود (`onChanged` هیچ ورودی ندارد)؛ Pull از cursor ِ خودِ دستگاه است.
 *  این‌جا همان سوکت با سرورِ جعلی ([FakeLivePort]) و یک دفترِ نمونه که Pull ِ آن «از cursor ِ خودم تا سرِ سرور» است.
 *
 *  ماندهٔ سناریو (pending، نه سرخ): Pull ِ واقعی (`SyncV1Engine.pull` با SharedPreferences و سرور) این‌جا نیست؛
 *  یکتایی و ترتیبِ op در سرور و جذبِ آن‌ها جدا سنجیده می‌شوند (`SyncV1CoreTest`، `SyncCommitTest`، سرور `sync-v1*.test.js`).
 */
class ScenarioS76Test {

  /** دفترِ سرور: نسخهٔ iم = مقدارِ آن نسخه (cursor = شمارِ نسخه‌ها) */
  private val server = mutableListOf("v1", "v2", "v3")

  /** دستگاه: فقط از cursor ِ خودش تا سرِ سرور می‌گیرد — همان قراردادِ `SyncV1Engine` */
  private var cursor = 0
  private var value = ""
  private var pulls = 0
  private val cursors = mutableListOf<Int>()

  private fun pull() {
    pulls++
    for (i in cursor until server.size) value = server[i]
    cursor = server.size
    cursors += cursor
  }

  private val port = FakeLivePort()
  private val live = port.socket { pull() }

  @After fun reset() { LiveSocket.current = null }

  private fun changed(c: Int) = """{"event":"changed","cursor":$c}"""

  @Test fun `تکراری، کهنه و شکافِ cursor هر کدام فقط یک Pull از cursor ِ خودِ دستگاه می‌سازند و دفتر عقب نمی‌رود`() {
    live.connect()
    port.last.opens()
    port.last.says("""{"event":"hello","cursor":3}""")
    assertEquals(1, pulls)
    assertEquals("v3", value)

    //  تکرارِ همان رخداد و رخدادی کهنه‌تر از آنچه دستگاه دارد
    port.last.says(changed(3))
    port.last.says(changed(3))
    port.last.says(changed(2))
    port.last.says(changed(1))
    assertEquals("v3", value)
    assertEquals(3, cursor)

    //  شکاف: سرور چهار نسخه جلو رفت و خبرِ ۴ تا ۶ هرگز نرسید؛ فقط خبرِ ۷ — و پس از آن خبرِ دیرِ ۵
    server += listOf("v4", "v5", "v6", "v7")
    port.last.says(changed(7))
    assertEquals("v7", value)
    assertEquals(7, cursor)
    port.last.says(changed(5))
    assertEquals("v7", value)

    //  هر رخداد دقیقاً یک Pull، و cursor ِ دستگاه هرگز پایین نیامد
    assertEquals(7, pulls)
    assertEquals(cursors.sorted(), cursors)
    assertEquals(listOf(true), port.lamp)
    assertTrue(port.timers.isEmpty())
  }

  @Test fun `پیامِ بدشکل، خالی یا ناشناخته نه Pull می‌سازد نه سوکت را می‌اندازد`() {
    live.connect()
    port.last.opens()
    port.last.says("not json")
    port.last.says("")
    port.last.says("{}")
    port.last.says("""{"event":"weird","cursor":99}""")
    port.last.says("""{"cursor":5}""")
    assertEquals(0, pulls)
    assertTrue(live.connected)
    assertTrue(port.timers.isEmpty())
    assertEquals(listOf(true), port.lamp)
  }

  @Test fun `رخدادِ دیرِ سوکتِ کهنه پس از وصلِ دوباره نشان را خاموش نمی‌کند و وصلِ دیگری نمی‌سازد`() {
    live.connect()
    val old = port.last
    old.opens()
    old.drops()
    assertEquals(listOf(true, false), port.lamp)
    port.advance(port.waits.last())
    val fresh = port.last
    fresh.opens()
    assertEquals(2, port.sockets.size)
    assertEquals(listOf(true, false, true), port.lamp)

    //  سوکتِ کهنه دیر خبر می‌دهد: باز شد، افتاد، سرور بست — هیچ‌کدام به سوکتِ تازه دست نمی‌زند
    old.opens()
    old.drops()
    old.serverCloses(4401)
    assertTrue(live.connected)
    assertEquals(listOf(true, false, true), port.lamp)
    assertTrue(port.timers.isEmpty())
    assertEquals(2, port.sockets.size)
    assertTrue("پاسخِ بستنِ سوکتِ کهنه همان 1000 است", old.closes == listOf(1000))

    //  سوکتِ تازه همچنان خبر می‌آورد
    server += "v4"
    fresh.says(changed(4))
    assertEquals("v4", value)
  }

  @Test fun `رخدادهای سکو هم به همان ترتیب می‌رسند و hello پس از هر وصل Pull می‌سازد`() {
    live.connect()
    port.last.opens()
    port.last.says("""{"event":"hello","cursor":3}""")
    port.last.says("""{"event":"photos"}""")
    port.last.says("""{"event":"scan","code":"6260000000017","pair":"123456"}""")
    port.last.drops()
    port.advance(port.waits.last())
    port.last.opens()
    port.last.says("""{"event":"hello","cursor":3}""")
    assertEquals(listOf("hello", "photos", "scan", "hello"), port.platform)
    assertEquals(2, pulls)
  }
}
