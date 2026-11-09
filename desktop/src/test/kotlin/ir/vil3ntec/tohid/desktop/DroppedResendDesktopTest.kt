package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.sync.v1.DroppedResend
import ir.vil3ntec.tohid.sync.v1.DroppedResendTest
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.RestoreMerge
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 *  بازفرستِ `hash_mismatch` ِ عددی روی **صفِ واقعیِ دیسک** (`OpQueue` با `Context` ِ کامپیوتر) — DATA-SAFETY §۷.
 *
 *  همان دفترِ ردشدهٔ `DroppedResendTest` (بایت‌های ساختِ پیشین؛ همان ِ `resend-dropped-v1.json`) در `oplog-*-dropped.json` ⇒
 *  `DroppedResend.run` ⇐ نامزدها جلوی صف، پیش از ویرایشِ تازه‌ترِ در صف، با همان op_id؛ ردهای دلیلِ دیگر سرِ جایشان.
 *  پذیرشِ سرور ⇒ همان ردیف‌ها از دفترِ ردشده پاک (نه زودتر)؛ ردِ دوباره ⇒ ردیفِ کهنه یک بار و دفترچه «rejected»؛
 *  بسته شدنِ برنامه میانِ دفترچه و صف ⇒ همان op دوباره؛ دورِ دوم ⇒ هیچ.
 */
class DroppedResendDesktopTest {

  //  همان ورودی‌های `DroppedResendTest` (همان که بردارِ `resend-dropped-v1.json` ِ آزمونِ سرور را می‌سازد)
  private val fx = DroppedResendTest()
  private val book = fx.book()
  private val snapshot: JsonObject = fx.snapshot()
  private val ledger: JsonObject = fx.ledger()
  private val resend = fx.vectorNow().getValue("resend").jsonArray.map { it.jsonObject }

  /** همان پروندهٔ دفترِ ردشده که ساختِ پیشین نوشته بود (`oplog-<کلید>-dropped.json`، بایت‌به‌بایت) */
  private fun seeded(key: String): OpQueue {
    File(DesktopContext.filesDir, "oplog-${OpQueue.fileKey(key)}-dropped.json").writeText(JsonArray(book).toString())
    return OpQueue(DesktopContext, key)
  }

  private var asked = 0
  private val io = DroppedResend.Io({ asked++; snapshot }, { emptySet<RestoreMerge.Row>() }, { emptyMap() })

  private fun opIdsOf(list: List<JsonObject>) = list.map { it.getValue("op").jsonObject.getValue("op_id").jsonPrimitive.content }
  private fun state(q: OpQueue) = q.resendRecord().associate { it.getValue("op_id").jsonPrimitive.content to it.getValue("state").jsonPrimitive.content }

  @Test fun `نامزدها جلوی صف با همان op_id؛ پذیرش پاک می‌کند، ردِ دوباره نه؛ دورِ دوم هیچ`() = runBlocking {
    val key = "resend-${System.nanoTime()}"
    val q = seeded(key)
    val before = q.dropped()
    assertEquals(book.size, before.size)
    //  ویرایشِ تازه‌ترِ همان کالا، پیش از بازفرست در صف
    val later = SyncOp("01JRSND00000000000000Q0009", 1_762_600_000_000, "products", "p-full", "update", buildJsonObject { put("name", JsonPrimitive("تازه")) })
    q.push(listOf(later))

    assertEquals(resend.size, DroppedResend.run(q, io, { ledger }, 1_762_700_000_000))
    val wire = q.all().map { it.toJson() }
    assertEquals("جلوی صف، به ترتیبِ دفتر؛ ویرایشِ تازه‌تر پس از آن‌ها", resend.map { it.toString() } + later.toJson().toString(), wire.map { it.toString() })
    assertEquals("دفترِ ردشده تا پذیرشِ سرور دست نمی‌خورد", before, q.dropped())
    val ids = resend.map { it.getValue("op_id").jsonPrimitive.content }
    assertEquals(ids.associateWith { DroppedResend.QUEUED }, state(q))

    //  دورِ دوم (همان شمارِ دفتر) و حتی پس از باز شدنِ دوباره: هیچ op ِ تازه‌ای
    assertEquals(0, DroppedResend.run(q, io, { ledger }, 1_762_700_000_001))
    val reopened = OpQueue(DesktopContext, key)
    assertEquals(0, DroppedResend.run(reopened, io, { ledger }, 1_762_700_000_002))
    assertEquals(resend.size + 1, reopened.size())
    assertEquals("یک Snapshot برای یک دور", 1, asked)

    //  «سرور»: همه جز خرید پذیرفته؛ خرید باز رد (همان راهِ `SyncV1Engine.pushQueue`: drop پس ack)
    val pu = ids.single { it.endsWith("U0001") }
    val ok = ids - pu
    reopened.accepted(ok, 1_762_700_000_100)
    reopened.drop(reopened.all().filter { it.opId == pu }, "approval_required")
    reopened.ack(listOf(pu))
    val after = reopened.dropped()
    assertEquals("پذیرفته‌ها از دفترِ ردشده بیرون", emptyList<String>(), opIdsOf(after).filter { it in ok })
    assertEquals("ردِ دوباره: همان ردیفِ کهنه، یک بار", 1, opIdsOf(after).count { it == pu })
    assertEquals("بقیه دست نخورد", before.filter { opIdsOf(listOf(it)).single() !in ok }, after)
    assertEquals(ok.associateWith { DroppedResend.ACCEPTED } + (pu to DroppedResend.REJECTED), state(reopened))
    assertEquals(listOf(later.opId), reopened.all().map { it.opId })
    assertEquals(0, DroppedResend.run(reopened, io, { ledger }, 1_762_700_000_200))
    assertEquals(1, reopened.size())
  }

  @Test fun `بسته شدن میانِ دفترچه و صف ⇒ همان op دوباره، یک بار`() = runBlocking {
    val key = "resend-${System.nanoTime()}"
    val q = seeded(key)
    assertEquals(resend.size, DroppedResend.run(q, io, { ledger }, 1_762_700_000_000))
    //  صف پیش از نوشتنِ «accepted» گم شد (یا پذیرفته و ack شد و برنامه بسته شد)
    q.ack(q.all().map { it.opId })
    assertEquals(0, q.size())
    val reopened = OpQueue(DesktopContext, key)
    assertEquals(resend.size, DroppedResend.run(reopened, io, { ledger }, 1_762_700_000_001))
    assertEquals(resend.map { it.toString() }, reopened.all().map { it.toJson().toString() })
    assertEquals("هیچ تصمیمِ دوباره‌ای (یک ردیف برای هر op)", resend.size + skippedCount(), reopened.resendRecord().size)
    assertEquals("یک Snapshot", 1, asked)
  }

  @Test fun `نشانِ صندوق روی دیسک (SetAsideMarks) ⇒ همان سند نمی‌رود، بقیه می‌روند`() = runBlocking {
    val key = "resend-${System.nanoTime()}"
    val q = seeded(key)
    val id = "01JRSND00000000000000R0002"
    //  صاحب همان فاکتور را در «سندهای ردشده» اصلاح کرد؛ دفتر آن کلید را از دست داد، نشانِ کنارِ صف ماند
    q.settledMarks().add("cf-1", listOf(id))
    val marked = DroppedResend.Io({ asked++; snapshot }, { emptySet<RestoreMerge.Row>() }, { emptyMap() }) {
      DroppedResend.settled(ir.vil3ntec.tohid.data.ShopData(), OpQueue(DesktopContext, key).settledMarks())
    }
    val ids = resend.map { it.getValue("op_id").jsonPrimitive.content }
    assertEquals(ids.size - 1, DroppedResend.run(q, marked, { ledger }, 1_762_700_000_000))
    assertEquals(ids - id, q.all().map { it.opId })
    val rec = q.resendRecord().single { it.getValue("op_id").jsonPrimitive.content == id }
    assertEquals(DroppedResend.SKIPPED to "set_aside", rec.getValue("state").jsonPrimitive.content to rec.getValue("why").jsonPrimitive.content)
  }

  private fun skippedCount(): Int = DroppedResend.candidates(book, emptySet()).size - resend.size

}
