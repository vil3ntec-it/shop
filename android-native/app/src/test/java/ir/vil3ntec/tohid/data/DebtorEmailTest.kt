package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-11/CRM-13 — ایمیلِ قرض‌دار (`Debtor.email`): همان کلیدی که سرور برای «حسابِ من» و گروهِ تخفیفِ ویترین با ایمیلِ
 *  **تأییدشدهٔ** مشتری می‌سنجد (`store-account-history.debtorRowsOf`: `lower(data->>'email')`، تطبیقِ دقیق).
 *   • کوچک‌حرف و بی فاصلهٔ دو سر؛ همان شکلِ پذیرفتهٔ `validate.email` ِ سرور، وگرنه هیچ نوشتن و پیامِ روشن؛
 *   • خالی ⇒ پاک؛ `null` ِ فرمِ کامپیوتر ⇒ دست‌نخورده (کاربرِ بی «شمارهٔ مشتریان» کادر را ندارد)؛
 *   • ⛔ قرض‌دارِ بی ایمیل بایت‌به‌بایت همان (کلیدِ `email` نوشته نمی‌شود)؛
 *   • ادغام ایمیلِ ماندگار را مقدم نگه می‌دارد؛ «حذفِ دادهٔ مشتری» ایمیل را هم پاک می‌کند.
 */
class DebtorEmailTest {

  private val d0 = ShopData(
    debtors = listOf(
      Debtor(id = "c1", name = "احمد کریمی", phone = "+93700123456", group = "همکار"),
      Debtor(id = "c2", name = "محمود", phone = "+93799000111", email = "mahmood@shop.af"),
    ),
  )

  private fun ok(r: DebtorEngine.Result): DebtorEngine.Result.Ok = r as DebtorEngine.Result.Ok
  private fun failed(r: DebtorEngine.Result): String = (r as DebtorEngine.Result.Failed).message
  private fun email(d: ShopData, id: String) = d.debtors.first { it.id == id }.email

  @Test
  fun emailIsSavedLowercaseAndTrimmedWithTheRestOfTheProfile() {
    val r = ok(CustomerProfileForm.setProfile(d0, "c1", "", "عمده", "  Ahmad.Karimi@Mail.AF "))
    assertEquals("ahmad.karimi@mail.af", email(r.data, "c1"))
    assertEquals("همکار", r.data.debtors.first { it.id == "c1" }.group)
    assertEquals(listOf("عمده"), r.data.debtors.first { it.id == "c1" }.tags)
    //  همان هسته: پرونده با ایمیل
    val p = CustomerCrm.profileOf(r.data.debtors.first { it.id == "c1" })
    assertEquals("ahmad.karimi@mail.af", p.email)
  }

  @Test
  fun badEmailIsRejectedAndNothingIsWritten() {
    for (bad in listOf("ahmad", "ahmad@", "@mail.af", "ahmad@mail", "a b@mail.af", "a@mail..af", "a@@mail.af", "x".repeat(186) + "@m.af")) {
      val msg = failed(CustomerProfileForm.setProfile(d0, "c1", "", "", bad))
      assertTrue(bad, msg.isNotBlank())
      assertFalse(bad, CustomerCrm.emailOf(bad)?.let(CustomerCrm::emailOk) ?: true)
    }
    //  مرزِ ۱۹۰ نویسه همان سرور: ۱۹۰ پذیرفته، ۱۹۱ نه
    for (good in listOf("a@b.af", "first.last+shop@mail.co.uk", "x".repeat(185) + "@m.af")) {
      assertTrue(good, CustomerCrm.emailOk(CustomerCrm.emailOf(good)!!))
    }
  }

  @Test
  fun blankClearsAndNullKeepsTheSavedEmail() {
    val cleared = ok(CustomerProfileForm.setProfile(d0, "c2", "", "", "   "))
    assertNull(email(cleared.data, "c2"))
    val kept = ok(CustomerProfileForm.setProfile(d0, "c2", "", "", null))
    assertEquals("mahmood@shop.af", email(kept.data, "c2"))
    //  هیچ تغییر ⇒ همان دفتر (بی op)
    assertSame(d0, ok(CustomerProfileForm.setProfile(d0, "c2", "", "", "Mahmood@Shop.AF")).data)
  }

  @Test
  fun debtorWithoutEmailSerialisesExactlyAsBefore() {
    val json = Json { encodeDefaults = true }
    assertFalse(json.encodeToString(Debtor.serializer(), Debtor(id = "c1", name = "احمد")).contains("email"))
    assertTrue(json.encodeToString(Debtor.serializer(), Debtor(id = "c1", name = "احمد", email = "a@b.af")).contains("\"email\":\"a@b.af\""))
  }

  @Test
  fun mergeKeepsTheKeepersEmailFirstAndErasureClearsIt() {
    val keep = Debtor(id = "k", name = "علی", phone = "+93700000001")
    val drop = Debtor(id = "x", name = "علی", phone = "+93700000001", email = "ali@shop.af")
    val merged = (CustomerMerge.merge(ShopData(debtors = listOf(keep, drop)), "k", "x", "2026-11-15", 1L, { "a1" }) as CustomerMerge.Result.Ok).data
    assertEquals("ali@shop.af", email(merged, "k"))
    val both = (CustomerMerge.merge(ShopData(debtors = listOf(keep.copy(email = "keep@shop.af"), drop)), "k", "x", "2026-11-15", 1L, { "a1" }) as CustomerMerge.Result.Ok).data
    assertEquals("keep@shop.af", email(both, "k"))

    val onlyEmail = Debtor(id = "e", name = "کریم", email = "karim@shop.af")
    assertFalse("ایمیل دادهٔ شخصی است", CustomerErasureLedger.clean(onlyEmail))
    val scrubbed = CustomerErasureLedger.scrub(ShopData(debtors = listOf(onlyEmail)), setOf("e"))!!
    assertNull(email(scrubbed, "e"))
    assertTrue(CustomerErasureLedger.clean(scrubbed.debtors.single()))
  }
}
