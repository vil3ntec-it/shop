package ir.vil3ntec.tohid.core.shopper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 *  CHK-03 — `core/test-vectors/account-cart-v1.json` روی سرورِ جعلیِ آزمون‌های Kotlin ([CartServer]) و کارخواهِ [ShopperApi]؛
 *  همان فایل را `server/test/storefront-cart.test.js` روی سرورِ واقعی (`lib/store-cart.js`) می‌دواند. پس سرورِ جعلیِ
 *  `ShopperCartSyncTest` همان پاسخ‌ها (کلیدها، نسخه، ۴۰۹، ادغامِ بیشینه، قیمتِ امروز، کدِ پرچمِ خاموش) را می‌دهد و برنامه همان را می‌خواند.
 */
class ShopperCartVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/account-cart-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/account-cart-v1.json پیدا نشد")
  }

  private fun JsonObject.s(k: String): String = getValue(k).jsonPrimitive.content

  private fun keys(k: String): List<String> = v.getValue(k).jsonArray.map { it.jsonPrimitive.content }

  /** [productId، qtyMilli، note، available، priceMinor] */
  private fun brief(c: AccountCart): List<List<Any?>> = c.lines.map { listOf(it.productId, it.qtyMilli, it.note, it.available, it.priceMinor) }

  private fun expected(lines: List<JsonElement>): List<List<Any?>> = lines.map { l ->
    val a = l.jsonArray
    listOf(a[0].jsonPrimitive.content, a[1].jsonPrimitive.long, a[2].jsonPrimitive.content, a[3].jsonPrimitive.boolean, a[4].jsonPrimitive.longOrNull)
  }

  private fun items(body: JsonObject): List<AccountItem> = body.getValue("items").jsonArray.map { x ->
    val o = x.jsonObject
    AccountItem(o.s("productId"), o.getValue("qtyMilli").jsonPrimitive.long, o["note"]?.jsonPrimitive?.content)
  }

  @Test fun theFakeServerAndTheClientMatchTheRealServerSteps() {
    assertEquals(1, v.getValue("version").jsonPrimitive.int)
    assertEquals(CartRules.MAX_LINES, v.getValue("maxLines").jsonPrimitive.int)
    val srv = CartServer("golestan")
    for (p in v.getValue("products").jsonArray.map { it.jsonObject }) {
      srv.product(p.s("id"), p.s("name"), p.s("unit"), (p.getValue("salePrice").jsonPrimitive.double * 100).roundToLong())
    }
    var last: ShopperResponse? = null
    val http = ShopperSamples.FakeHttp { r -> srv.handle(r).also { last = it } }
    val api = ShopperApi(http)
    val session = CartServer.SESSION_A
    for ((i, el) in v.getValue("steps").jsonArray.withIndex()) {
      val st = el.jsonObject
      val at = "گامِ ${i + 1} (${st.s("note")})"
      val body = st["body"]?.jsonObject
      val result: ShopperResult<AccountCart> = when (st.s("do")) {
        "unpublish" -> { srv.products.getValue(st.s("productId")).published = false; continue }
        "price" -> { srv.products.getValue(st.s("productId")).priceMinor = st.getValue("onlinePriceMinor").jsonPrimitive.long; continue }
        "get" -> api.accountCart("golestan", session)
        "put" -> api.putAccountCart("golestan", session, body!!.getValue("version").jsonPrimitive.long, items(body))
        "merge" -> api.mergeAccountCart("golestan", session, items(body!!))
        else -> error("گامِ ناشناخته: $at")
      }
      assertEquals(session, http.seen.last().headers[v.s("sessionHeader")], at)
      val status = st.getValue("status").jsonPrimitive.int
      val e = result.errorOrNull()
      assertEquals(status, e?.status ?: 200, at)
      st["code"]?.let { assertEquals(it.jsonPrimitive.content, e?.code, at) }
      val raw = Json.parseToJsonElement(last!!.body).jsonObject
      val cartJson = (if (e == null) raw["cart"] else raw.getValue("error").jsonObject["cart"])?.takeIf { it != JsonNull }?.jsonObject
      val want = st["cart"]?.jsonObject
      if (want == null) {
        assertEquals(null, cartJson, at)
        continue
      }
      assertNotNull(cartJson, at)
      //  همان کلیدهای سرورِ واقعی
      assertEquals(keys("cartKeys"), cartJson.keys.sorted(), at)
      for (l in cartJson.getValue("lines").jsonArray) assertEquals(keys("lineKeys"), l.jsonObject.keys.sorted(), at)
      assertEquals(want.getValue("subtotalMinor").jsonPrimitive.long, cartJson.getValue("subtotalMinor").jsonPrimitive.long, at)
      st["reasons"]?.jsonObject?.forEach { (id, reason) ->
        val line = cartJson.getValue("lines").jsonArray.map { it.jsonObject }.first { it.s("productId") == id }
        assertEquals(reason.jsonPrimitive.content, line.s("reason"), at)
      }
      //  و برنامه همان را می‌خواند: سبدِ ۲۰۰ یا سبدِ درونِ ۴۰۹ (`AccountRules.cartIn`)
      val c = result.valueOrNull() ?: AccountRules.cartIn(e!!)
      assertNotNull(c, at)
      assertEquals(want.getValue("version").jsonPrimitive.long, c.version, at)
      assertEquals(expected(want.getValue("lines").jsonArray), brief(c), at)
    }
    //  بی نشست و پرچمِ خاموش: دو ۴۰۱ ِ جدا — فقط اولی «دوباره وارد شوید»؛ دومی فقط سبد را خاموش می‌کند
    val none = api.accountCart("golestan", "").errorOrNull()
    assertNotNull(none)
    val noSession = v.getValue("noSession").jsonObject
    assertEquals(noSession.getValue("status").jsonPrimitive.int, none.status)
    assertEquals(noSession.s("code"), none.code)
    assertEquals(CartSync.SESSION_REQUIRED, none.code)
    assertFalse(CartSync.doorClosed(none))
    srv.flagOn = false
    val off = api.accountCart("golestan", session).errorOrNull()
    assertNotNull(off)
    val flagOff = v.getValue("flagOff").jsonObject
    assertEquals(flagOff.getValue("status").jsonPrimitive.int, off.status)
    assertEquals(flagOff.s("code"), off.code)
    assertTrue(CartSync.doorClosed(off))
    assertNotEquals(none.code, off.code)
    //  کدِ ۴۰۹ ِ گامِ «نسخهٔ کهنه» همان که ماشین می‌شناسد
    assertTrue(v.getValue("steps").jsonArray.any { it.jsonObject["code"]?.jsonPrimitive?.content == CartSync.CART_CHANGED })
    assertEquals("accountCart", v.s("feature"))
  }
}
