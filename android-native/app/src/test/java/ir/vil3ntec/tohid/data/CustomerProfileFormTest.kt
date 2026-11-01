package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  پروندهٔ مشتری در صفحه‌های قدیم — CRM-02 (شمارهٔ دوم)، CRM-10 (ادغام‌شده بیرونِ فهرست)، CRM-11
 *  (برچسب و صافی). `CustomerProfileForm` روی همان `DebtorEngine` و `CustomerCrm`.
 */
class CustomerProfileFormTest {

  private var seq = 0
  private fun newId() = "n${seq++}"

  private val d0 = ShopData(
    debtors = listOf(
      Debtor(id = "c1", name = "احمد کریمی", phone = "+93700123456", group = "همکار", priceList = CustomerCrm.PRICE_WHOLESALE),
      Debtor(id = "c2", name = "محمود", phone = "+93799000111", phone2 = "+93788555444", tags = listOf("عمده", "همسایه")),
      Debtor(id = "c3", name = "محمود", phone = "+93799000111", mergedInto = "c2"),
      Debtor(id = "c4", name = "زهرا", phone = "", tags = listOf("بدقول")),
    ),
  )

  private fun ok(r: DebtorEngine.Result): DebtorEngine.Result.Ok = r as DebtorEngine.Result.Ok
  private fun failed(r: DebtorEngine.Result): String = (r as DebtorEngine.Result.Failed).message

  @Test
  fun newCustomerSavesSecondPhoneAndTagsInOneStep() {
    val r = ok(CustomerProfileForm.save(d0, null, DebtorEngine.DebtorDraft("کریم", "0700999888"), "۰۷۹۹ ۱۱۱ ۲۲۲", "عمده، #همسایه، عمده", 7, ::newId))
    val c = r.data.debtors.single { it.id == r.id }
    assertEquals("کریم", c.name)
    assertEquals("+93799111222", CustomerCrm.phoneKey(c.phone2))
    assertEquals(listOf("عمده", "همسایه"), c.tags)
  }

  @Test
  fun editKeepsGroupAndPriceListAndCanClearTags() {
    val withTags = ok(CustomerProfileForm.setProfile(d0, "c1", "", "عمده")).data
    val r = ok(CustomerProfileForm.save(withTags, "c1", DebtorEngine.DebtorDraft("احمد کریمی", "+93700123456"), "0788000111", "", 7, ::newId))
    val c = r.data.debtors.single { it.id == "c1" }
    assertEquals("همکار", c.group)
    assertEquals(CustomerCrm.PRICE_WHOLESALE, c.priceList)
    assertNull(c.tags)
    assertEquals("+93788000111", CustomerCrm.phoneKey(c.phone2))
  }

  @Test
  fun rejectedProfileLeavesLedgerUntouched() {
    //  شمارهٔ دوم همان شمارهٔ اول ⇒ رد، و نامِ تازه هم ذخیره نمی‌شود (همه یا هیچ)
    val msg = failed(CustomerProfileForm.save(d0, "c1", DebtorEngine.DebtorDraft("نامِ تازه", "+93700123456"), "0700123456", "", 7, ::newId))
    assertTrue(msg, msg.contains("شمارهٔ اول"))
    assertEquals("احمد کریمی", d0.debtors.single { it.id == "c1" }.name)
    val tooMany = (1..CustomerCrm.MAX_TAGS + 1).joinToString("، ") { "ب$it" }
    failed(CustomerProfileForm.save(d0, null, DebtorEngine.DebtorDraft("تازه"), "", tooMany, 7, ::newId))
    //  ادغام‌شده ویرایش نمی‌شود
    failed(CustomerProfileForm.setProfile(d0, "c3", "", "عمده"))
  }

  @Test
  fun unchangedProfileIsTheSameLedger() {
    val r = ok(CustomerProfileForm.setProfile(d0, "c2", "+93788555444", "عمده، همسایه"))
    assertSame(d0, r.data)
  }

  @Test
  fun listHidesMergedAndCrmSearchesSecondPhoneAndTags() {
    fun ids(q: String, tag: String? = null, crm: Boolean = true) = CustomerProfileForm.visible(d0, q, tag, crm).map { it.id }
    assertEquals(listOf("c1", "c2", "c4"), ids(""))
    assertEquals(listOf("c1", "c2", "c4"), ids("", crm = false))
    //  شمارهٔ دوم، با رقمِ فارسی و 07
    assertEquals(listOf("c2"), ids("۰۷۸۸۵۵۵"))
    assertEquals(emptyList<String>(), ids("0788555", crm = false))
    //  برچسب: جست‌وجو با «#» و صافیِ برچسب
    assertEquals(listOf("c4"), ids("#بدقول"))
    assertEquals(listOf("c2"), ids("", tag = "همسایه"))
    assertEquals(listOf("c2"), ids("محمود", tag = "عمده"))
    assertEquals(emptyList<String>(), ids("زهرا", tag = "عمده"))
    //  خاموش: صافیِ برچسب بی‌اثر، همان جست‌وجوی امروز (نام و شمارهٔ اول)
    assertEquals(listOf("c1", "c2", "c4"), ids("", tag = "عمده", crm = false))
    assertEquals(listOf("c2"), ids("0799000", crm = false))
  }

  @Test
  fun extraLineShowsSecondPhoneAndTags() {
    assertEquals("", CustomerProfileForm.extraLine(d0.debtors[0]))
    assertTrue(CustomerProfileForm.extraLine(d0.debtors[1]).endsWith(" · #عمده #همسایه"))
    assertEquals("#بدقول", CustomerProfileForm.extraLine(d0.debtors[3]))
    assertEquals("عمده، همسایه", CustomerProfileForm.tagsText(d0.debtors[1]))
    assertEquals("", CustomerProfileForm.tagsText(null))
  }
}
