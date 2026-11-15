package ir.vil3ntec.tohid.ui.v3.sales.history

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pay.DayClose
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalePayment
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.PhoneV3Dark
import ir.vil3ntec.tohid.ui.theme.PhoneV3Light
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ir.vil3ntec.tohid.ui.theme.V3Section
import ir.vil3ntec.tohid.ui.theme.SectionColors
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import java.io.File

/**
 *  ۶۵h — «تاریخچه فروش» ساده و دقیق با رنگِ معنادار (صاحب‌کار: «من حتی یک درصدش رو هم نفهمیدم؛ … رنگ بده»):
 *   • هر نوعِ پول یک رنگِ جدا در روز و شب، و هر رنگ با آیکن و واژه (`historyTone`، `moneyIcon`، `HistoryRow.payWord`)؛
 *   • «برگشت دادید / خالص / قرضِ وصول‌شده» با نامِ ساده، صفرها نه، و قرضِ وصول‌شده «فروش نیست»؛
 *   • ردیفِ برگشت‌خورده مبلغِ برگشت را دارد؛ لغوشده سرخ است؛
 *   • راهنمای خاکستریِ بلند جایش را به یک کارِ «برگشتِ کالا» داد که همهٔ راه‌ها را دارد.
 */
class HistoryMeaningTest {

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { I18n.source = { Lang.FA } }

  private fun totals(cash: Long = 0, credit: Long = 0, card: Long = 0, refunds: Long = 0, receipts: Long = 0, returns: Int = 0, cancelled: Int = 0, online: Int = 0) =
    SalesPeriods.Totals(
      invoices = 3, cancelled = cancelled, online = online, returns = returns,
      methods = linkedMapOf(
        PayFlow.CASH to DayClose.Sums(sales = cash, refunds = refunds, receipts = receipts),
        PayFlow.ACCOUNT to DayClose.Sums(sales = credit),
        "card" to DayClose.Sums(sales = card),
      ),
    )

  @Test fun `هر نوعِ پول رنگِ جدای خودش را دارد — روز و شب، و هر کدام آیکن دارد`() {
    for (c in listOf(PhoneV3Light, PhoneV3Dark)) {
      val fg = MoneyKind.entries.associateWith { historyTone(c, it).fg }
      //  شش معنای صفحه شش رنگ‌اند (لغو همان سرخِ برگشت است؛ اینترنتی خاکستریِ آرام)
      val meanings = listOf(MoneyKind.CASH, MoneyKind.CREDIT, MoneyKind.CARD, MoneyKind.RETURN, MoneyKind.NET, MoneyKind.DEBT_IN)
      assertEquals(meanings.size, meanings.map { fg.getValue(it) }.toSet().size)
      assertEquals(fg.getValue(MoneyKind.RETURN), fg.getValue(MoneyKind.CANCELLED))
      assertEquals("خالص خنثیِ پررنگ است، همان رنگِ نوشته", c.ink, fg.getValue(MoneyKind.NET))
      //  ۶۵s: رنگ از منبعِ رنگِ بخش‌ها (`SectionColors`، خانواده‌های پالت): نقد زمردی (همان «رسیدها»)، نسیه زر، کارت
      //  لاجوردی، برگشت عقیقِ سرخِ قرض، قرضِ وصول‌شده نفتی (۶۸c عمدی: دیگر صورتی نیست — «رنگ صورتی کار نگیر») — عدد روی
      //  تهِ‌رنگِ خودش ≥ ۴٫۵:۱ (`SectionColorsTest`)
      fun tone(h: PhoneV3Palette.Hue) = SectionColors.ofHue(h, c.source.isDark)
      assertEquals(tone(PhoneV3Palette.Hue.MINT).strong, fg.getValue(MoneyKind.CASH))
      assertEquals(SectionColors.of(V3Section.RECEIPTS, c.source.isDark).strong, fg.getValue(MoneyKind.CASH))
      assertEquals(tone(PhoneV3Palette.Hue.AMBER).strong, fg.getValue(MoneyKind.CREDIT))
      assertEquals(tone(PhoneV3Palette.Hue.SKY).strong, fg.getValue(MoneyKind.CARD))
      assertEquals(SectionColors.of(V3Section.DEBT, c.source.isDark).strong, fg.getValue(MoneyKind.RETURN))
      assertEquals(tone(PhoneV3Palette.Hue.TEAL).strong, fg.getValue(MoneyKind.DEBT_IN))
      assertEquals(tone(PhoneV3Palette.Hue.MINT).tint, historyTone(c, MoneyKind.CASH).bg)
    }
    assertEquals("هر معنا آیکنِ خودش را دارد (رنگ هرگز تنها نیست)", MoneyKind.entries.size, MoneyKind.entries.map(::moneyIcon).toSet().size)
  }

  @Test fun `برگشت، خالص و قرضِ وصول‌شده با نامِ ساده؛ صفر نشان داده نمی‌شود`() {
    assertTrue("روزِ بی برگشت و بی قرض هیچ خطی ندارد", HistoryResult.lines(totals(cash = 50_000), StrHistory.netToday).isEmpty())
    val t = totals(cash = 80_000, credit = 30_000, card = 15_000, refunds = 10_000, receipts = 50_000, returns = 1)
    val lines = HistoryResult.lines(t, StrHistory.netToday)
    assertEquals(listOf(MoneyKind.RETURN, MoneyKind.NET, MoneyKind.DEBT_IN), lines.map { it.kind })
    assertEquals(listOf(StrHistory.refunded, StrHistory.netToday, StrHistory.debtCollected), lines.map { it.label })
    assertEquals("−" + PeriodLabel.amount(10_000), lines[0].amount)
    assertEquals(StrHistory.refundTimes("1"), lines[0].note)
    assertEquals("خالص = فروش − برگشت (قرض در آن نیست)", PeriodLabel.amount(115_000L), lines[1].amount)
    assertEquals(PeriodLabel.amount(50_000), lines[2].amount)
    assertTrue("قرضِ وصول‌شده فروش نیست", lines[2].note!!.contains("فروش"))
    //  فقط قرض (همان عکسِ صاحب‌کار: فروشِ صفر و «دریافتِ قرض: ۵۰۰»)
    assertEquals(listOf(MoneyKind.DEBT_IN), HistoryResult.lines(totals(receipts = 50_000), StrHistory.netToday).map { it.kind })
  }

  @Test fun `بیرون از جمع دقیق گفته می‌شود و خالص نامِ دوره را دارد`() {
    assertNull(HistoryResult.excluded(totals()))
    assertEquals(StrHistory.notCounted(StrHistory.cancelled("1") + StrHistory.and + StrHistory.online("2")), HistoryResult.excluded(totals(cancelled = 1, online = 2)))
    assertEquals("خالصِ امروز", HistoryResult.netLabel(current = true, SalesPeriods.Unit.DAY, "امروز"))
    assertEquals("خالصِ دیروز", HistoryResult.netLabel(current = false, SalesPeriods.Unit.DAY, "دیروز"))
    assertEquals("خالصِ عقرب 1405", HistoryResult.netLabel(current = true, SalesPeriods.Unit.MONTH, "عقرب 1405"))
  }

  private val book = ShopData(
    debtors = listOf(Debtor(id = "d1", name = "احمد کریمی")),
    sales = listOf(
      Sale("cash", 10, finalTotal = 300.0, paidAmount = 300.0, createdAt = 4, date = "2026-11-14"),
      Sale("credit", 11, finalTotal = 200.0, paidAmount = 0.0, remaining = 200.0, debtGiven = 200.0, paymentMethod = "credit", debtorId = "d1", createdAt = 3, date = "2026-11-14"),
      Sale(
        "card", 12, finalTotal = 150.0, paidAmount = 150.0, paymentMethod = "card", createdAt = 2, date = "2026-11-14",
        payments = listOf(SalePayment(method = "card", kind = "card", amountMinor = 15_000, status = "confirmed")),
      ),
      Sale("gone", 13, finalTotal = 90.0, status = "cancelled", createdAt = 1, date = "2026-11-14"),
    ),
    saleItems = listOf(
      SaleItem("i1", "cash", "", name = "صابون", quantity = 3.0, unitPrice = 100.0, totalPrice = 300.0),
      SaleItem("i2", "credit", "", name = "برنج", quantity = 2.0, unitPrice = 100.0, totalPrice = 200.0),
      SaleItem("i3", "card", "", name = "چای", quantity = 1.0, unitPrice = 150.0, totalPrice = 150.0),
      SaleItem("i4", "gone", "", name = "روغن", quantity = 1.0, unitPrice = 90.0, totalPrice = 90.0),
    ),
    saleReturns = listOf(SaleReturn("r1", saleId = "cash", saleItemId = "i1", quantity = 1.0, amount = 100.0, createdAt = 5, date = "2026-11-14")),
  )

  @Test fun `ردیف — رنگ و واژهٔ هر پرداخت، لغوشده سرخ، و برگشتِ همان فاکتور`() {
    for (look in listOf(null, RowLookup(book))) {
      val rows = book.sales.associate { it.id to HistoryModel.row(book, it, returnsV2 = true, look = look, today = "2026-11-14") }
      assertEquals(MoneyKind.CASH, rows.getValue("cash").kind)
      assertEquals(StrHistory.cash, rows.getValue("cash").payWord)
      assertEquals(MoneyKind.CREDIT, rows.getValue("credit").kind)
      assertEquals(StrHistory.credit, rows.getValue("credit").payWord)
      assertEquals(MoneyKind.CARD, rows.getValue("card").kind)
      assertEquals("کارت", rows.getValue("card").payWord)
      assertEquals(MoneyKind.CANCELLED, rows.getValue("gone").kind)
      assertEquals(StrSale.cancelled, rows.getValue("gone").payWord)
      assertTrue("هر ردیف واژه دارد", rows.values.all { it.payWord.isNotBlank() })
      //  پولِ برگشت‌دادهٔ همین فاکتور؛ هنوز دو صابون مانده ⇒ «همه» نیست
      assertEquals(100.0, rows.getValue("cash").refunded, 0.0)
      assertFalse(rows.getValue("cash").refundedAll)
      assertEquals(0.0, rows.getValue("credit").refunded, 0.0)
      //  وضعیتِ افزوده فقط PAY-07 است؛ کارتِ تأییدشده هیچ
      assertNull(rows.getValue("card").status)
      assertTrue(rows.getValue("credit").subtitle.endsWith("#11"))
    }
  }

  @Test fun `نوعِ پول همان گروهِ فیلتر است`() {
    assertEquals(MoneyKind.CANCELLED, moneyKindOf(SaleState.CANCELLED, PayFlow.CASH))
    assertEquals(MoneyKind.ONLINE, moneyKindOf(SaleState.ONLINE, PayFlow.NON_CASH))
    assertEquals(MoneyKind.CREDIT, moneyKindOf(SaleState.DONE, "credit"))
    assertEquals(MoneyKind.CASH, moneyKindOf(SaleState.DONE, PayFlow.CASH))
    assertEquals(MoneyKind.CARD, moneyKindOf(SaleState.DONE, PayFlow.NON_CASH))
  }

  private fun src(name: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/v3/sales/history/$name") }.firstOrNull { it.isFile }?.readText()
      ?: error("$name پیدا نشد")

  @Test fun `یک کارِ «برگشتِ کالا» به‌جای راهنمای خاکستری، و برگه همهٔ راه‌ها را دارد`() {
    val screen = src("HistoryScreen.kt")
    assertFalse("راهنمای خاکستریِ بلند برداشته شد", screen.contains("StrSale.returnsHint"))
    assertFalse("دکمهٔ جدای «برگشتِ بی رسید» زیرِ خلاصه نیست", screen.contains("V3Button(StrSale.noReceiptReturn"))
    assertTrue(screen.contains("ReturnEntry(ctx.onReturns)"))
    val sheet = src("HistoryResult.kt").substringAfter("internal fun ReturnStartSheet(")
    listOf("actions.scanReceipt", "V3ScopedSearch.openCurrent()", "actions.noReceipt").forEach { assertTrue(it, sheet.contains(it)) }
  }
}
