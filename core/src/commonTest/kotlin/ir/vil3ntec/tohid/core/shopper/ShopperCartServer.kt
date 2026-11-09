package ir.vil3ntec.tohid.core.shopper

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 *  سرورِ جعلیِ سبدِ حسابِ مشتری (CHK-03) برای `ShopperCartSyncTest` — همان قاعده‌های `server/src/lib/store-cart.js` و ترتیبِ
 *  درهای `routes/storefront.js` (پرچم ⇒ سطل ⇒ نشست)، کوچک. `ShopperCartVectorsTest` (jvmTest) همین را با
 *  `core/test-vectors/account-cart-v1.json` روبه‌روی سرورِ واقعی نگه می‌دارد (همان بردار را `storefront-cart.test.js` می‌دواند).
 *  خطاهای ساختگی: [down] (بی اینترنت)، [failWith]×[failTimes] (۵xx یا ۴۲۹ با [retryAfter])، [flagOn] خاموش (۴۰۱ ِ درِ ناشناخته)،
 *  [raceEveryPut] (دستگاهِ دیگر پیش از هر نوشتن می‌نویسد ⇒ ۴۰۹ ِ همیشگی).
 */
class CartServer(private val slug: String = "golestan") {
  class Product(val id: String, val name: String, val unit: String, var priceMinor: Long) {
    var published = true
    var stock = "unverified"
    var maxQtyMilli: Long? = null
  }

  data class Item(val productId: String, val qtyMilli: Long, val note: String = "")

  private class Bad(val code: String) : Exception(code)

  val products = LinkedHashMap<String, Product>()
  var items: List<Item> = emptyList()
  var version = 0L
  var flagOn = true
  var down = false
  var failWith = 503
  var failTimes = 0
  var retryAfter: Int? = null
  var raceEveryPut = false
  val sessions = mutableSetOf(SESSION_A, SESSION_B)
  /** درهای زده‌شده به ترتیب (`GET /cart`، `PUT /cart`، `POST /merge`) و بدنهٔ هر یک */
  val seen = mutableListOf<String>()
  val bodies = mutableListOf<JsonObject>()

  fun product(id: String, name: String, unit: String, priceMinor: Long): Product = Product(id, name, unit, priceMinor).also { products[id] = it }

  /** دستگاهِ دیگرِ همان حساب سبد را جایگزین کرد (نسخه بالا) */
  fun otherDevice(vararg x: Pair<String, Long>) {
    items = x.map { Item(it.first, it.second) }
    version++
  }

  fun brief(): List<Pair<String, Long>> = items.map { it.productId to it.qtyMilli }

  fun handle(r: ShopperRequest): ShopperResponse {
    if (down) throw IllegalStateException("offline")
    val base = "/api/store/$slug/account/cart"
    val door = when {
      r.method == "GET" && r.path == base -> "GET /cart"
      r.method == "PUT" && r.path == base -> "PUT /cart"
      r.method == "POST" && r.path == "$base/merge" -> "POST /merge"
      else -> return err(404, "not_found")
    }
    //  پرچمِ خاموش ⇒ `next('route')` ⇒ همان ۴۰۱ ِ درِ ناشناختهٔ main، پیش از سطل و نشست
    if (!flagOn) return err(401, "unauthorized")
    if (failTimes > 0) {
      failTimes--
      return err(failWith, if (failWith == 429) "rate_limited" else "server_error", retryAfter)
    }
    if (r.headers["X-Store-Session"].orEmpty() !in sessions) return err(401, "session_required")
    val raw = r.body
    val body = if (raw.isNullOrBlank()) JsonObject(emptyMap()) else ShopperJson.parseToJsonElement(raw).jsonObject
    seen += door
    bodies += body
    return when (door) {
      "GET /cart" -> ok()
      "PUT /cart" -> put(body)
      else -> merge(body)
    }
  }

  private fun put(b: JsonObject): ShopperResponse {
    val v = (b["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it >= 0 } ?: return err(400, "bad_version")
    val next = try { itemsOf(b) } catch (e: Bad) { return err(400, e.code) }
    if (raceEveryPut) version++
    if (v != version) return err(409, "cart_changed", extra = "cart" to view())
    items = next
    version++
    return ok()
  }

  /** بیشینهٔ مقدار، نه جمع؛ یادداشتِ حساب مقدم؛ بی تغییر ⇒ همان نسخه؛ بیش از سقف ⇒ ۴۰۹ و هیچ تغییر */
  private fun merge(b: JsonObject): ShopperResponse {
    val guest = try { itemsOf(b) } catch (e: Bad) { return err(400, e.code) }
    val merged = items.toMutableList()
    var changed = false
    for (g in guest) {
      val i = merged.indexOfFirst { it.productId == g.productId }
      if (i < 0) {
        merged += g
        changed = true
        continue
      }
      val m = merged[i]
      var n = m
      if (g.qtyMilli > m.qtyMilli) n = n.copy(qtyMilli = g.qtyMilli)
      if (m.note.isEmpty() && g.note.isNotEmpty()) n = n.copy(note = g.note)
      if (n != m) {
        merged[i] = n
        changed = true
      }
    }
    if (merged.size > MAX_LINES) return err(409, "cart_full", extra = "cart" to view())
    if (changed) {
      items = merged
      version++
    }
    return ok()
  }

  private fun itemsOf(b: JsonObject): List<Item> {
    val raw = b["items"] as? JsonArray ?: throw Bad("bad_lines")
    if (raw.size > MAX_LINES) throw Bad("bad_lines")
    val out = raw.map { e ->
      val x = e as? JsonObject ?: throw Bad("bad_lines")
      val id = (x["productId"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw Bad("bad_lines")
      val q = (x["qtyMilli"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: throw Bad("bad_number")
      if (q < 1 || q > CartRules.MAX_QTY_MILLI) throw Bad("bad_number")
      Item(id, q, ((x["note"] as? JsonPrimitive)?.contentOrNull ?: "").trim().take(200))
    }
    if (out.map { it.productId }.toSet().size != out.size) throw Bad("bad_lines")
    return out
  }

  /** نمای سبد با قیمت و وضعیتِ امروز (همان کلیدهای `view` ِ سرور) */
  fun view(): JsonObject {
    var sub = 0L
    val lines = items.map { i ->
      val p = products[i.productId]?.takeIf { it.published }
      if (p == null) {
        obj(
          "productId" to str(i.productId), "qtyMilli" to num(i.qtyMilli), "note" to str(i.note), "available" to JsonPrimitive(false),
          "reason" to str("not_in_store"), "name" to JsonNull, "unit" to JsonNull, "priceMinor" to JsonNull, "lineTotalMinor" to JsonNull,
          "maxQtyMilli" to JsonNull, "overLimit" to JsonPrimitive(false), "stock" to JsonNull,
        )
      } else {
        val total = CartRules.lineTotal(i.qtyMilli, p.priceMinor)
        sub += total
        val max = p.maxQtyMilli
        obj(
          "productId" to str(i.productId), "qtyMilli" to num(i.qtyMilli), "note" to str(i.note), "available" to JsonPrimitive(true),
          "reason" to str(""), "name" to str(p.name), "unit" to str(p.unit), "priceMinor" to num(p.priceMinor), "lineTotalMinor" to num(total),
          "maxQtyMilli" to (if (max != null) num(max) else JsonNull), "overLimit" to JsonPrimitive(max != null && i.qtyMilli > max),
          "stock" to obj("status" to str(p.stock), "checkedAt" to JsonNull),
        )
      }
    }
    return obj(
      "version" to num(version), "updatedAt" to (if (version > 0) num(1) else JsonNull), "maxLines" to num(MAX_LINES.toLong()),
      "lines" to JsonArray(lines), "subtotalMinor" to num(sub),
    )
  }

  private fun ok(): ShopperResponse = ShopperResponse(200, ShopperJson.encodeToString(JsonObject.serializer(), obj("cart" to view())))

  private fun err(status: Int, code: String, retryAfter: Int? = null, extra: Pair<String, JsonElement>? = null): ShopperResponse {
    val e = obj("code" to str(code), "message" to str("x"), *listOfNotNull(extra).toTypedArray())
    val headers = if (retryAfter != null) mapOf("Retry-After" to retryAfter.toString()) else emptyMap()
    return ShopperResponse(status, ShopperJson.encodeToString(JsonObject.serializer(), obj("error" to e)), headers)
  }

  private fun obj(vararg kv: Pair<String, JsonElement>): JsonObject = JsonObject(kv.toMap())

  private fun str(v: String): JsonPrimitive = JsonPrimitive(v)

  private fun num(v: Long): JsonPrimitive = JsonPrimitive(v)

  companion object {
    const val SESSION_A = "sc1.A"
    const val SESSION_B = "sc1.B"
    const val MAX_LINES = 100
  }
}
