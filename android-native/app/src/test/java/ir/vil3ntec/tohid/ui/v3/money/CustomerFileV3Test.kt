package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.data.CustomerProfileForm
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.DebtorListModel
import ir.vil3ntec.tohid.ui.screens.DebtorListModel.Sort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  پروندهٔ مشتریِ نسخهٔ ۳ (CRM-02/11): صافیِ برچسبِ فهرستِ قرض‌داران (`DebtorListModel.rows(…, tag)`، `tagChips`،
 *  `keptTag`) و خطِ «شمارهٔ دوم · #برچسب» ِ کشوی حساب (`CustomerProfileForm.extraLine` — همان خطِ پروندهٔ کامپیوتر).
 */
class CustomerFileV3Test {

  private val d = ShopData(
    debtors = listOf(
      Debtor(id = "a", name = "احمد", phone = "0700000001", phone2 = "0705556677", tags = listOf("عمده", "همسایه")),
      Debtor(id = "b", name = "بصیر", tags = listOf("عمده")),
      Debtor(id = "c", name = "جمیله"),
      //  ادغام‌شده (CRM-10) نه در فهرست می‌آید، نه در شمارِ برچسب
      Debtor(id = "old", name = "احمد کهنه", tags = listOf("کهنه"), mergedInto = "a"),
    ),
  )

  private fun ids(tag: String?, term: String = "") = DebtorListModel.rows(d, term, Sort.NAME, 0L, tag).map { it.debtor.id }

  @Test
  fun `صافیِ برچسب فقط مشتریانِ همان برچسب، با جست‌وجو هم`() {
    assertEquals(listOf("a", "b", "c"), ids(null))
    assertEquals(listOf("a", "b"), ids("عمده"))
    assertEquals(listOf("a", "b"), ids("#عمده"))
    assertEquals(listOf("a"), ids("همسایه"))
    assertEquals(listOf("b"), ids("عمده", term = "بصیر"))
    assertEquals(emptyList<String>(), ids("کهنه"))
    //  بی صافی همان ردیف‌های پیشین (پارامترِ تازه پیش‌فرض دارد)
    assertEquals(DebtorListModel.rows(d, "", Sort.NAME, 0L).map { it.debtor.id }, ids(null))
  }

  @Test
  fun `تراشه‌ها برچسب‌های مشتریانِ فعال با شمار، پرتکرار اول؛ برچسبِ رفته ⇒ همه`() {
    assertEquals(listOf("عمده" to 2, "همسایه" to 1), DebtorListModel.tagChips(d))
    assertEquals(emptyList<Pair<String, Int>>(), DebtorListModel.tagChips(ShopData(debtors = listOf(Debtor(id = "x", name = "x")))))
    assertEquals("عمده", DebtorListModel.keptTag(d, "عمده"))
    assertNull(DebtorListModel.keptTag(d, "کهنه"))
    assertNull(DebtorListModel.keptTag(d, null))
  }

  @Test
  fun `کشوی حساب شمارهٔ دوم و برچسب‌ها را نشان می‌دهد، مشتریِ بی آن‌ها هیچ`() {
    assertEquals("${LocaleText.phoneDisplay("0705556677")} · #عمده #همسایه", CustomerProfileForm.extraLine(d.debtors[0]))
    assertEquals("#عمده", CustomerProfileForm.extraLine(d.debtors[1]))
    assertEquals("", CustomerProfileForm.extraLine(d.debtors[2]))
  }
}
