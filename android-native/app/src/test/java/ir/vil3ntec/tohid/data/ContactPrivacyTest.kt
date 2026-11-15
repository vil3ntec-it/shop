package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.ui.components.customerPhone
import ir.vil3ntec.tohid.ui.screens.DebtorListModel
import ir.vil3ntec.tohid.ui.v3.account.team.MemberModel
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  CRM-15 — «کمترین اطلاعاتِ شخصی، دسترسیِ محدود»: مجوزِ تازهٔ `customers.contact` (همان هستهٔ مشترکِ برنامه، وب و
 *  سرور؛ بردارهای `perm-v1.json`) — فقط نمایش. بی آن شمارهٔ مشتری پوشیده است (`ContactMask`) و تماس، پیامک و واتس‌اپ
 *  از پروندهٔ مشتری برداشته می‌شوند. سه نقشِ قدیم، صندوق‌دار و حسابدار مثلِ امروز؛ انباردار و جمع‌آور نه؛ صاحب برای هر
 *  نقش یا کاربر تنگ یا باز می‌کند (`edit = full`).
 */
class ContactPrivacyTest {

  private val phone = "0799123456"

  @Test fun `CRM-15 پیش‌فرضِ هر نقش و لایهٔ صاحب`() {
    val seen = mapOf(
      "owner" to true, "manager" to true, "staff" to true, "cashier" to true,
      "storekeeper" to false, "picker" to false, "accountant" to true,
    )
    for ((role, want) in seen) assertEquals(role, want, PermPolicy.allows(role, null, "customers.contact"))
    //  صاحب برای شاگرد می‌بندد و برای انباردار باز می‌کند؛ صاحب خودش هرگز بسته نمی‌شود
    val deny = """{"v":1,"layers":[{"customers.contact":{"mode":"deny"}}]}"""
    val allow = """{"v":1,"layers":[{"customers.contact":{"mode":"allow"}}]}"""
    assertFalse(PermPolicy.allows("staff", deny, "customers.contact"))
    assertTrue(PermPolicy.allows("storekeeper", allow, "customers.contact"))
    assertTrue(PermPolicy.allows("owner", deny, "customers.contact"))
    //  برچسبِ صفحهٔ کارمند (`GET /api/shop/permissions` کلید را می‌دهد)
    assertEquals(StrAccount.permContacts, MemberModel.permLabel("customers.contact"))
  }

  @Test fun `CRM-15 بی مجوز شماره پوشیده است و تماس و پیام از پرونده برداشته می‌شود`() {
    //  با مجوز همان امروز
    assertEquals(LocaleText.phoneDisplay(phone), customerPhone(phone, true))
    val open = DebtorLedgerModel.actions(phone, hasTransactions = true)
    assertTrue(open.containsAll(listOf(DebtorLedgerModel.Action.CALL, DebtorLedgerModel.Action.SMS, DebtorLedgerModel.Action.WHATSAPP)))
    assertTrue(DebtorLedgerModel.summary(phone, null).contains(LocaleText.phoneDisplay(phone)))

    //  بی مجوز: هیچ‌جا شمارهٔ کامل نیست؛ دو رقمِ آخر برای جدا کردنِ هم‌نام‌ها می‌ماند
    val masked = customerPhone(phone, false)
    assertFalse(masked, masked.contains("0799") || masked.contains("۰۷۹۹"))
    assertTrue(masked, masked.contains("•"))
    val closed = DebtorLedgerModel.actions(phone, hasTransactions = true, contacts = false)
    assertTrue(closed.none { it == DebtorLedgerModel.Action.CALL || it == DebtorLedgerModel.Action.SMS || it == DebtorLedgerModel.Action.WHATSAPP })
    assertTrue(closed.contains(DebtorLedgerModel.Action.STATEMENT_CSV))
    val sum = DebtorLedgerModel.summary(phone, null, contacts = false)
    assertFalse(sum, sum.contains(LocaleText.phoneDisplay(phone)))
    assertTrue(sum, sum.contains(masked))
    val row = DebtorListModel.Row(Debtor(id = "d1", name = "نوشا", phone = phone), 0.0, null)
    assertEquals(masked, DebtorListModel.subtitle(row, contacts = false))
    assertEquals(LocaleText.phoneDisplay(phone), DebtorListModel.subtitle(row))
    //  شمارهٔ دوم زیرِ نام (پرونده) همان قاعده؛ برچسب‌ها می‌مانند
    val two = Debtor(id = "d2", name = "رضا", phone2 = "0700111222", tags = listOf("عمده"))
    val line = CustomerProfileForm.extraLine(two, contacts = false)
    assertFalse(line, line.contains("0700") || line.contains("۰۷۰۰"))
    assertTrue(line, line.contains("#عمده"))
    assertTrue(CustomerProfileForm.extraLine(two).contains(LocaleText.phoneDisplay("0700111222")))
  }

  @Test fun `CRM-15 همهٔ صفحه‌های مشتریِ نسخهٔ ۳ و کامپیوتر شماره را از همان یک راه نشان می‌دهند`() {
    fun src(rel: String): String = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    val pages = listOf(
      "ui/v3/sales/checkout/CustomerPickerSheet.kt", "ui/v3/money/ReceivePickerSheet.kt", "ui/v3/money/DebtorMergeSheet.kt",
      "ui/screens/DeskCustomers.kt", "ui/screens/CrmProfileUi.kt", "ui/screens/PhoneDebtors.kt",
      "ui/v3/money/DebtorLedgerParts.kt", "ui/v3/money/DebtorLedgerScreen.kt",
      //  ردیفِ سادهٔ قرض‌داران (`claude/e-rows-63q`): شماره از `DebtorRowModel` با همان مجوز
      "ui/v3/money/DebtorsListScreen.kt",
    )
    for (p in pages) {
      val s = src(p)
      assertTrue(p, s.contains("rememberCanSeeContacts()"))
      //  شمارهٔ مشتری دیگر مستقیم از `phoneDisplay` نمی‌آید
      assertFalse(p, Regex("""phoneDisplay\((r\.phone|r\.debtor\.phone|m\.phone|debtor\.phone|row\.debtor\.phone|it\))""").containsMatchIn(s))
    }
    assertTrue(src("ui/screens/DeskCustomers.kt").contains("customerPhone(r.phone, ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts())"))
    //  مرزِ سکو فقط در فایلِ `*Android.kt` (iPhone/کامپیوتر همان شیم‌ها)
    assertTrue(src("ui/components/ContactPrivacyAndroid.kt").contains("ShopRole.allows(context, \"customers.contact\")"))
    //  CRM-15 نقش‌محور: خروجی‌ها و کادرِ ویرایشِ شماره هم از همان راه
    assertTrue(src("ui/screens/DeskCustomers.kt").contains("customerPhoneExport(s.phone, contacts)"))
    assertTrue(src("ui/v3/analytics/ReportsDesk.kt").contains("customerPhoneExport(it, contacts)"))
    assertTrue(src("ui/v3/analytics/ReportsScreen.kt").contains("customerPhoneExport(it, contacts)"))
    assertTrue(src("ui/v3/money/DebtorFormSheets.kt").contains("debtor == null || contacts || debtor.phone.isBlank()"))
    assertTrue(src("print/StatementImage.kt").contains("CustomerPrintPref.roleMask(context)"))
  }

  @Test fun `CRM-15 پرچمِ نقش‌محور — فقط صاحب و مدیر کامل، قالبِ سه رقم، اجازهٔ صریحِ صاحب می‌چربد`() {
    val on = """{"layers":[{},{}],"customerMask":true}"""
    assertTrue(PermPolicy.customerMask(on))
    assertFalse(PermPolicy.customerMask("""{"layers":[{},{}]}"""))
    assertFalse(PermPolicy.customerMask("not json"))
    val seen = mapOf(
      "owner" to true, "manager" to true, "staff" to false, "cashier" to false,
      "storekeeper" to false, "picker" to false, "accountant" to false,
    )
    for ((role, want) in seen) assertEquals(role, want, PermPolicy.allows(role, on, "customers.contact"))
    //  کلیدهای دیگر دست نمی‌خورند
    assertEquals(PermPolicy.allows("cashier", null, "sales.discount"), PermPolicy.allows("cashier", on, "sales.discount"))
    val grant = """{"layers":[{},{"customers.contact":{"mode":"allow"}}],"customerMask":true}"""
    assertTrue(PermPolicy.allows("cashier", grant, "customers.contact"))
    val closedManager = """{"layers":[{},{"customers.contact":{"mode":"deny"}}],"customerMask":true}"""
    assertFalse(PermPolicy.allows("manager", closedManager, "customers.contact"))

    val before = ShopRole.customerMask.value
    try {
      ShopRole.customerMask.value = true
      val masked = customerPhone(phone, false)
      assertEquals(LocaleText.phoneDisplay(phone).let { ir.vil3ntec.tohid.core.crm.CustomerMask.masked(it) }, masked)
      assertEquals(3, masked.count { it.isDigit() })
      assertEquals(LocaleText.phoneDisplay(phone), customerPhone(phone, true))
      assertEquals("•••• 456", ir.vil3ntec.tohid.ui.components.customerPhoneExport(phone, false))
      assertEquals(phone, ir.vil3ntec.tohid.ui.components.customerPhoneExport(phone, true))
      assertEquals(null, ir.vil3ntec.tohid.ui.components.customerAddress("کارتهٔ ۴", false))
      ShopRole.customerMask.value = false
      //  خاموش ⇒ همان امروز: خروجی کامل، صفحه دو رقمِ آخر
      assertEquals(phone, ir.vil3ntec.tohid.ui.components.customerPhoneExport(phone, false))
      assertEquals(2, customerPhone(phone, false).count { it.isDigit() })
    } finally {
      ShopRole.customerMask.value = before
    }
  }
}
