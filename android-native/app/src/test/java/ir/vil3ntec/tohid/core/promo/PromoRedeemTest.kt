package ir.vil3ntec.tohid.core.promo

import ir.vil3ntec.tohid.core.promo.PromoRedeem.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  PRM-02 — کدام کد پیش از ثبت از سرور گرفته می‌شود و پاسخِ سرور چه معنایی دارد
 *  (`core/.../promo/PromoRedeem.kt`؛ سرور: `server/src/routes/shop-promos.js` `/redeem`).
 *  وضعیتِ کدها از خودِ `PromoCore.evaluate` می‌آید، نه دست‌ساز.
 */
class PromoRedeemTest {

  private val cart = PromoCore.Cart(
    lines = listOf(PromoCore.Line(key = "a", productId = "A", qtyMilli = 2000, unitMinor = 10_000)),
  )
  private val capped = PromoCore.Rule(id = "c", name = "سقف‌دار", kind = "percent", bp = 1000, code = "CAP10", maxUses = 5, stack = "combine")
  private val free = PromoCore.Rule(id = "f", name = "بی سقف", kind = "percent", bp = 500, code = "FREE5", stack = "combine")
  private val auto = PromoCore.Rule(id = "x", name = "خودکار", kind = "percent", bp = 300, stack = "combine")

  private fun codes(rules: List<PromoCore.Rule>, entered: List<String>) =
    PromoCore.evaluate(cart.copy(coupons = entered), rules).codes

  @Test
  fun `فقط کدِ اعمال‌شده با سقف — نه کدِ بی سقف، نه تخفیفِ خودکار`() {
    val rules = listOf(capped, free, auto)
    assertEquals(listOf("CAP10"), PromoRedeem.capped(rules, codes(rules, listOf("free5", "cap-10"))))
    assertEquals(emptyList<String>(), PromoRedeem.capped(rules, codes(rules, listOf("FREE5"))))
    assertEquals(emptyList<String>(), PromoRedeem.capped(rules, codes(rules, emptyList())))
  }

  @Test
  fun `کدِ پُر، ناشناخته یا بی‌اثر به سرور نمی‌رود؛ دو شکلِ یک کد یک بار`() {
    val full = capped.copy(uses = 5)
    assertEquals("used_up", codes(listOf(full), listOf("CAP10")).single().status)
    assertEquals(emptyList<String>(), PromoRedeem.capped(listOf(full), codes(listOf(full), listOf("CAP10"))))
    assertEquals(emptyList<String>(), PromoRedeem.capped(listOf(capped), codes(listOf(capped), listOf("NOPE1"))))
    //  همان کد دو بار در وضعیت‌ها (ورودیِ دست‌کاری‌شده) ⇒ یک بار
    val twice = listOf(PromoCore.CodeStatus("cap10", "applied"), PromoCore.CodeStatus("CAP 10", "applied"))
    assertEquals(listOf("CAP10"), PromoRedeem.capped(listOf(capped), twice))
    //  قاعدهٔ خاموش هرگز «سقف‌دار» شمرده نمی‌شود
    assertEquals(emptyList<String>(), PromoRedeem.capped(listOf(capped.copy(active = false)), twice))
  }

  @Test
  fun `پاسخِ سرور ⇒ ثبت یا نه`() {
    assertEquals(Decision.GO, PromoRedeem.decide(true, 200, ""))
    //  پرچمِ شمارش در سرور خاموش شد ⇒ همان رفتارِ پیش از PRM-02
    assertEquals(Decision.GO, PromoRedeem.decide(false, 403, "promo_uses_disabled"))
    assertEquals(Decision.REJECTED, PromoRedeem.decide(false, 409, "coupon_rejected"))
    assertEquals(Decision.REJECTED, PromoRedeem.decide(false, 400, "invalid_codes"))
    //  بی پاسخ، قطع، خطای سرور، نرخ و نشستِ تمام‌شده ⇒ کدِ سقف‌دار پذیرفته نمی‌شود
    for ((status, code) in listOf(0 to "offline", 0 to "unreachable", 500 to "internal", 503 to "x", 429 to "rate_limited", 401 to "session_expired", 403 to "permission_denied", 409 to "other")) {
      assertEquals("$status $code", Decision.UNREACHABLE, PromoRedeem.decide(false, status, code))
    }
  }
}
