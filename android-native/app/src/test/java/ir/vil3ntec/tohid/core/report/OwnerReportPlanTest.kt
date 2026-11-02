package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PAY-20 / RPT-12 — قاعده‌های رابطِ گزارشِ خودکار، همان `server/src/lib/owner-report.js` */
class OwnerReportPlanTest {

  private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

  @Test fun `پرچم خاموش یا غیرِ صاحب یعنی هیچ تنظیم و گزارشی`() {
    assertEquals(OwnerReportPlan.View(), OwnerReportPlan.parse(obj("""{"enabled":false}""")))
    val notOwner = OwnerReportPlan.parse(obj("""{"enabled":true,"owner":false,"reports":[{"reportDate":"2026-10-31"}]}"""))
    assertTrue(notOwner.enabled)
    assertFalse(notOwner.owner)
    assertTrue(notOwner.reports.isEmpty())
  }

  @Test fun `نمای صاحب از پاسخِ سرور خوانده می‌شود و عددِ نبود null است`() {
    val v = OwnerReportPlan.parse(obj("""
      {"enabled":true,"owner":true,"timezone":"Asia/Kabul",
       "settings":{"active":true,"frequency":"weekly","weekday":6,"hour":8,"channels":["email","inapp","whatsapp"],"version":3},
       "channels":{"inapp":true,"push":true,"email":false,"whatsapp":false},
       "reports":[{"reportDate":"2026-10-31","frequency":"weekly","status":"sent","summary":{"invoices":2,"netSales":110000}},
                  {"reportDate":"2026-10-24","frequency":"weekly","status":"error","summary":{}}]}
    """))
    assertEquals(OwnerReportPlan.Settings(true, "weekly", 6, 8, listOf("inapp", "email"), 3), v.settings)
    assertFalse(v.emailAvailable)
    assertEquals("Asia/Kabul", v.timezone)
    assertEquals(110000L, v.reports[0].netSalesMinor)
    assertNull(v.reports[1].netSalesMinor)
    assertNull(v.reports[1].invoices)
  }

  @Test fun `صبح یعنی دیروزِ کامل و از ظهر به بعد امروز تا همان ساعت`() {
    val s = OwnerReportPlan.Settings(active = true, hour = 11)
    assertEquals("هر روز ساعتِ 11:00: دیروز (کامل)", OwnerReportPlan.coverage(s))
    assertEquals("هر روز ساعتِ 12:00: امروز تا ساعتِ 12:00", OwnerReportPlan.coverage(s.copy(hour = 12)))
    assertEquals("هر شنبه ساعتِ 21:00: هفت روزِ تا امروز تا ساعتِ 21:00",
      OwnerReportPlan.coverage(s.copy(frequency = OwnerReportPlan.WEEKLY, weekday = 6, hour = 21)))
    assertEquals("00:00", OwnerReportPlan.hourLabel(0))
    assertEquals("یکشنبه", OwnerReportPlan.weekdayName(0))
    assertEquals(6, OwnerReportPlan.WEEK_ORDER.first())
  }

  @Test fun `آخرین کانال برداشته نمی‌شود و واتس‌اپ پذیرفته نیست`() {
    assertEquals(listOf("inapp"), OwnerReportPlan.toggleChannel(listOf("inapp"), "inapp"))
    assertEquals(listOf("inapp", "email"), OwnerReportPlan.toggleChannel(listOf("email"), "inapp"))
    assertEquals(listOf("email"), OwnerReportPlan.toggleChannel(listOf("inapp", "email"), "inapp"))
    assertEquals(listOf("push"), OwnerReportPlan.toggleChannel(listOf("push"), "whatsapp"))
  }

  @Test fun `بدنهٔ نوشتن همان شکلِ سرور است با نسخهٔ دیده‌شده`() {
    val b = OwnerReportPlan.body(OwnerReportPlan.Settings(true, "weekly", 9, 30, listOf("email", "inapp")), 4)
    assertEquals("true", b["active"]!!.jsonPrimitive.content)
    assertEquals("weekly", b["frequency"]!!.jsonPrimitive.content)
    assertEquals("6", b["weekday"]!!.jsonPrimitive.content)
    assertEquals("23", b["hour"]!!.jsonPrimitive.content)
    assertEquals(listOf("inapp", "email"), b["channels"]!!.jsonArray.map { it.jsonPrimitive.content })
    assertEquals("4", b["expectedVersion"]!!.jsonPrimitive.content)
  }
}
