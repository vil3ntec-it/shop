package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.sync.OpBacklog
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  صفِ واقعیِ روی دیسک (`OpQueue`، همان `Context` ِ کامپیوتر) هیچ opی را دور نمی‌ریزد — یافتهٔ ممیزیِ SYN:
 *  پیش‌تر بالای ۵۰٬۰۰۰ op کهنه‌ترین‌ها `queue_overflow` می‌شدند و هرگز به سرور نمی‌رسیدند.
 *  ۶۰٬۰۰۰ op ِ کوچک (۲۰ ثبتِ سه‌هزارتایی، همان شکلِ `LedgerDiff.diff`) ⇐ دسته‌دسته با `batch`/`ack` تا ته ⇐
 *  همه، یک بار و به همان ترتیب. پیامِ صفِ بزرگ: `core/…/core/sync/OpBacklog.kt`.
 *  ⚠️ دسته‌ها این‌جا ۶٬۰۰۰تایی‌اند (موتور ۲۰۰): هر `batch`/`ack` کلِ پروندهٔ صف را می‌خواند، و ۳۰۰ دور روی
 *  صفِ ۶ مگابایتی آزمون را چند دقیقه‌ای می‌کرد. بریدنِ دستهٔ ۲۰۰تایی را `OpQueue.cut` و آزمون‌هایش می‌سنجند.
 */
class OpQueueBacklogDesktopTest {

  @Test fun `۶۰ هزار op همه می‌رسند — هیچ‌کدام دور ریخته نمی‌شود`() {
    val q = OpQueue(DesktopContext, "backlog-${System.nanoTime()}")
    val total = 60_000
    val per = 3_000
    for (r in 0 until total / per) {
      q.push(List(per) { i ->
        val n = r * per + i
        SyncOp("op-$n", 1_000L + r, "sales", "s-$n", SyncOp.INSERT, JsonObject(mapOf("n" to JsonPrimitive(n))))
      })
    }
    assertEquals(total, q.size())
    assertEquals("هیچ opی کنار نرفت", 0, q.droppedCount())
    assertTrue("پیامِ صفِ بزرگ", OpBacklog.warning(q.size()).startsWith("۶۰٬۰۰۰ تغییر هنوز به سرور نرسیده"))

    //  «سرور»: هر دسته را می‌پذیرد — همان حلقهٔ `SyncV1Engine.pushQueue`
    val arrived = ArrayList<String>(total)
    var rounds = 0
    while (q.size() > 0 && rounds < 1_000) {
      rounds++
      val batch = q.batch(maxOps = 6_000, maxBytes = 4 * 1024 * 1024)
      assertTrue(batch.isNotEmpty())
      arrived += batch.map { it.opId }
      q.ack(batch.map { it.opId })
    }
    assertEquals(total, arrived.size)
    assertEquals("هر op یک بار", total, arrived.toSet().size)
    assertEquals("به همان ترتیب", List(total) { "op-$it" }, arrived)
    assertEquals(0, q.size())
    assertEquals(0, q.droppedCount())
    assertEquals("", OpBacklog.warning(q.size()))
  }
}
