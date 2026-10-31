package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** CRM-08 (گروه و لیستِ قیمتِ پیش‌فرض) و CRM-12 (رضایتِ تبلیغ با سند): فرمِ «همه یا هیچ» */
class CrmPricingFormTest {

  private var seq = 0
  private fun newId() = "a${seq++}"

  private val d0 = ShopData(
    debtors = listOf(
      Debtor(id = "c1", name = "احمد", phone = "+93700123456", phone2 = "+93799111222", tags = listOf("همسایه")),
      Debtor(id = "c2", name = "محمود", group = "همکار"),
      Debtor(id = "c3", name = "نادر", group = "همکار "),
      Debtor(id = "c4", name = "کریم", group = "عمده‌فروش"),
      Debtor(id = "m1", name = "ادغام‌شده", group = "کهنه", mergedInto = "c1"),
    ),
  )

  private fun ok(r: DebtorEngine.Result): ShopData = (r as DebtorEngine.Result.Ok).data
  private fun save(d: ShopData, id: String, input: CrmPricingForm.Input) = CrmPricingForm.save(d, id, input, "2026-10-31", 77, ::newId)

  @Test fun `گروه، عمده و «بله» با منبع در یک گام؛ شمارهٔ دوم و برچسب دست نمی‌خورند؛ دو رد در دفترچه`() {
    val d = ok(save(d0, "c1", CrmPricingForm.Input(group = " همکار ", priceList = CustomerCrm.PRICE_WHOLESALE, consent = true, source = "حضوری")))
    val c1 = d.debtors.first { it.id == "c1" }
    assertEquals("همکار", c1.group)
    assertEquals(CustomerCrm.PRICE_WHOLESALE, c1.priceList)
    assertEquals(true, c1.marketingConsent)
    assertEquals("+93799111222", c1.phone2)
    assertEquals(listOf("همسایه"), c1.tags)
    assertEquals(listOf("price_list_set", "marketing_consent"), d.auditLog.map { it.type })
    assertTrue(d.auditLog.all { it.refId == "c1" && it.date == "2026-10-31" && it.createdAt == 77L })
    assertTrue(d.auditLog.last().notes.endsWith("— حضوری"))
    assertEquals(d.auditLog.last(), CrmPricingForm.lastConsent(d, "c1"))
    assertEquals(listOf("c1"), CustomerCrm.promoRecipients(d).map { it.id })
  }

  @Test fun `«بله» بی منبع ⇒ رد و دفترِ دست‌نخورده، حتی وقتی گروه هم عوض می‌شد`() {
    val r = save(d0, "c1", CrmPricingForm.Input(group = "همکار", consent = true))
    assertTrue(r is DebtorEngine.Result.Failed)
    assertTrue(CrmPricingForm.needsSource(d0.debtors.first(), CrmPricingForm.Input(consent = true)))
    //  گروهِ بیش از سقف هم پیش از هر نوشتنی رد می‌شود
    assertTrue(save(d0, "c1", CrmPricingForm.Input(group = "x".repeat(CustomerCrm.MAX_GROUP_LENGTH + 1))) is DebtorEngine.Result.Failed)
    assertTrue(save(d0, "m1", CrmPricingForm.Input(group = "تازه")) is DebtorEngine.Result.Failed)
  }

  @Test fun `«بله»ی پیشین با تغییرِ گروه منبعِ تازه نمی‌خواهد و ردِ رضایتِ دوم نمی‌سازد`() {
    val d1 = ok(save(d0, "c1", CrmPricingForm.Input(consent = true, source = "تلفنی")))
    val input = CrmPricingForm.of(d1.debtors.first { it.id == "c1" }).copy(group = "عمده‌فروش")
    assertFalse(CrmPricingForm.needsSource(d1.debtors.first { it.id == "c1" }, input))
    val d2 = ok(save(d1, "c1", input))
    assertEquals("عمده‌فروش", d2.debtors.first { it.id == "c1" }.group)
    assertEquals(1, d2.auditLog.count { it.type == "marketing_consent" })
    //  «نه» منبع نمی‌خواهد و از گیرندگان بیرون می‌برد
    val d3 = ok(save(d2, "c1", CrmPricingForm.of(d2.debtors.first { it.id == "c1" }).copy(consent = false)))
    assertEquals(false, d3.debtors.first { it.id == "c1" }.marketingConsent)
    assertTrue(CustomerCrm.promoRecipients(d3).isEmpty())
  }

  @Test fun `بی تغییر ⇒ همان دفتر؛ خرده یعنی null و ردِ لیستِ قیمت`() {
    assertSame(d0, ok(save(d0, "c2", CrmPricingForm.of(d0.debtors.first { it.id == "c2" }))))
    val wholesale = ok(save(d0, "c2", CrmPricingForm.Input(group = "همکار", priceList = CustomerCrm.PRICE_WHOLESALE)))
    val back = ok(save(wholesale, "c2", CrmPricingForm.Input(group = "همکار", priceList = CustomerCrm.PRICE_RETAIL)))
    assertNull(back.debtors.first { it.id == "c2" }.priceList)
    assertEquals(2, back.auditLog.count { it.type == "price_list_set" })
  }

  @Test fun `پیشنهادِ گروه — پرتکرار اول، بی ادغام‌شده، با نوشته صافی و بی تکرارِ همان نوشته`() {
    assertEquals(listOf("همکار", "عمده‌فروش"), CrmPricingForm.suggestions(d0, ""))
    assertEquals(listOf("عمده‌فروش"), CrmPricingForm.suggestions(d0, "عمده"))
    assertEquals(emptyList<String>(), CrmPricingForm.suggestions(d0, "همکار"))
  }
}
