package ir.vil3ntec.tohid.core.support

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  آینهٔ کاتلینِ «از پشتیبانی بخواه» (`core/support/SupportProblem`) در برابرِ `server/src/lib/support-problems.json` — همان فایلی که
 *  سرور کد، پارامتر و واقعیت‌ها را با آن می‌پذیرد (`server/test/support-requests.test.js`). کد/پارامتر/کار/واقعیت/متن یکی عوض شود ⇒
 *  یکی از دو آزمون سرخ.
 */
class SupportProblemMirrorTest {

  private val reg: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "server/src/lib/support-problems.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("server/src/lib/support-problems.json پیدا نشد")
  }

  private fun text(o: JsonObject) = SupportProblem.Text(o.str("fa"), o.str("en"), o.str("ps"))
  private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content
  private fun JsonObject.strs(k: String) = getValue(k).jsonArray.map { it.jsonPrimitive.content }

  @Test
  fun `کدها، پارامترها، کار، واقعیت‌ها و سرخط‌ها همان سرورند`() {
    val server = reg.getValue("problems").jsonArray.map { it.jsonObject }
    assertEquals(server.map { it.str("code") }, SupportProblem.ALL.map { it.code })
    for ((s, k) in server.zip(SupportProblem.ALL)) {
      assertEquals(k.code, s.strs("param"), k.params)
      assertEquals(k.code, s.getValue("needsShop").jsonPrimitive.boolean, k.needsShop)
      assertEquals(k.code, s.str("fix"), k.fix.id)
      assertEquals(k.code, s.strs("facts"), k.facts)
      assertEquals(k.code, text(s.getValue("title").jsonObject), k.title)
    }
    val facts = reg.getValue("facts").jsonObject.mapValues { it.value.jsonPrimitive.content }
    assertEquals(facts, SupportProblem.FACTS)
    val names = reg.getValue("names").jsonObject.mapValues { text(it.value.jsonObject) }
    assertEquals(names, SupportProblem.NAMES)
  }

  @Test
  fun `کدِ کامل فقط پایه و پارامترِ مجاز`() {
    assertNotNull(SupportProblem.parse("flag_off:shop.orders"))
    assertNotNull(SupportProblem.parse("subscription_expired"))
    assertNull(SupportProblem.parse("flag_off"))
    assertNull(SupportProblem.parse("flag_off:shop.secret"))
    assertNull(SupportProblem.parse("subscription_expired:x"))
    assertNull(SupportProblem.parse("drop_tables"))
    assertEquals("flag_off:shop.orders", SupportProblem.code(SupportProblem.FLAG_OFF, "shop.orders"))
    assertEquals("سفارشِ آنلاین برای این دکان خاموش است", SupportProblem.title("flag_off:shop.orders") { it.fa })
    assertEquals("Delivery and couriers is not in this shop's plan", SupportProblem.title("plan_required:delivery") { it.en })
    assertEquals("کمک از پشتیبانی", SupportProblem.title("nope") { it.fa })
  }

  @Test
  fun `واقعیت‌ها فقط مجازهای همان مشکل با شکلِ سخت — ایمیل، متن، کسر و منفی می‌افتند`() {
    val def = SupportProblem.of(SupportProblem.SYNC_REFUSED)!!
    val out = SupportProblem.facts(def, mapOf(
      "appVersion" to "2.14.5", "platform" to "android", "screen" to "settings:sync", "syncError" to "rejected", "rejected" to 4,
      "devices" to 3, "email" to "a@b.cd", "note" to "سلام",
    ))
    assertEquals(mapOf("appVersion" to "2.14.5", "platform" to "android", "screen" to "settings:sync", "syncError" to "rejected", "rejected" to 4L), out)
    val bad = SupportProblem.facts(def, mapOf("appVersion" to "x; drop", "platform" to "windows", "screen" to "a@b.cd", "syncError" to "UPPER", "rejected" to -1))
    assertTrue(bad.toString(), bad.isEmpty())
    assertTrue(SupportProblem.facts(def, mapOf("rejected" to 1.5)).isEmpty())
  }

  @Test
  fun `دروازهٔ «چرا سفارش نمی‌رسد» ⇒ کدِ درخواست؛ کارِ خودِ دکان ⇒ هیچ`() {
    assertEquals("flag_off:shop.orders", SupportProblem.forOrderBlocker("orders_off"))
    assertEquals("flag_off:shop.storefront", SupportProblem.forOrderBlocker("storefront_off"))
    assertEquals("flag_off:shop.order_notify", SupportProblem.forOrderBlocker("alerts_off"))
    assertEquals("other", SupportProblem.forOrderBlocker("storefront_suspended"))
    assertNull(SupportProblem.forOrderBlocker("storefront_missing"))
    assertNull(SupportProblem.forOrderBlocker("no_subscription"))
  }
}
