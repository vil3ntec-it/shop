package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.core.pay.EmptyEnterGate
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalePost
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.scan.HidScan
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import ir.vil3ntec.tohid.ui.v3.sales.cart.bareEnterPays
import ir.vil3ntec.tohid.ui.v3.sales.cart.hidKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S11 — دوبار زدنِ پرداخت و Enter ِ تصادفی (POS-14): قفلِ «ثبتِ فروش» (`SalePost`) و کلیدِ یکتای فاکتور
 *  (`SalesEngine.record` با `saleId`)، Enter در کادرِ خالی (`PosSearch.resolve`) و نگهبانِ Enter ِ پنلِ پرداخت
 *  (`CashKeys.enterArmed`، PAY-04؛ پنل پشتِ پرچمِ خاموشِ `ShopPolicy.payV2`).
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • گامِ ۲ (دو کلیک در وب با قطع و وصلِ شبکه) آزموده نشده: صندوقِ وب منجمد است و این آزمون فقط برنامه را می‌سنجد.
 *   • قاعدهٔ «Enter در کادرِ خالی پس از ≥ ۵۰۰ میلی‌ثانیه ⇒ پنلِ پرداخت» (§۵.۲) فقط در صندوقِ کامپیوتر و فقط با پنلِ
 *     پرداخت (`payV2`) است (`CashKeys.emptyEnterOpensPay`)؛ بی پنل Enter در کادرِ خالی همان «هیچ کار» ِ امروز. صندوقِ
 *     گوشیِ نسخهٔ ۳ با صفحه‌کلیدِ بیرونی همان قاعده را دارد (`bareEnterPays` ⇐ «پرداخت»)؛ رفتنِ واقعی به برگهٔ پرداخت
 *     (`SalesPaths.checkout`) صفحه است و فقط در CI.
 */
class ScenarioS11Test {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلوگرم", salePrice = 120.0, barcodes = listOf("6260000000017"))
  private val d0 = ShopData(products = listOf(rice), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 50.0)))
  private val cart = listOf(SalesEngine.CartLine("p1", 2.0))
  private var seq = 0

  private fun record(d: ShopData, key: String) =
    SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-11-02", 1_000, { "n${seq++}" }, saleId = key)

  @Test
  fun `S11 — پنج Enter ِ پیاپی روی پرداخت ⇒ دقیقاً یک فروش، یک شمارهٔ سند و یک رسید`() = runBlocking<Unit> {
    var ledger = d0
    val post = SalePost()
    val gate = CompletableDeferred<Unit>()
    var receipts = 0
    val first = async {
      post.run("K1", { k -> record(ledger, k) }, { data -> gate.await(); ledger = data; true }) { receipts++ }
    }
    while (!post.busy.value) yield()
    repeat(4) {
      assertEquals(SalePost.Outcome.Busy, post.run("K1", { k -> record(ledger, k) }, { data -> ledger = data; true }) { receipts++ })
    }
    gate.complete(Unit)
    assertTrue(first.await() is SalePost.Outcome.Done)
    //  Enter ِ دیرتر پس از پایانِ ثبت با همان کلید ⇒ «یک بار ثبت شده»، بی نوشتن و بی رسید
    val late = post.run("K1", { k -> record(ledger, k) }, { data -> ledger = data; true }) { receipts++ }
    assertEquals(SalePost.Outcome.Refused(SalesEngine.ALREADY_POSTED), late)
    assertEquals(1, ledger.sales.size)
    assertEquals(1, ledger.sales.mapNotNull { it.invoiceNumber }.distinct().size)
    assertEquals(1, receipts)
    assertEquals(48.0, ShopStore.stock(ledger, "p1"), 0.0)
  }

  @Test
  fun `S11 — اسکن و سه Enter ِ پشتِ سرِ هم ⇒ هیچ فروشی ثبت نمی‌شود`() {
    val index = ShopStore.barcodeIndex(d0)
    //  Enter ِ پسوندِ بارکدخوان ⇒ کالا به سبد؛ کادر خالی می‌شود
    assertEquals(PosHit.Add(rice, 1.0), PosSearch.resolve("6260000000017", index, d0.products, emptyList(), 0))
    //  دو Enter ِ دیگر در کادرِ خالی ⇒ هیچ کار
    repeat(2) { assertEquals(PosHit.None, PosSearch.resolve("", index, d0.products, emptyList(), 0)) }
    //  اگر پنل باز شده باشد، Enter ِ زیرِ ۴۰۰ میلی‌ثانیه پس از باز شدن فاکتور را نمی‌بندد
    for (ms in listOf(0L, 15L, 30L, CashKeys.ENTER_GUARD_MS - 1)) assertFalse(CashKeys.enterArmed(ms))
    assertTrue(CashKeys.enterArmed(CashKeys.ENTER_GUARD_MS))
  }

  @Test
  fun `S11 — Enter در کادرِ خالی ⇒ پنلِ پرداخت فقط ۵۰۰ میلی‌ثانیه پس از آخرین اسکن یا Enter`() {
    //  اسکن با Enter ِ پسوند (۰)، دو Enter ِ دیگرِ بارکدخوان (۱۵ و ۳۰) ⇒ پنل باز نمی‌شود و فروشی هم نیست
    var last: Long? = 0L
    var opened = 0
    for (at in listOf(15L, 30L)) {
      if (CashKeys.emptyEnterOpensPay(at - last!!, cartEmpty = false)) opened++
      last = at
    }
    assertEquals(0, opened)
    //  صندوقدار پس از مکث Enter می‌زند ⇒ پنل (نگهبانِ ۴۰۰ ِ خودِ پنل فاکتور را با همان Enter نمی‌بندد)
    assertTrue(CashKeys.emptyEnterOpensPay(30L + CashKeys.EMPTY_ENTER_MS - last!!, cartEmpty = false))
    assertFalse(CashKeys.enterArmed(0))
    assertFalse("سبدِ خالی هرگز", CashKeys.emptyEnterOpensPay(null, cartEmpty = true))
  }

  @Test
  fun `S11 — صندوقِ کامپیوتر قاعده را فقط با پنلِ پرداخت و پیش از جست‌وجو می‌زند و اسکنِ بیرون از کادر هم شمرده می‌شود`() {
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/ui/screens/DeskSale.kt") }.firstOrNull { it.isFile }?.readText()
      ?: error("DeskSale.kt پیدا نشد")
    val submit = src.substringAfter("fun submit() {").substringBefore("PosSearch.resolve(")
    //  هر Enter لحظه را می‌نشاند؛ Enter ِ کادرِ خالی فقط با پنلِ پرداخت سنجیده می‌شود و پس از ثبتِ سریع و برچسبِ ترازو
    assertTrue(submit.contains("val pay = if (payV2 && query.isBlank()) enterGate.emptyEnter(cart.isEmpty()) else { enterGate.mark(); false }"))
    assertTrue(submit.indexOf("if (payV2 && query.isBlank()) { if (pay) payOpen = true; return }") > submit.indexOf("scaleLabel(query, d)"))
    assertTrue(src.contains("val enterGate = remember { ir.vil3ntec.tohid.core.pay.EmptyEnterGate() }"))
    assertTrue(src.substringAfter("fun fromScanner(code: String): Boolean {").substringBefore("val dd = latestD").contains("enterGate.mark()"))
  }

  /**
   *  صندوقِ گوشیِ نسخهٔ ۳ با صفحه‌کلید یا بارکدخوانِ بیرونی (`Modifier.hidScanner` ⇐ `hidKey` و `bareEnterPays`): همان
   *  `HidScan` و `EmptyEnterGate` ِ واقعی با ساعتِ دستی.
   */
  @Test
  fun `S11 — صندوقِ گوشی با صفحه‌کلیدِ بیرونی ⇒ Enter ِ بی اسکن پرداخت را فقط با پنل و ۵۰۰ میلی‌ثانیه پس از آخرین اسکن باز می‌کند`() {
    var t = 0L
    val gate = EmptyEnterGate { t }
    val hid = HidScan(maxGapMs = 50, minLength = 4)
    var pays = 0
    fun key(enter: Boolean, ch: Char?, payV2: Boolean = true, cartEmpty: Boolean = false) =
      hidKey(enter, ch, t, hid::feed, hid::submit) { bareEnterPays(payV2, gate, cartEmpty) { pays++ } }

    //  بارکدخوان: نویسه‌های تند و CR ⇒ کد (صفحه آن را به `onCode` می‌دهد که لحظه را می‌نشاند)، سپس LF ِ پانزده میلی‌ثانیه بعد
    "6260000000017".forEach { c -> t++; assertNull(key(false, c)!!.code) }
    t++
    assertEquals("6260000000017", key(true, null)?.code)
    gate.mark()
    t += 15
    val lf = key(true, null)
    assertNotNull("Enter ِ دوم خورده شد و به دکمه‌ای نرسید", lf)
    assertNull(lf!!.code)
    assertEquals(0, pays)
    //  صندوقدار پس از مکث Enter می‌زند ⇒ «پرداخت»؛ Enter ِ پشتِ سرِ همان (زیرِ نیم ثانیه) دوباره نه
    t += CashKeys.EMPTY_ENTER_MS
    key(true, null)
    assertEquals(1, pays)
    t += 100
    key(true, null)
    assertEquals(1, pays)

    //  سبدِ خالی هرگز؛ بی پنلِ پرداخت (پیش‌فرض) Enter ِ آدم همان راهِ امروز را می‌رود (خورده نمی‌شود)
    t += 5_000
    assertNotNull(key(true, null, cartEmpty = true))
    t += 5_000
    assertNull(key(true, null, payV2 = false))
    assertEquals(1, pays)
  }

  @Test
  fun `S11 — صندوقِ گوشی هر اسکن را می‌شمارد و Enter ِ بی اسکن را فقط با پنلِ پرداخت به پرداخت می‌برد`() {
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/ui/v3/sales/cart/SaleRoute.kt") }.firstOrNull { it.isFile }?.readText()
      ?: error("SaleRoute.kt پیدا نشد")
    assertTrue(src.contains("val payV2 = remember { ir.vil3ntec.tohid.data.ShopPolicy.payV2(context) }"))
    //  هر بارکد از هر راه (بارکدخوان، دوربین، گوشیِ جفت‌شده) پیش از هر کار لحظه را می‌نشاند
    val onCode = src.substringAfter("fun onCode(raw: String, manual: Boolean, fromPhone: Boolean) {").substringBefore("gate.accept(code)")
    assertTrue(onCode.contains("enterGate.mark()"))
    assertTrue(src.contains("onBareEnter = { bareEnterPays(payV2, enterGate, cart.lines.value.isEmpty()) { CheckoutSession.reset(); nav.open(SalesPaths.checkout()) } }"))
  }
}
