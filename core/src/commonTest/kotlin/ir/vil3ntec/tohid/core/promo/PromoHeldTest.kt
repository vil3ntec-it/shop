package ir.vil3ntec.tohid.core.promo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PRM-02 — آزادِ دستیِ کدِ نگه‌داشته: همان پاسخ‌های قفل‌شدهٔ `server/test/promo-held.test.js` خوانده می‌شود و فقط
 *  فاکتورِ لغوشدهٔ دفترِ همین دستگاه دکمهٔ «آزاد کن» دارد.
 */
class PromoHeldTest {

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val page = json(
    """{"held":[{"saleId":"S2","code":"EID5","promoId":"r1","at":2000},{"saleId":"S2","code":"NOROOZ","promoId":"r2","at":2000},
      {"saleId":"S1","code":"EID5","promoId":"r1","at":1000},{"saleId":"S3","code":"EID5","promoId":"r1","at":3000}],"more":false}""",
  )

  @Test
  fun `خواندنِ فهرست — همان شکلِ سرور، بدشکل و شناسهٔ نادرست کنار`() {
    val p = PromoHeld.parse(page)
    assertEquals(4, p.rows.size)
    assertEquals(PromoHeld.Row("S2", "EID5", "r1", 2000), p.rows[0])
    assertFalse(p.more)
    assertTrue(PromoHeld.parse(json("""{"held":[],"more":true}""")).more)
    //  سرورِ کهنه یا پاسخِ دیگر ⇒ خالی، نه خطا
    assertEquals(PromoHeld.Page(emptyList(), false), PromoHeld.parse(json("""{"ok":true}""")))
    assertEquals(PromoHeld.Page(emptyList(), false), PromoHeld.parse(null))
    val odd = PromoHeld.parse(json(
      """{"held":[{"saleId":"../x","code":"A"},{"saleId":7},"x",{"saleId":"OK1","code":5,"at":"9"}],"more":"true"}""",
    ))
    assertEquals(listOf(PromoHeld.Row("OK1", "", "", 0)), odd.rows)
    assertFalse(odd.more, "more ِ رشته‌ای «بله» نیست")
  }

  @Test
  fun `گروه‌بندی — لغوشده اول و فقط آن آزاد کردنی؛ زنده و نارسیده بی دکمه`() {
    val status = mapOf("S1" to "cancelled", "S2" to "completed")
    val g = PromoHeld.groups(PromoHeld.parse(page).rows) { status[it] }
    assertEquals(listOf("S1", "S2", "S3"), g.map { it.saleId })
    assertEquals(PromoHeld.Sale.CANCELLED, g[0].sale)
    assertTrue(g[0].releasable)
    assertEquals(listOf("EID5", "NOROOZ"), g[1].codes)
    assertEquals(PromoHeld.Sale.ACTIVE, g[1].sale)
    assertFalse(g[1].releasable)
    assertEquals(PromoHeld.Sale.MISSING, g[2].sale)
    assertFalse(g[2].releasable)
    //  دو فاکتورِ لغوشده ⇒ تازه‌ترین اول
    val both = PromoHeld.groups(PromoHeld.parse(page).rows) { if (it == "S2") "completed" else "cancelled" }
    assertEquals(listOf("S3", "S1", "S2"), both.map { it.saleId })
  }

  @Test
  fun `راه به صفحه فقط با شمارشِ روشن و اجازهٔ لغو`() {
    assertTrue(PromoHeld.shown(redeemOn = true, canCancel = true))
    assertFalse(PromoHeld.shown(redeemOn = false, canCancel = true))
    assertFalse(PromoHeld.shown(redeemOn = true, canCancel = false))
  }

  @Test
  fun `بدنهٔ آزاد کردن — فقط saleId، شناسهٔ نادرست هرگز`() {
    assertEquals(json("""{"saleId":"01JHELD0000000000000000001"}"""), PromoHeld.releaseBody("01JHELD0000000000000000001"))
    assertFailsWith<IllegalArgumentException> { PromoHeld.releaseBody("a/b") }
    assertFailsWith<IllegalArgumentException> { PromoHeld.releaseBody("") }
    assertEquals("/shop/promos/redeem/release", PromoHeld.RELEASE_PATH)
    assertEquals("/shop/promos/redemptions", PromoHeld.PATH)
  }

  @Test
  fun `پاسخ‌های فهرست و آزاد کردن`() {
    assertEquals(PromoHeld.Access.DENIED, PromoHeld.access(403, "release_not_allowed"))
    assertEquals(PromoHeld.Access.OFF, PromoHeld.access(403, "promo_uses_disabled"))
    assertEquals(PromoHeld.Access.OFF, PromoHeld.access(404, "not_found"))
    assertNull(PromoHeld.access(0, ""))
    assertNull(PromoHeld.access(503, ""))

    assertEquals(1, PromoHeld.releasedOf(json("""{"released":1}""")))
    assertEquals(0, PromoHeld.releasedOf(json("""{"released":"1"}""")))
    assertEquals(0, PromoHeld.releasedOf(null))
    assertEquals(PromoHeld.Done.RELEASED, PromoHeld.releaseOutcome(true, 2, 200, ""))
    assertEquals(PromoHeld.Done.NOTHING, PromoHeld.releaseOutcome(true, 0, 200, ""))
    assertEquals(PromoHeld.Done.OFF, PromoHeld.releaseOutcome(false, 0, 403, "promo_uses_disabled"))
    assertEquals(PromoHeld.Done.DENIED, PromoHeld.releaseOutcome(false, 0, 403, "release_not_allowed"))
    assertEquals(PromoHeld.Done.DENIED, PromoHeld.releaseOutcome(false, 0, 400, "invalid_sale_id"))
    assertEquals(PromoHeld.Done.FAILED, PromoHeld.releaseOutcome(false, 0, 0, ""))
    assertEquals(PromoHeld.Done.FAILED, PromoHeld.releaseOutcome(false, 0, 429, "rate_limited"))
  }
}
