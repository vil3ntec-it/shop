package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
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

  @Test fun `دوران و cursor ِ پایین‌آمده با هم روی دیسک`() {
    val s = SyncStateV1(DesktopContext, key)
    s.cursor = 120
    s.epoch = "ep_old"
    assertTrue(s.commitEpoch("ep_new", 100))
    val again = SyncStateV1(DesktopContext, key)
    assertEquals("ep_new", again.epoch)
    assertEquals(100L, again.cursor)
  }
}
