package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** خانهٔ گوشی v3: کارتِ کششی (r6)، کاشی‌ها و هشدارها (r7)، بازه‌های آمار (r8). */
class PhoneHomeModelTest {

  private val cfg = PullCard.Config(threshold = 120f, flingMin = 40f, flingVelocity = 1500f)

  private fun pulled(px: Float, atTop: Boolean = true): PullCard.State {
    var s = PullCard.touchDown(PullCard.State(), atTop)
    //  چند گامِ کوچکِ انگشت
    repeat(10) { s = PullCard.pullDown(s, px / 10) }
    return s
  }

  @Test
  fun `کشیدنِ کوتاه و آرام برمی‌گردد`() {
    val s = PullCard.release(pulled(100f), 0f, cfg)          // ۵۰ پیکسل با مقاومت
    assertFalse(s.shown); assertEquals(0f, s.pull)
  }

  @Test
  fun `کشیدن تا آستانه کارت را می‌آورد`() {
    val s = PullCard.release(pulled(300f), 0f, cfg)
    assertTrue(s.shown)
    assertEquals(1f, PullCard.progress(s, 400f))
  }

  @Test
  fun `کششِ کوتاهِ تند هم کارت را می‌آورد`() {
    assertTrue(PullCard.release(pulled(100f), 2400f, cfg).shown)
    //  ولی تکانِ خیلی کوچک، حتی تند، نه
    assertFalse(PullCard.release(pulled(40f), 4000f, cfg).shown)
  }

  @Test
  fun `اسکرولِ معمولی کارت را نمی‌آورد — لمس از وسطِ صفحه شروع شده بود`() {
    val s = pulled(600f, atTop = false)
    assertEquals(0f, s.pull)
    assertFalse(PullCard.release(s, 5000f, cfg).shown)
  }

  @Test
  fun `بالا بردنِ انگشت پیش از رها کردن کشش را پس می‌گیرد`() {
    val s = pulled(300f)
    val (back, used) = PullCard.pullUp(s, 300f)
    assertEquals(0f, back.pull)
    assertEquals(300f, used)
    assertFalse(PullCard.release(back, 0f, cfg).shown)
  }

  @Test
  fun `نمایان — کششِ دوباره بی‌اثر و لمسِ بیرون یا هل به بالا پنهانش می‌کند`() {
    val shown = PullCard.release(pulled(300f), 0f, cfg)
    assertEquals(shown, PullCard.pullDown(shown, 50f))
    assertEquals(shown, PullCard.touchDown(shown, atTop = true))
    assertFalse(PullCard.dismiss(shown).shown)
    assertEquals(0f, PullCard.progress(PullCard.dismiss(shown), 400f))
  }

  @Test
  fun `کاشی‌ها همیشه هشت تا و هشتمی بیشتر`() {
    for (manage in listOf(true, false)) for (orders in listOf(true, false)) {
      val t = HomeSections.tiles(manage, orders)
      assertEquals(8, t.size)
      assertEquals("more", t.last().route)
      assertEquals(manage, t.any { it.route == "team" })
      assertEquals(orders, t.any { it.route == "orders" })
      assertEquals(t.map { it.route }.distinct(), t.map { it.route })
    }
  }

  @Test
  fun `هشدار — هیچ چیز ⇒ خالی؛ سفارش فقط وقتی زنده است`() {
    assertTrue(HomeAlerts.build(HomeAlerts.Input()).isEmpty())
    assertTrue(HomeAlerts.build(HomeAlerts.Input(ordersLive = false, newOrders = 3)).isEmpty())
    val a = HomeAlerts.build(HomeAlerts.Input(ordersLive = true, newOrders = 3))
    assertEquals(HomeAlerts.Kind.ORDERS, a.single().kind)
    assertEquals("orders", a.single().route)
  }

  @Test
  fun `هشدار — ترتیبِ فوریت و مقصدِ هر کدام`() {
    val a = HomeAlerts.build(
      HomeAlerts.Input(
        ordersLive = true, newOrders = 2, subscriptionDaysLeft = 2, sync = HomeAlerts.Sync.ERROR,
        outOfStock = 1, lowStock = 4, overdueDebtors = 3, overdueTotal = "1,200 ؋", expiring = 2,
      ),
    )
    assertEquals(
      listOf(HomeAlerts.Kind.ORDERS, HomeAlerts.Kind.SUBSCRIPTION, HomeAlerts.Kind.SYNC, HomeAlerts.Kind.STOCK,
        HomeAlerts.Kind.DEBTS, HomeAlerts.Kind.EXPIRY),
      a.map { it.kind },
    )
    assertEquals(listOf("orders", "vip", "devices", "products", "debtors", "warehouse"), a.map { it.route })
    assertTrue(a[1].urgent)
  }

  @Test
  fun `اشتراکِ قفل‌شده بر روزهای مانده مقدم است`() {
    val a = HomeAlerts.build(HomeAlerts.Input(subscriptionLocked = true, subscriptionDaysLeft = 5)).single()
    assertEquals("اشتراک تمام شده", a.title)
  }

  @Test
  fun `بازهٔ هفته هفت روزِ پیاپی تا امروز است`() {
    val b = StatsBuckets.of(StatsBuckets.Period.WEEK, "2026-10-19", emptyList())
    assertEquals(7, b.size)
    assertEquals("2026-10-13", b.first().from)
    assertEquals("2026-10-19", b.last().to)
    //  ۱۹ اکتبرِ ۲۰۲۶ دوشنبه است
    assertEquals("دوشنبه", b.last().label)
  }

  @Test
  fun `بازهٔ ماه شش ماهِ خورشیدی است و ماهِ جاری تا امروز`() {
    val names = listOf("حمل", "ثور", "جوزا", "سرطان", "اسد", "سنبله", "میزان", "عقرب", "قوس", "جدی", "دلو", "حوت")
    val b = StatsBuckets.of(StatsBuckets.Period.MONTH, "2026-10-19", names)
    assertEquals(6, b.size)
    //  ۱۹ اکتبر ۲۰۲۶ = ۲۷ میزان ۱۴۰۵
    assertEquals("میزان", b.last().label)
    assertEquals("2026-10-19", b.last().to)
    assertEquals("ثور", b.first().label)
    //  پیوسته: پایانِ هر ماه روزِ پیش از آغازِ بعدی
    for (i in 1 until b.size) {
      assertEquals(ir.vil3ntec.tohid.core.calendar.CivilDays.addDays(b[i - 1].to, 1), b[i].from)
    }
  }

  @Test
  fun `بازهٔ سال پنج سالِ خورشیدی است`() {
    val b = StatsBuckets.of(StatsBuckets.Period.YEAR, "2026-10-19", emptyList())
    assertEquals(listOf("1401", "1402", "1403", "1404", "1405"), b.map { it.label })
    assertEquals("2026-03-21", b.last().from)
  }

  @Test
  fun `محور گرد می‌شود و برچسبش کوتاه است`() {
    assertEquals(40_000.0, StatsBuckets.axisMax(36_500.0), 0.0)
    assertEquals(4.0, StatsBuckets.axisMax(0.0), 0.0)
    assertTrue(StatsBuckets.axisMax(12_345_678.0) >= 12_345_678.0)
    assertEquals("10K", StatsBuckets.compact(10_000.0))
    assertEquals("1.5M", StatsBuckets.compact(1_500_000.0))
    assertEquals("950", StatsBuckets.compact(950.0))
  }
}
