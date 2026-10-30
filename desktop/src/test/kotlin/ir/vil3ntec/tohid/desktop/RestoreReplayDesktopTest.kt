package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  §۹.۵ — تکه‌های اندرویدیِ «سرور از پشتیبان برگشت» (`SyncV1Engine.checkEpoch`)، روی
 *  همان `Context`ِ کامپیوتر: صف و حالِ واقعی روی دیسک. منطقِ خالص در
 *  `core/sync/RestoreReplayTest.kt` و سناریوی سرتاسری در `server/test/e2e/ops.e2e.mjs`.
 */
class RestoreReplayDesktopTest {

  private val key = "restore-replay-${System.nanoTime()}"
  private fun op(id: String) = SyncOp(id, 1L, "sales", "s-$id", SyncOp.UPDATE, JsonObject(mapOf("note" to JsonPrimitive(id))))

  @Test fun `opهای دوباره جلوی صف، با همان op_id، بی تکرارِ آنچه در صف هست`() {
    val q = OpQueue(DesktopContext, key)
    q.push(listOf(op("o3"), op("o4")))
    assertEquals(2, q.pushFront(listOf(op("o1"), op("o2"), op("o3"))))
    assertEquals(listOf("o1", "o2", "o3", "o4"), q.all().map { it.opId })
    assertEquals("دستهٔ بعدی از جلوی صف", listOf("o1", "o2"), q.batch(maxOps = 2).map { it.opId })
    //  همان کار دوباره (بسته شدن پیش از نشستنِ دوران) ⇒ هیچ چیزِ تازه‌ای
    assertEquals(0, q.pushFront(listOf(op("o1"), op("o2"))))
    assertEquals(4, OpQueue(DesktopContext, key).size())
    assertEquals(0, q.pushFront(emptyList()))
  }

  @Test fun `دوران و «در انتظارِ ساختنِ دوباره» با هم روی دیسک`() {
    val s = SyncStateV1(DesktopContext, key)
    s.cursor = 120
    s.epoch = "ep_old"
    assertFalse(s.restorePending)
    assertTrue(s.commitRestore("ep_new", true))
    val again = SyncStateV1(DesktopContext, key)
    assertEquals("ep_new", again.epoch)
    assertTrue(again.restorePending)
    assertEquals("cursor دست نخورد (Snapshot عوضش می‌کند)", 120L, again.cursor)
    assertTrue(s.commitRestore("ep_new", false))
    assertFalse(SyncStateV1(DesktopContext, key).restorePending)
  }

  @Test fun `«پرچمِ دورانِ سرور روشن است» برای هر حساب جدا روی دیسک — خاموش پیش‌فرض، پس هیچ درخواستی`() {
    val s = SyncStateV1(DesktopContext, key)
    assertFalse("پیش‌فرض: درِ دوران پرسیده نمی‌شود", s.epochLive)
    s.epochLive = true
    assertTrue(SyncStateV1(DesktopContext, key).epochLive)
    assertFalse("حسابِ دیگر جدا", SyncStateV1(DesktopContext, "$key-other").epochLive)
    s.epochLive = false
    assertFalse(SyncStateV1(DesktopContext, key).epochLive)
  }

  @Test fun `pushFront و push زیرِ یک قفل — سقفِ صف و دوباره‌ها با هم`() {
    val q = OpQueue(DesktopContext, "$key-lock")
    val t = Thread { repeat(50) { i -> q.push(listOf(op("n$i"))) } }
    t.start()
    repeat(50) { i -> q.pushFront(listOf(op("r$i"))) }
    t.join()
    val ids = q.all().map { it.opId }
    assertEquals("هیچ opی گم نشد", 100, ids.size)
    assertEquals(100, ids.toSet().size)
  }
}
