package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  خانهٔ گوشی v3: کادرِ پنهانِ کششی (HOME-V2) و هشدارها. (کاشی‌های r7 و بازه‌های آمارِ r8 بی‌راه بودند و با آزمونشان
 *  برداشته شدند، `claude/e-rows-39m`؛ درهای خانه: `NavLayoutTest`، `ModuleRegistryTest`، `ScenarioS96Test`.)
 */
class PhoneHomeModelTest {

  private val cfg = PullCard.Config(open = 400f, threshold = 120f, flingMin = 40f, flingVelocity = 1500f)

  private fun pulled(px: Float, atTop: Boolean = true): PullCard.State {
    var s = PullCard.touchDown(PullCard.State(), atTop)
    //  چند گامِ کوچکِ انگشت
    repeat(10) { s = PullCard.pullDown(s, px / 10, cfg) }
    return s
  }

  @Test
  fun `لاستیک — مقاومت با فاصله بیشتر می‌شود و از سقف نمی‌گذرد`() {
    val a = PullCard.stretch(100f, cfg)
    val b = PullCard.stretch(200f, cfg)
    assertTrue(a < 100f * PullCard.RUBBER + 0.01f)
    assertTrue(b > a)
    assertTrue(b - a < a)                                   // پیکسلِ دوم کمتر از اول
    assertTrue(PullCard.stretch(1_000_000f, cfg) < cfg.limit)
    assertEquals(0f, PullCard.stretch(-5f, cfg))
  }

  @Test
  fun `وارونهٔ لاستیک همان پیکسلِ دیدنی را برمی‌گرداند`() {
    listOf(10f, 120f, 200f, 399f).forEach { v ->
      assertEquals(v, PullCard.stretch(PullCard.fingerFor(v, cfg), cfg), 0.5f)
    }
  }

  @Test
  fun `کشیدنِ کوتاه و آرام با فنر برمی‌گردد`() {
    val s = pulled(100f)                                    // ~۴۹ پیکسلِ دیدنی
    assertFalse(s.latched)
    assertFalse(PullCard.release(s, 0f, cfg).shown)
  }

  @Test
  fun `گذر از آستانه قفل می‌کند و رها کردن کادر را باز نگه می‌دارد`() {
    val s = pulled(400f)                                    // ~۱۴۸ پیکسلِ دیدنی
    assertTrue(s.latched)
    val open = PullCard.release(s, 0f, cfg)
    assertTrue(open.shown)
    assertEquals(1f, PullCard.progress(open, cfg))
    assertEquals(cfg.open, PullCard.visible(open, cfg))
  }

  @Test
  fun `قفل تا نیمهٔ آستانه می‌ماند و پایین‌تر برداشته می‌شود`() {
    val (half, used) = PullCard.pullUp(pulled(400f), 150f, cfg)    // ~۱۰۵ پیکسل
    assertEquals(150f, used)
    assertTrue(half.latched)
    val (low, _) = PullCard.pullUp(half, 150f, cfg)                  // ~۴۹ پیکسل
    assertFalse(low.latched)
    assertFalse(PullCard.release(low, 0f, cfg).shown)
  }

  @Test
  fun `کششِ کوتاهِ تند هم کادر را می‌آورد`() {
    assertTrue(PullCard.release(pulled(100f), 2400f, cfg).shown)
    //  کمتر از کمینه — پرتاب هم بس نیست
    assertFalse(PullCard.release(pulled(60f), 4000f, cfg).shown)
  }

  @Test
  fun `اسکرولِ معمولی کادر را نمی‌آورد — لمس از وسطِ صفحه شروع شده بود`() {
    val s = pulled(900f, atTop = false)
    assertEquals(0f, s.finger)
    assertFalse(PullCard.release(s, 5000f, cfg).shown)
  }

  @Test
  fun `بالا بردنِ انگشت پیش از رها کردن کشش را پس می‌گیرد`() {
    val s = pulled(100f)
    val (back, used) = PullCard.pullUp(s, 300f, cfg)
    assertEquals(0f, back.finger)
    assertEquals(100f, used, 0.01f)                          // فقط همان که کشیده بود
    assertFalse(PullCard.release(back, 0f, cfg).shown)
  }

  @Test
  fun `باز — کششِ دوباره بی‌اثر و بالا کشیدن یا اسکرول می‌بندد`() {
    val shown = PullCard.release(pulled(400f), 0f, cfg)
    assertEquals(shown, PullCard.pullDown(shown, 50f, cfg))
    assertEquals(shown, PullCard.touchDown(shown, atTop = true))
    assertFalse(PullCard.dismiss(shown).shown)
    assertEquals(0f, PullCard.progress(PullCard.dismiss(shown), cfg))
  }

  @Test
  fun `باز — اسکرولی که روی کادر شروع نشد کادر را نمی‌بندد`() {
    val shown = PullCard.touchDown(PullCard.forceShown(), atTop = true, onCard = false)
    val (s, used) = PullCard.pushUp(shown, 200f, cfg)
    assertEquals(0f, used)
    assertTrue(PullCard.release(s, -5000f, cfg).shown)
    //  صفحه بالا نبود (لمس روی کادر ولی پیمایش‌شده) ⇒ همان
    assertEquals(0f, PullCard.pushUp(PullCard.touchDown(PullCard.forceShown(), atTop = false, onCard = true), 200f, cfg).second)
  }

  @Test
  fun `باز — کشیدنِ کادر رو به بالا هم‌پای انگشت جمعش می‌کند و پس از آستانه پنهان می‌شود`() {
    var s = PullCard.touchDown(PullCard.forceShown(), atTop = true, onCard = true)
    val (a, used) = PullCard.pushUp(s, 60f, cfg)
    assertEquals(60f, used, 0.01f)
    assertEquals(cfg.open - 60f, PullCard.visible(a, cfg), 0.01f)
    //  کوتاه و آرام ⇒ با فنر باز برمی‌گردد
    assertTrue(PullCard.release(a, 0f, cfg).shown)
    //  کوتاه ولی تند رو به بالا ⇒ پنهان
    assertFalse(PullCard.release(a, -2000f, cfg).shown)
    //  پس از آستانه ⇒ پنهان؛ هرگز بیش از بلندیِ کادر
    s = PullCard.pushUp(a, 1000f, cfg).first
    assertEquals(cfg.open, s.push, 0.01f)
    assertEquals(0f, PullCard.visible(s, cfg), 0.01f)
    assertFalse(PullCard.release(s, 0f, cfg).shown)
  }

  @Test
  fun `باز — پایین آوردنِ انگشت پیش از رها کردن کادر را باز نگه می‌دارد`() {
    val s = PullCard.pushUp(PullCard.touchDown(PullCard.forceShown(), atTop = true, onCard = true), 200f, cfg).first
    val (back, used) = PullCard.pushDown(s, 500f)
    assertEquals(200f, used, 0.01f)
    assertEquals(0f, back.push)
    assertTrue(PullCard.release(back, 0f, cfg).shown)
    //  پنهان: پایین آوردن کاری با «جمع شدن» ندارد
    assertEquals(0f, PullCard.pushDown(PullCard.State(), 50f).second)
  }

  @Test
  fun `عکسِ نیمه‌کشیده همان نیمه را نشان می‌دهد`() {
    val s = PullCard.forcePulled(0.5f, cfg)
    assertEquals(0.5f, PullCard.progress(s, cfg), 0.01f)
    assertTrue(s.latched)
  }

  @Test
  fun `هشدار — هیچ چیز ⇒ خالی؛ سفارش فقط وقتی زنده است`() {
    assertTrue(HomeAlerts.build(HomeAlerts.Input()).isEmpty())
    assertTrue(HomeAlerts.build(HomeAlerts.Input(ordersLive = false, newOrders = 3)).isEmpty())
    val a = HomeAlerts.build(HomeAlerts.Input(ordersLive = true, newOrders = 3))
    assertEquals(HomeAlerts.Kind.ORDERS, a.single().kind)
    assertEquals("orders?tab=new", a.single().route)
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
    //  «هر کار به کادرِ خودش»: همگام‌سازی ⇐ صفحهٔ همگام‌سازی (نه دستگاه‌های جانبی)، کالا ⇐ همان فیلتر
    assertEquals(
      listOf("orders?tab=new", "subscription", "settings/data/sync", "products?filter=out", "debtors?sort=oldest", "stock?filter=expiring"),
      a.map { it.route },
    )
    assertEquals("subscription/plans", a[1].actionRoute)
    assertEquals("orders?tab=new", a[0].actionRoute)
    assertTrue(a[1].urgent)
  }

  @Test
  fun `کالای کم بی تمام‌شده فهرستِ کم را باز می‌کند`() {
    val a = HomeAlerts.build(HomeAlerts.Input(lowStock = 3)).single()
    assertEquals("products?filter=low", a.route)
  }

  @Test
  fun `خطای همگام‌سازی دلیلِ واقعیِ موتور را می‌گوید`() {
    val a = HomeAlerts.build(HomeAlerts.Input(sync = HomeAlerts.Sync.ERROR, syncError = "HTTP 503 — سرور در دسترس نیست")).single()
    assertEquals("HTTP 503 — سرور در دسترس نیست", a.body)
    assertEquals("settings/data/sync", a.route)
    val long = HomeAlerts.syncReason("x".repeat(500))
    assertEquals(HomeAlerts.MAX_ERROR, long.length)
    assertTrue(long.endsWith("…"))
    assertEquals(HomeAlerts.build(HomeAlerts.Input(sync = HomeAlerts.Sync.ERROR)).single().body, HomeAlerts.syncReason(""))
  }

  @Test
  fun `قطعیِ ماندگارِ سرور کارتِ آرام است، نه سرخ — SYN-12`() {
    val a = HomeAlerts.build(HomeAlerts.Input(sync = HomeAlerts.Sync.OUTAGE, queued = 3, syncError = "به سرور نرسیدیم")).single()
    assertEquals(HomeAlerts.Kind.SYNC, a.kind)
    assertEquals("همگام‌سازی منتظرِ سرور است", a.title)
    assertFalse("هشدارِ بی‌مورد نه", a.urgent)
    assertTrue("روی کارتِ خانه دیده می‌شود", a.pressing)
    assertEquals("settings/data/sync", a.route)
    //  بی صف هم: داده‌های دستگاه‌های دیگر نمی‌رسند
    assertEquals(1, HomeAlerts.pressing(HomeAlerts.build(HomeAlerts.Input(sync = HomeAlerts.Sync.OUTAGE))).size)
  }

  @Test
  fun `صفِ بی‌اینترنت هم به صفحهٔ همگام‌سازی می‌رود`() {
    val a = HomeAlerts.build(HomeAlerts.Input(sync = HomeAlerts.Sync.OFFLINE, queued = 4)).single()
    assertEquals("settings/data/sync", a.route)
  }

  @Test
  fun `کارتِ خانه فقط کارِ فوری — تمام‌شده و صفِ مانده بله، کم و قرض و انقضا نه`() {
    val calm = HomeAlerts.build(HomeAlerts.Input(lowStock = 3, overdueDebtors = 2, expiring = 1, sync = HomeAlerts.Sync.QUEUED, queued = 2))
    assertTrue(HomeAlerts.pressing(calm).isEmpty())
    val out = HomeAlerts.build(HomeAlerts.Input(outOfStock = 2, lowStock = 3))
    assertEquals(listOf(HomeAlerts.Kind.STOCK), HomeAlerts.pressing(out).map { it.kind })
    val offline = HomeAlerts.build(HomeAlerts.Input(sync = HomeAlerts.Sync.OFFLINE, queued = 4))
    assertEquals(listOf(HomeAlerts.Kind.SYNC), HomeAlerts.pressing(offline).map { it.kind })
    val error = HomeAlerts.build(HomeAlerts.Input(sync = HomeAlerts.Sync.ERROR))
    assertEquals(1, HomeAlerts.pressing(error).size)
  }

  @Test
  fun `اشتراکِ قفل‌شده بر روزهای مانده مقدم است`() {
    val a = HomeAlerts.build(HomeAlerts.Input(subscriptionLocked = true, subscriptionDaysLeft = 5)).single()
    assertEquals("اشتراک تمام شده", a.title)
  }
}
