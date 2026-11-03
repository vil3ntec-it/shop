package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.core.promo.PromoRedeem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-02 — ثبتِ صندوقِ گوشیِ نسخهٔ ۳ (`CheckoutRoute`) با کدِ سقف‌دار، همان زنجیرهٔ صندوقِ کامپیوتر: قفلِ ثبت با کلیدِ
 *  فاکتور (`SalePost.run`، زیرِ `CartStore.post`) ⇐ دروازهٔ کد (`TillSale.redeem`، همان که `PromoLive.redeemGate` ِ هر
 *  صندوق صدا می‌زند) ⇐ `TillSale.record`. سرور با تابعِ ساختگی جای `POST /api/shop/promos/redeem` را می‌گیرد؛ سیم‌کشیِ
 *  صفحه در `PhoneTillWiringTest`.
 *
 *  حالِ صندوق کهنه است (۲ از ۵ مصرف)، پس کد در سبد «اعمال‌شده» است و تخفیف را می‌دهد؛ فقط سرور می‌داند ظرفیت پر شده.
 */
class TillSaleRedeemTest {
  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-03"
  private val now = 1_800_000_000_000L
  private val key = "sale-key-1"

  private val base = ShopData(
    products = listOf(Product(id = "p1", name = "روغن", salePrice = 1000.0, purchasePrice = 700.0, unit = "عدد")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 50.0)),
  )
  private val lines = listOf(SalesEngine.CartLine("p1", 2.0))
  private val capped = PromoCore.Rule(
    id = "c", name = "کدِ عید", kind = "percent", bp = 1000, code = "CAP10", maxUses = 5, uses = 2, stack = "combine", channels = listOf("pos"),
  )
  private val snap = PromoEngine.Snapshot(enabled = true, rules = listOf(capped), stamp = true, redeem = true, account = "a@s")
  private val sale = SalesEngine.Checkout(seller = "رضا")
  private val full = "کدِ «کدِ عید» تمام شده است"

  private fun promo(s: PromoEngine.Snapshot = snap, coupons: List<String> = listOf("cap10")) =
    PromoEngine.evaluate(base, lines, s, coupons, today, 600)

  /** سرورِ ساختگی: هر پرسش با کلید و کدهایش نوشته می‌شود */
  private class Server(val reply: TillSale.Reply) {
    val asked = ArrayList<Pair<String, List<String>>>()
  }

  private class Run(val outcome: SalePost.Outcome, val recorded: Int, val saved: ShopData?)

  /** همان کاری که «ثبت» ِ برگهٔ پرداختِ گوشی می‌کند — با دروازه یا (برای سنجش) بی آن */
  private fun checkout(promo: PromoEngine.Outcome?, s: PromoEngine.Snapshot, online: Boolean, server: Server?, gated: Boolean = true): Run = runBlocking {
    var recorded = 0
    var saved: ShopData? = null
    val gate: (suspend (String) -> String?)? = if (!gated) null else { k ->
      TillSale.redeem(s.redeem, s.rules, promo, online = { online }) { codes ->
        val srv = server ?: error("سرور نباید پرسیده شود")
        srv.asked += k to codes
        srv.reply
      }.message
    }
    val out = SalePost().run(
      key,
      record = { k ->
        recorded++
        TillSale.record(base, PromoEngine.priced(lines, promo), sale, null, promo, null, today, now, newId, saleId = k)
      },
      save = { d -> saved = d; true },
      before = gate,
      done = {},
    )
    Run(out, recorded, saved)
  }

  @Test
  fun `کدِ سقف‌دارِ پرشده در پرداختِ گوشی هیچ فروش و هیچ تخفیفی ثبت نمی‌کند و پیامِ سرور را می‌گوید`() {
    val p = promo()!!
    assertEquals("applied", p.codes.single().status)
    assertEquals(200.0, p.discount, 0.0)
    //  همان چیزی که پیش از این رفع رخ می‌داد: بی دروازه همین سبد با تخفیفِ کدِ پرشده ثبت می‌شد
    val bypass = checkout(p, snap, online = true, server = null, gated = false)
    val leaked = (bypass.outcome as SalePost.Outcome.Done).ok
    assertEquals(1800.0, leaked.data.sales.single { it.id == leaked.saleId }.finalTotal, 0.0)

    val server = Server(TillSale.Reply(ok = false, status = 409, code = "coupon_rejected", message = full))
    val r = checkout(p, snap, online = true, server = server)
    assertEquals(SalePost.Outcome.Refused(full), r.outcome)
    assertEquals("فاکتور ساخته نشد", 0, r.recorded)
    assertNull("دفتر نوشته نشد", r.saved)
    //  همان کلیدِ فاکتور و فقط کدِ سقف‌دارِ نرمال‌شده
    assertEquals(listOf(key to listOf("CAP10")), server.asked)
    //  پیامِ نیامده ⇒ همان «فقط با اینترنت»، نه ثبت
    val bare = checkout(p, snap, online = true, server = Server(TillSale.Reply(ok = false, status = 409, code = "coupon_rejected")))
    assertEquals(SalePost.Outcome.Refused(StrPos.couponNeedsOnline("CAP10")), bare.outcome)
    assertEquals(0, bare.recorded)
  }

  @Test
  fun `سرور گرفت ⇒ همان فاکتور با تخفیف و کد ثبت می‌شود و شناسه‌اش همان کلیدی است که سرور شمرد`() {
    val p = promo()!!
    val server = Server(TillSale.Reply(ok = true, status = 200))
    val r = checkout(p, snap, online = true, server = server)
    val ok = (r.outcome as SalePost.Outcome.Done).ok
    val s = ok.data.sales.single { it.id == ok.saleId }
    assertEquals(1800.0, s.finalTotal, 0.0)
    assertEquals(listOf("CAP10"), s.coupons)
    assertEquals(20_000L, s.promoDiscountMinor)
    //  لغوِ همین فاکتور با همین شناسه آزادش می‌کند (`/redeem/release`)
    assertEquals(key, s.id)
    assertEquals(listOf(key to listOf("CAP10")), server.asked)
    assertEquals(1, r.recorded)
    assertTrue(r.saved != null)
    //  همین فاکتور دوباره (قطعِ شبکه پس از گرفتن) ⇒ سرور «already» می‌گوید و ثبت می‌رود
    assertEquals(TillSale.Redeem(null, PromoRedeem.Decision.GO), runBlocking { TillSale.redeem(true, snap.rules, p, { true }) { server.reply } })
    //  شمارش همین حالا در سرور خاموش شد ⇒ همان ثبتِ پیش از PRM-02
    val off = checkout(p, snap, online = true, server = Server(TillSale.Reply(ok = false, status = 403, code = "promo_uses_disabled")))
    assertTrue(off.outcome is SalePost.Outcome.Done)
  }

  @Test
  fun `بی اینترنت یا بی پاسخِ سرور کدِ سقف‌دار پذیرفته نمی‌شود و هیچ فروشی بی شمارش نمی‌نشیند`() {
    val p = promo()!!
    val need = StrPos.couponNeedsOnline("CAP10")
    //  آفلاین: سرور پرسیده نمی‌شود و ثبت نمی‌شود — همان پیامِ کامپیوتر
    val offline = checkout(p, snap, online = false, server = null)
    assertEquals(SalePost.Outcome.Refused(need), offline.outcome)
    assertEquals(0, offline.recorded)
    assertNull(offline.saved)
    //  وصل ولی بی پاسخ، خطای سرور، نشستِ تمام‌شده یا نرخ ⇒ همان؛ «دوباره» با همین کلید بی‌خطر است
    for ((status, code) in listOf(0 to "offline", 503 to "internal", 401 to "session_expired", 429 to "rate_limited")) {
      val r = checkout(p, snap, online = true, server = Server(TillSale.Reply(ok = false, status = status, code = code, message = "x")))
      assertEquals("$status $code", SalePost.Outcome.Refused(need), r.outcome)
      assertEquals(0, r.recorded)
    }
  }

  @Test
  fun `بی کدِ سقف‌دار یا با شمارشِ خاموش هیچ پرسشی نیست و ثبت همان امروز است`() {
    val gate = { s: PromoEngine.Snapshot, p: PromoEngine.Outcome? ->
      runBlocking { TillSale.redeem(s.redeem, s.rules, p, { error("وصل بودن پرسیده نشود") }) { error("سرور پرسیده نشود") } }
    }
    //  پرچمِ `redeem` خاموش (D-05: کد مثلِ پیش آفلاین هم کار می‌کند)
    val noRedeem = snap.copy(redeem = false)
    assertEquals(TillSale.Redeem(null), gate(noRedeem, promo(noRedeem)))
    assertTrue(checkout(promo(noRedeem), noRedeem, online = false, server = null).outcome is SalePost.Outcome.Done)
    //  کدِ بی سقف و تخفیفِ خودکار
    val free = snap.copy(rules = listOf(capped.copy(maxUses = 0)))
    assertEquals(TillSale.Redeem(null), gate(free, promo(free)))
    val auto = snap.copy(rules = listOf(capped.copy(code = "")))
    assertEquals(TillSale.Redeem(null), gate(auto, promo(auto, emptyList())))
    //  کدی زده نشده، یا کلیدِ دکان خاموش (بی نتیجه)
    assertEquals(TillSale.Redeem(null), gate(snap, promo(snap, emptyList())))
    assertEquals(TillSale.Redeem(null), gate(snap, null))
  }
}
