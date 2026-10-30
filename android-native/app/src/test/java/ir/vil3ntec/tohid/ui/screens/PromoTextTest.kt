package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** فرم و متن‌های صفحهٔ «تخفیف‌ها» — رفت‌وبرگشتِ فرم و داوریِ `PromoCore.validate` */
class PromoTextTest {

  private val base = PromoCore.Rule(id = "r1", channels = listOf("pos"))

  private fun roundTrip(r: PromoCore.Rule) {
    assertEquals(PromoCore.validate(r), emptyList<String>())
    assertEquals(r, PromoForm.of(r).toRule(base.copy(id = r.id, channels = r.channels, uses = r.uses, maxUses = r.maxUses)))
  }

  @Test fun `فرم رفت‌وبرگشت دارد — هر هفت نوع`() {
    roundTrip(base.copy(name = "ده", kind = "percent", bp = 1000,
      scope = PromoCore.Scope(categories = listOf("نوشیدنی"), exProducts = listOf("c")),
      window = PromoCore.Window(days = listOf(5L, 6L), start = 1320, end = 120, from = "2026-10-01", to = "2026-10-31")))
    roundTrip(base.copy(name = "پانزده", kind = "amount", amountMinor = 1500, stack = "combine", priority = 3))
    roundTrip(base.copy(name = "ویژه", kind = "price", prices = listOf(PromoCore.Price("tea", 25000)), code = "VIP10"))
    roundTrip(base.copy(name = "پله", kind = "tiers", tiers = listOf(PromoCore.Tier(3000, 500), PromoCore.Tier(6000, 1000))))
    roundTrip(base.copy(name = "آستانه", kind = "threshold", tiers = listOf(PromoCore.Tier(min = 50000, amountMinor = 5000), PromoCore.Tier(min = 100000, amountMinor = 12000))))
    roundTrip(base.copy(name = "دو و یک", kind = "bxgy", buy = 2, get = 1, bp = 10000, scope = PromoCore.Scope(products = listOf("tea"))))
    roundTrip(base.copy(name = "بسته", kind = "bundle", qty = 3, priceMinor = 25000, active = false, window = PromoCore.Window(start = 1200, end = 1440)))
  }

  @Test fun `فرمِ نادرست را validate می‌گیرد`() {
    val f = PromoForm(name = "زیاد", kind = "percent", pct = "150")
    assertEquals(listOf("bp"), PromoCore.validate(f.toRule(base)))
    assertEquals("درصد باید بیش از ۰ و حداکثر ۱۰۰ باشد", PromoText.errors(PromoCore.validate(f.toRule(base))))
    assertEquals(listOf("name"), PromoCore.validate(PromoForm(name = " ").toRule(base)))
    assertEquals(listOf("window"), PromoCore.validate(PromoForm(name = "n", start = "10:00").toRule(base)))
    assertEquals(listOf("window"), PromoCore.validate(PromoForm(name = "n", start = "25:00", end = "26:00").toRule(base)))
    assertEquals(listOf("tiers"), PromoCore.validate(PromoForm(name = "n", kind = "tiers", tiers = listOf("5" to "10", "3" to "20")).toRule(base)))
    assertEquals(listOf("amount"), PromoCore.validate(PromoForm(name = "n", kind = "amount", amount = "12.5").toRule(base)))
    assertEquals(listOf("prices"), PromoCore.validate(PromoForm(name = "n", kind = "price").toRule(base)))
  }

  @Test fun `PRM-02 — سقفِ مصرف فقط با پرچمِ redeem و فقط برای کد؛ بی پرچم همان سقفِ پایه`() {
    val coded = PromoCore.Rule(id = "c", name = "کد", kind = "percent", bp = 1000, code = "CAP10", maxUses = 3, uses = 2, channels = listOf("pos"))
    //  بی پرچم: فرم سقف را نمی‌شناسد و دست نمی‌زند (رفتارِ پیشین)
    assertEquals(null, PromoForm.of(coded).maxUses)
    assertEquals(3L, PromoForm.of(coded).copy(pct = "15").toRule(coded).maxUses)
    //  با پرچم: نشان داده و نوشته می‌شود؛ خالی = بی سقف؛ uses ِ سرور دست نمی‌خورد
    assertEquals("3", PromoForm.of(coded, uses = true).maxUses)
    assertEquals(25L, PromoForm.of(coded, uses = true).copy(maxUses = "۲۵").toRule(coded).maxUses)
    assertEquals(0L, PromoForm.of(coded, uses = true).copy(maxUses = " ").toRule(coded).maxUses)
    assertEquals(2L, PromoForm.of(coded, uses = true).copy(maxUses = "25").toRule(coded).uses)
    assertEquals("", PromoForm.of(coded.copy(maxUses = 0), uses = true).maxUses)
    //  بی کد (خودکار) سقف نمی‌گیرد — سرور فقط کد را می‌شمارد
    assertEquals(0L, PromoForm.of(coded, uses = true).copy(code = "", maxUses = "25").toRule(coded).maxUses)
    //  عددِ نادرست ⇒ validate («uses»)، نه بی‌صدا صفر
    for (bad in listOf("2.5", "-1", "ده")) {
      val r = PromoForm.of(coded, uses = true).copy(maxUses = bad).toRule(coded)
      assertTrue(bad, "uses" in PromoCore.validate(r))
    }
  }

  @Test fun `دقیقه از متن، با ارقامِ فارسی`() {
    assertEquals(1320L, PromoForm.minutes("22:00"))
    assertEquals(150L, PromoForm.minutes("۰۲:۳۰"))
    assertEquals(-1L, PromoForm.minutes(""))
    assertEquals(-2L, PromoForm.minutes("25:00"))
    assertEquals(-2L, PromoForm.minutes("ده"))
    assertEquals("22:00", PromoText.hm(1320))
  }

  @Test fun `متنِ قاعده، وضعیت و فیلتر`() {
    val d = ShopData(products = listOf(Product(id = "tea", name = "چای")))
    val bxgy = base.copy(name = "چای", kind = "bxgy", buy = 2, get = 1, bp = 10000, scope = PromoCore.Scope(products = listOf("tea")))
    assertEquals("بخر 2 بگیر 1 رایگان", PromoText.valueText(bxgy))
    assertEquals("چای", PromoText.scopeText(bxgy, d))
    assertEquals("همیشه", PromoText.windowText(bxgy))
    assertEquals("12.5٪", PromoText.valueText(base.copy(kind = "percent", bp = 1250)))
    val ended = bxgy.copy(id = "r2", name = "کهنه", window = PromoCore.Window(to = "2026-10-01"))
    val later = bxgy.copy(id = "r3", name = "بعدی", code = "EID5", window = PromoCore.Window(from = "2026-11-01"))
    assertEquals(PromoText.Status.ENDED, PromoText.status(ended, "2026-10-19"))
    assertEquals(PromoText.Status.SCHEDULED, PromoText.status(later, "2026-10-19"))
    assertEquals(PromoText.Status.OFF, PromoText.status(bxgy.copy(active = false), "2026-10-19"))
    val all = listOf(bxgy, ended, later)
    assertEquals(listOf("r1"), PromoText.filter(all, "", PromoText.Tab.ACTIVE, "2026-10-19").map { it.id })
    assertEquals(listOf("r2", "r3"), PromoText.filter(all, "", PromoText.Tab.OFF, "2026-10-19").map { it.id })
    assertEquals(listOf("r3"), PromoText.filter(all, "eid", PromoText.Tab.ALL, "2026-10-19").map { it.id })
    assertTrue(PromoText.windowText(later).contains("2026-11-01"))
  }

  @Test fun `N2 — ویرایش کانالِ خالی و کالاهای دیگرِ قیمتِ ویژه را دست نمی‌زند`() {
    //  کانالِ خالی یعنی «همه»؛ ویرایش آن را «فقط صندوق» نمی‌کند
    val all = base.copy(name = "همه‌جا", kind = "percent", bp = 1000, channels = emptyList())
    assertEquals(emptyList<String>(), PromoForm.of(all).copy(pct = "15").toRule(all).channels)
    //  قیمتِ ویژهٔ سه‌کالایی: فرم فقط نخستین را دارد؛ بقیه و محدودهٔ کالا همان می‌مانند
    val multi = base.copy(
      name = "ویژه", kind = "price", scope = PromoCore.Scope(products = listOf("x")),
      prices = listOf(PromoCore.Price("tea", 25000), PromoCore.Price("rice", 9000), PromoCore.Price("oil", 40000)),
    )
    assertEquals(multi, PromoForm.of(multi).toRule(multi))
    val edited = PromoForm.of(multi).copy(priceUnit = "240").toRule(multi)
    assertEquals(listOf(PromoCore.Price("tea", 24000), PromoCore.Price("rice", 9000), PromoCore.Price("oil", 40000)), edited.prices)
    //  کالای نخست به یکی از بقیه عوض شد ⇒ تکراری نمی‌شود
    val swapped = PromoForm.of(multi).copy(priceProduct = "oil", priceUnit = "390").toRule(multi)
    assertEquals(listOf(PromoCore.Price("oil", 39000), PromoCore.Price("rice", 9000)), swapped.prices)
    //  قاعدهٔ تازه همان کانالی را دارد که صفحه برایش گذاشت
    assertEquals(listOf("pos"), PromoForm(name = "n").toRule(base).channels)
  }
}
