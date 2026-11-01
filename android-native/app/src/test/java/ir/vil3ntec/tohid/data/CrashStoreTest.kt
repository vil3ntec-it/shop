package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.ops.CrashReport
import ir.vil3ntec.tohid.core.ops.CrashReports
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  OPS-03 — صفِ روی دیسکِ گزارشِ خطا: رفت‌وبرگشتِ بی‌کم‌وکاست، فایلِ خراب ⇒ صفِ خالی
 *  (برنامه بالا می‌آید)، ردیفِ ناجور کنار، و بدنهٔ `POST /api/errors` با `report_id`.
 */
class CrashStoreTest {

  private val now = 1_761_800_000_000L

  @Test
  fun `صف بی کم و کاست روی دیسک می‌ماند`() {
    val a = CrashReports.fromThrowable(IllegalStateException("x"), "2.14.4", now)
    val b = a.copy(id = CrashReports.newId(now + 1), atMs = now + 1, count = 3, shown = true, sent = true)
    assertEquals(listOf(a, b), CrashStore.decode(CrashStore.encode(listOf(a, b))))
  }

  @Test
  fun `فایلِ خراب یا ناجور صفِ خالی است و برنامه را نمی‌شکند`() {
    for (bad in listOf(null, "", "   ", "{", "{}", "\"x\"", "[1,2]", "[{\"id\":\"cer_bad\",\"at\":1}]", "[{\"id\":\"cer_01JB2Z3K4M5N6P7Q8R9S0T0001\"}]")) {
      assertEquals(bad.toString(), emptyList<CrashReport>(), CrashStore.decode(bad))
    }
    //  ردیفِ درست کنارِ ردیفِ خراب می‌ماند
    val ok = """[{"id":"cer_01JB2Z3K4M5N6P7Q8R9S0T0001","at":5,"v":"1","message":"m","stack":"s","sig":"g","count":2},7,{"id":1}]"""
    val list = CrashStore.decode(ok)
    assertEquals(1, list.size)
    assertEquals(2, list.single().count)
    assertFalse(list.single().shown)
    assertFalse(list.single().sent)
  }

  @Test
  fun `آن‌چه از دیسک می‌آید دوباره پوشانده می‌شود`() {
    val raw = """[{"id":"cer_01JB2Z3K4M5N6P7Q8R9S0T0001","at":5,"message":"شماره 0791112233","stack":"ali@example.com"}]"""
    val r = CrashStore.decode(raw).single()
    assertFalse(r.message.contains("0791112233"))
    assertFalse(r.stack.contains("ali@example.com"))
  }

  @Test
  fun `بدنهٔ فرستادن همان نام‌های سرور را دارد`() {
    val r = CrashReports.fromThrowable(RuntimeException("بوم"), "2.14.4", now).copy(count = 2)
    val body = CrashStore.body(r)
    assertEquals(r.id, body.getValue("report_id").jsonPrimitive.content)
    assertEquals("count=2", body.getValue("log_tail").jsonPrimitive.content)
    assertEquals("shop", body.getValue("app").jsonPrimitive.content)
    assertTrue(body.getValue("message").jsonPrimitive.content.startsWith("RuntimeException"))
  }
}
