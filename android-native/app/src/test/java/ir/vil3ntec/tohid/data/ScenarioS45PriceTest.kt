package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.core.pos.PriceApproval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  سناریوی S45 (§۷.۵، STF-02/STF-03) — «هر عملِ ممنوع یا نیازمندِ تأیید پنجرهٔ درخواستِ تأیید باز می‌کند، نه پیامِ
 *  خطای مبهم. در سابقه، درخواست‌کننده و تأییدکننده هر دو ثبت می‌شوند.» — سمتِ برنامه:
 *   • تخفیف: `ApprovalDialog` با برگهٔ امضاشده (DiscountGateTest، #284)؛
 *   • فروشِ قرضی بیش از سقف: همان `CreditApprovalDialog` ِ S07 (پشتِ `creditGate`، #817) که حالا روی پنجرهٔ
 *     مشترکِ `PinApprovalDialog` است؛ سابقهٔ `credit_over_limit` با `by` (فروشنده) و `approvedBy`؛
 *   • تغییرِ قیمت (تازه): با «قیمت و تخفیفِ ردیف» ِ روشن، ردِ قاعدهٔ جهت یا سقفِ قلمِ آزاد در کادرِ ردیف دکمهٔ
 *     «درخواستِ تأیید» دارد (`PriceApproval`)، همان `PinApprovalDialog`، و پس از «ثبت» ردیفِ سابقهٔ
 *     `price_override` با درخواست‌کننده و تأییدکننده و دلیل (`LinePolicy.approvalAudit`). گوشیِ نسخهٔ ۳ از `claude/e-rows-31w`
 *     همان را دارد (`LineModel.check`/`apply`، `PinApprovalSheet`)؛ پنجرهٔ واقعی: ScenarioS45DeskUiTest و ScenarioS45PhoneUiTest.
 */
class ScenarioS45PriceTest {

  private fun source(rel: String): String = listOf(rel, "../$rel", "../../$rel", rel.removePrefix("android-native/app/"))
    .map { File(it) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")

  @Test
  fun `S45 سابقهٔ تغییرِ قیمت درخواست‌کننده و تأییدکننده و دلیل دارد`() {
    val g = PriceApproval.Grant(LinePricing.PRICE_DOWN_BLOCKED, "مدیر", "manager", "مشتریِ همیشگی", "علی", 10_000, 8_000)
    val row = LinePolicy.approvalAudit(g, "چای", "p-tea", 1_700_000_000_000, "2026-11-02")
    assertEquals(PriceApproval.AUDIT_TYPE, row.type)
    assertEquals("علی", row.by)
    assertEquals("مدیر", row.approvedBy)
    assertEquals("مشتریِ همیشگی", row.reason)
    assertEquals("p-tea", row.refId)
    assertEquals(80.0, row.amount, 0.0)
    assertTrue(row.notes, row.notes.contains("علی") && row.notes.contains("مدیر") && row.notes.contains("چای"))
  }

  @Test
  fun `S45 کادرِ ردیف به‌جای پیامِ رد درخواستِ تأیید دارد — کامپیوتر و گوشیِ نسخهٔ ۳ از یک در`() {
    val dialogs = source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt")
    //  یک در (`LinePolicy.checked`) با کدِ ردِ هسته — پیش‌تر متنِ رد به `askable` می‌رفت و دکمه هرگز دیده نمی‌شد (ScenarioS45DeskUiTest)
    assertTrue(dialogs.contains("ir.vil3ntec.tohid.data.LinePolicy.checked("))
    assertTrue(dialogs.contains("StrTill.askApproval"))
    assertTrue(dialogs.contains("ir.vil3ntec.tohid.ui.components.staff.PinApprovalDialog("))
    assertTrue(source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/DeskSale.kt").contains("priceAsk = remember(id) { ir.vil3ntec.tohid.data.LinePolicyPrefs.ask("))
    //  گوشیِ نسخهٔ ۳ (`claude/e-rows-31w`): مسیرِ `sale/line/{i}` همان سیاست و همان در را می‌گیرد (ScenarioS45PhoneTest، ScenarioS45PhoneUiTest)
    val route = source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/sales/cart/CartSheetRoutes.kt")
    assertTrue(route.contains("LinePolicyPrefs.ask(context, store, priceAudit"))
    assertTrue(route.contains("LineModel.apply(cart.lines.value, info, form, start, policy, grant)"))
    val sheet = source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/sales/cart/LineSheet.kt")
    assertTrue(sheet.contains("LineModel.check(policy, form, info, start, grant, canAsk = priceAsk != null)"))
    assertTrue(sheet.contains("PinApprovalSheet("))
  }

  @Test
  fun `S45 کدِ رد همراهِ متنِ رد است`() {
    val upOnly = LinePricing.Policy(on = true, direction = LinePricing.Direction.UP)
    val d = LinePolicy.edit(upOnly, false, 100.0, 100.0, 80.0, 1.0, true, "")
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, d.code)
    assertTrue(PriceApproval.askable(upOnly, d.code))
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, LinePolicy.checked(upOnly, false, 100.0, 100.0, 80.0, 1.0, true, "", null, canAsk = true).askCode)
  }

  @Test
  fun `S45 فروشِ قرضی همان پنجرهٔ مشترکِ PIN است`() {
    val credit = source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/components/staff/CreditApprovalDialog.kt")
    //  عنوان پارامترِ پیش‌فرضِ creditOverTitle است (S22 همین پنجره را با عنوانِ «فروشِ منقضی» هم می‌خواند)
    assertNotNull(Regex("""fun CreditApprovalDialog\([\s\S]*?title: String = StrPos\.creditOverTitle,\s*\) = PinApprovalDialog\(\s+title""").find(credit))
    assertTrue(credit.contains("ApproverVault.verify(r, pin)"))
  }
}
