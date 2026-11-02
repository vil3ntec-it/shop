package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.promo.Loyalty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-13، تکمیل — قاعدهٔ یکسانِ دکان (برتری و بی‌اینترنتی، `LoyaltyRules`)، اصلاحِ دستی (`LoyaltyPoints.adjust`) و انقضا
 *  در صندوق، برگشت و لغو. خاموش ⇒ همان امروز (`LoyaltyOffTest` هم سبز می‌ماند).
 */
class LoyaltyCompleteTest {

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }
  private val today = "2026-11-02"
  private val now = 1_800_000_000_000L
  private val rule = Loyalty.Rule(everyAfn = 100, pointAfn = 1, maxSharePct = 20, silverAfn = 50_000, goldAfn = 200_000)
  private val month = Loyalty.MONTH_MS

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", salePrice = 1000.0, purchasePrice = 700.0, unit = "عدد"),
      Product(id = "p2", name = "برنج", salePrice = 500.0, purchasePrice = 300.0, unit = "عدد"),
    ),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 50.0), WarehouseEntry(id = "w2", productId = "p2", units = 50.0)),
    debtors = listOf(Debtor(id = "d1", name = "نوشا"), Debtor(id = "d2", name = "نوشا (کهنه)", mergedInto = "d1")),
  )

  @After fun unbind() { LoyaltyRules.override = null }

  private fun earn(d: ShopData, points: Long, at: Long, id: String = "e$points-$at", customer: String = "d1") = d.copy(
    auditLog = d.auditLog + AuditEntry(id = id, type = LoyaltyPoints.EARN, refId = "old-$id", payRef = customer, points = points, createdAt = at),
  )

  private fun ok(r: SalesEngine.Result): ShopData = (r as? SalesEngine.Result.Ok)?.data ?: error("ثبت نشد: $r")

  // ── قاعدهٔ یکسانِ دکان ─────────────────────────────────────────────
  private val shopRule = rule.copy(everyAfn = 50, expiryMonths = 12)
  private val json = Json

  @Test fun `قاعدهٔ ذخیره‌شدهٔ دکان بر قاعدهٔ دستگاه برتری دارد — روشن و خاموشش هم`() {
    val shop = LoyaltyRules.Shop(on = true, rule = shopRule, version = 3, manage = false)
    assertEquals(LoyaltyRules.Config(true, shopRule, true), LoyaltyRules.resolve(false, rule, shop))
    //  دکان خاموشش کرده ⇒ همهٔ دستگاه‌ها خاموش، حتی دستگاهی که خودش روشن بود
    assertEquals(LoyaltyRules.Config(false, shopRule, true), LoyaltyRules.resolve(true, rule, shop.copy(on = false)))
  }

  @Test fun `بی قاعدهٔ دکان یا بی‌اینترنتِ هرگز‌گرفته‌نشده همان قاعدهٔ دستگاه`() {
    //  هرگز گرفته نشده (یا پرچمِ خاموش پاکش کرده)
    assertEquals(LoyaltyRules.Config(true, rule, false), LoyaltyRules.resolve(true, rule, null))
    //  پرچم روشن ولی هنوز ذخیره نشده
    assertEquals(LoyaltyRules.Config(false, rule, false), LoyaltyRules.resolve(false, rule, LoyaltyRules.Shop(true, null, 0, true)))
    //  قاعدهٔ نادرستِ دستگاه ⇒ پیش‌فرضِ هسته، نه قاعدهٔ خراب
    assertEquals(Loyalty.Rule(), LoyaltyRules.resolve(true, rule.copy(goldAfn = 1), null).rule)
  }

  @Test fun `پاسخِ سرور روی دستگاه می‌ماند و بی‌اینترنت همان را می‌دهد`() {
    val body = json.parseToJsonElement(
      """{"enabled":true,"on":true,"rule":{"everyAfn":50,"pointAfn":1,"maxSharePct":20,"silverAfn":50000,"goldAfn":200000,"expiryMonths":12},""" +
        """"version":3,"updatedAt":9,"permissions":{"manage":true}}""",
    ).jsonObject
    val shop = LoyaltyRules.parse(body)!!
    assertEquals(LoyaltyRules.Shop(true, shopRule, 3, true), shop)
    //  همان چیزی که `LoyaltyShop` روی دیسک می‌گذارد دوباره همان می‌شود (بی‌اینترنت ⇒ همین)
    assertEquals(shop, LoyaltyRules.parse(json.parseToJsonElement(LoyaltyRules.serialize(shop).toString()).jsonObject))
    assertEquals(LoyaltyRules.Config(true, shopRule, true), LoyaltyRules.resolve(false, rule, shop))
    //  پرچمِ خاموش ⇒ پاک (هر دستگاه قاعدهٔ خودش)
    assertNull(LoyaltyRules.parse(json.parseToJsonElement("""{"enabled":false}""").jsonObject))
    //  عددِ رشته‌ای یا قاعدهٔ نادرست ⇒ بی قاعده، نه قاعدهٔ نیمه‌کاره
    assertNull(LoyaltyRules.ruleOf(json.parseToJsonElement("""{"everyAfn":"50","pointAfn":1,"maxSharePct":20,"silverAfn":5,"goldAfn":9}""").jsonObject))
    assertNull(LoyaltyRules.ruleOf(json.parseToJsonElement("""{"everyAfn":50,"pointAfn":1,"maxSharePct":20,"silverAfn":9,"goldAfn":9}""").jsonObject))
  }

  @Test fun `بدنهٔ ذخیره همان شکلِ سرور است`() {
    val b = LoyaltyRules.body(true, shopRule, 3)
    assertEquals(
      """{"on":true,"rule":{"everyAfn":50,"pointAfn":1,"maxSharePct":20,"silverAfn":50000,"goldAfn":200000,"expiryMonths":12},"expectedVersion":3}""",
      b.toString(),
    )
  }

  // ── اصلاحِ دستی ───────────────────────────────────────────────────
  private fun adjust(d: ShopData, delta: Long, op: String, may: Boolean = true, reason: String = "هدیهٔ عید", debtor: String? = "d1", at: Long = now) =
    LoyaltyPoints.adjust(d, debtor, delta, reason, op, rule, may, today, at, "صاحب")

  @Test fun `اصلاحِ دستی یک ردیفِ فقط‌افزودنی با دلیل است و تلاشِ دوباره ردیفِ دوم نمی‌سازد`() {
    val d0 = earn(base, 10, 1)
    val a = adjust(d0, 5, "op1") as LoyaltyPoints.Adjusted.Ok
    assertFalse(a.again)
    val row = a.data.auditLog.last()
    assertEquals("lpa-op1", row.id)
    assertEquals(LoyaltyPoints.ADJUST, row.type)
    assertEquals(5L, row.points)
    assertEquals("d1", row.payRef)
    assertEquals("هدیهٔ عید", row.notes)
    assertNull(row.refId)
    assertEquals(15L, LoyaltyPoints.balance(a.data, "d1"))
    //  همان کار دوباره — حتی با عددِ دیگر — همان دفتر
    val again = adjust(a.data, 7, "op1") as LoyaltyPoints.Adjusted.Ok
    assertTrue(again.again)
    assertSame(a.data, again.data)
    //  کم‌کردن و سابقه با دلیل
    val b = (adjust(a.data, -15, "op2", reason = "  اشتباهِ   ثبت ", at = now + 1) as LoyaltyPoints.Adjusted.Ok).data
    assertEquals(0L, LoyaltyPoints.balance(b, "d1"))
    val h = LoyaltyPoints.history(b, "d1").first()
    assertEquals(Loyalty.ADJUST, h.kind)
    assertEquals(-15L, h.signed)
    assertEquals(StrLoyalty.kindAdjust + " — اشتباهِ ثبت", LoyaltyPoints.rowText(h))
  }

  @Test fun `اصلاحِ دستی هرگز زیرِ صفر نمی‌برد و دلیل، مشتری و اجازه می‌خواهد`() {
    val d0 = earn(base, 10, 1)
    assertEquals(LoyaltyPoints.Adjusted.Bad(StrLoyalty.errBalance), adjust(d0, -11, "x1"))
    assertEquals(LoyaltyPoints.Adjusted.Bad(StrLoyalty.adjustOwnerOnly), adjust(d0, 5, "x2", may = false))
    assertEquals(LoyaltyPoints.Adjusted.Bad(StrLoyalty.adjustNeedsReason), adjust(d0, 5, "x3", reason = "   "))
    assertEquals(LoyaltyPoints.Adjusted.Bad(StrLoyalty.adjustBadPoints), adjust(d0, 0, "x4"))
    assertEquals(LoyaltyPoints.Adjusted.Bad(StrLoyalty.errCustomer), adjust(d0, 5, "x5", debtor = "nobody"))
    assertEquals(LoyaltyPoints.Adjusted.Bad(StrLoyalty.adjustReasonLong(LoyaltyPoints.REASON_MAX)), adjust(d0, 5, "x6", reason = "ا".repeat(121)))
    //  مشتریِ ادغام‌شده ⇒ مشتریِ ماندگار
    val m = (adjust(d0, -10, "x7", debtor = "d2") as LoyaltyPoints.Adjusted.Ok).data
    assertEquals("d1", m.auditLog.last().payRef)
    assertEquals(0L, LoyaltyPoints.balance(m, "d1"))
  }

  @Test fun `اصلاحِ منفی امتیازِ سوخته را خرج نمی‌کند`() {
    val r = rule.copy(expiryMonths = 1)
    val d0 = earn(earn(base, 10, 0, id = "old"), 4, month, id = "new")
    //  در ۱ ماه و ۱ میلی‌ثانیه: ۱۰ ِ نخست سوخته، فقط ۴ مانده
    val bad = LoyaltyPoints.adjust(d0, "d1", -5, "اصلاح", "o1", r, true, today, month + 1, null)
    assertEquals(LoyaltyPoints.Adjusted.Bad(StrLoyalty.errBalance), bad)
    assertTrue(LoyaltyPoints.adjust(d0, "d1", -4, "اصلاح", "o2", r, true, today, month + 1, null) is LoyaltyPoints.Adjusted.Ok)
  }

  // ── انقضا در صندوق، برگشت و لغو ───────────────────────────────────
  @Test fun `امتیازِ سوخته در صندوق مصرف‌شدنی نیست و ثبت هم آن را رد می‌کند`() {
    val r = rule.copy(expiryMonths = 1)
    val d0 = earn(base, 300, now - month)          //  درست در مرزِ سوختن
    val cart = listOf(SalesEngine.CartLine("p2", 3.0))
    assertEquals(0L, LoyaltyPoints.capFor(d0, cart, r, "d1", now))
    assertNull(LoyaltyPoints.apply(d0, cart, r, "d1", 100, now))
    //  یک میلی‌ثانیه پیش‌تر هنوز زنده
    assertEquals(300L, LoyaltyPoints.capFor(d0, cart, r, "d1", now - 1))
    val plan = LoyaltyPoints.apply(d0, cart, r, "d1", 100, now - 1)!!
    //  ثبتِ همان سبد در لحظهٔ سوختن ⇒ رد، بی هیچ ردیف
    val late = LoyaltyPoints.post(SalesEngine.record(d0, plan.cart, SalesEngine.Checkout(debtorId = "d1"), today, now, newId, saleId = "s1"), true, r, plan.plan, null)
    assertEquals(SalesEngine.Result.Failed(StrLoyalty.errBalance), late)
    //  مانده و «به‌زودی» در نمای صندوق
    val v = LoyaltyPoints.view(d0, r, "d1", now - 1)
    assertEquals(300L, v.balance)
    assertEquals(300L, v.soon)
    assertEquals(now, v.soonAt)
  }

  @Test fun `برگشت با انقضای قاعدهٔ در کار مانده را منفی نمی‌کند`() {
    val r = rule.copy(expiryMonths = 1)
    var d = ok(LoyaltyPoints.post(SalesEngine.record(base, listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout(debtorId = "d1"), today, now, newId, saleId = "s1"), true, r, null, null))
    assertEquals(10L, LoyaltyPoints.balance(d, "d1"))
    val item = d.saleItems.single { it.saleId == "s1" }.id
    LoyaltyRules.override = LoyaltyRules.Config(true, r, true)
    //  دو ماه بعد: ۱۰ امتیاز سوخته ⇒ چیزی برای پس‌گرفتن نیست، مانده صفر نه منفی
    d = ok(SalesEngine.recordReturn(d, "s1", mapOf(item to 1.0), "دیگر", today, now + 2 * month, newId))
    assertTrue(d.auditLog.none { it.type == LoyaltyPoints.EARN_BACK })
    assertEquals(0L, LoyaltyPoints.balanceAt(d, "d1", r, now + 2 * month))
  }

  @Test fun `لغو پیش از سوختن همه را پس می‌گیرد و بی قاعدهٔ وصل‌شده همان رفتارِ بی انقضا`() {
    val r = rule.copy(expiryMonths = 1)
    val d = ok(LoyaltyPoints.post(SalesEngine.record(base, listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout(debtorId = "d1"), today, now, newId, saleId = "s1"), true, r, null, null))
    LoyaltyRules.override = LoyaltyRules.Config(true, r, true)
    val c = ok(SalesEngine.cancel(d, "s1", today, now + 1, newId, reason = "اشتباه"))
    assertEquals(10L, c.auditLog.single { it.id == "s1-lx-e" }.points)
    //  قاعدهٔ بی انقضا (خاموش، یا هنوز وصل‌نشده ⇒ `OFF`) ⇒ همان پس‌گرفتنِ کاملِ پیشین
    LoyaltyRules.override = LoyaltyRules.OFF
    val late = ok(SalesEngine.cancel(d, "s1", today, now + 2 * month, newId, reason = "اشتباه"))
    assertEquals(10L, late.auditLog.single { it.id == "s1-lx-e" }.points)
  }

  // ── خاموش ⇒ همان ──────────────────────────────────────────────────
  @Test fun `خاموش یا بی انقضا همان امروز`() {
    val d0 = earn(base, 300, 1)
    assertEquals(LoyaltyPoints.balance(d0, "d1"), LoyaltyPoints.balanceAt(d0, "d1", rule, 10 * now))
    val cart = listOf(SalesEngine.CartLine("p2", 3.0))
    assertEquals(LoyaltyPoints.capFor(d0, cart, rule, "d1"), LoyaltyPoints.capFor(d0, cart, rule, "d1", 10 * now))
    val r = SalesEngine.record(d0, cart, SalesEngine.Checkout(debtorId = "d1"), today, now, newId, saleId = "s9")
    assertSame(r, LoyaltyPoints.post(r, false, rule, null, null))
    assertEquals(LoyaltyPoints.View("d1", 300, rule.tiers[0], 0), LoyaltyPoints.view(d0, rule, "d1", now))
  }
}
