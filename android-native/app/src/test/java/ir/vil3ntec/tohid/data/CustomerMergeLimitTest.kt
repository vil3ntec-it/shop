package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.i18n.StrSaleCaps
import ir.vil3ntec.tohid.core.perm.CreditLimitPerm
import ir.vil3ntec.tohid.money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CRM-03 / §۱۰.۱ — ادغامِ دو مشتری (CRM-10) هم پیش از نوشتن می‌پرسد «سقفِ اعتبارِ ماندگار عوض می‌شود؟»: ماندگارِ بی سقف
 *  سقفِ تکراری را می‌گیرد (`CustomerMerge.fold`)، یعنی همان تغییری که سرور با `shop.credit_limit_perm` رد می‌کرد و ادغام
 *  بی‌صدا به صندوقِ تعارض می‌رفت. قاعده همان `CreditLimitGate` ِ برگهٔ مشتری است؛ خاموش/صاحب ⇒ همان امروز.
 */
class CustomerMergeLimitTest {

  private val key = CreditLimitPerm.PERM
  private val denyRules = CreditLimitPerm.Rules("staff", listOf(mapOf(key to mapOf("mode" to "deny"))), enforced = true)
  private val capRules = CreditLimitPerm.Rules("cashier", listOf(mapOf(key to mapOf("caps" to mapOf("maxAmount" to 500_000.0)))), enforced = true)

  private fun ledger(keepLimit: Double?, dropLimit: Double?) = ShopData(
    debtors = listOf(
      Debtor(id = "k", name = "نصیر", phone = "0700000001", creditLimit = keepLimit),
      Debtor(id = "x", name = "نصیر", phone = "0700000001", creditLimit = dropLimit),
    ),
  )

  private fun merge(d: ShopData, rules: CreditLimitPerm.Rules) = CustomerMerge.merge(d, "k", "x", rules, "2026-11-12", 1L) { "a" }

  @Test fun mergeThatGivesTheKeptCustomerALimitIsRefusedWithoutThePermission() {
    val d = ledger(keepLimit = null, dropLimit = 1000.0)
    val r = merge(d, denyRules)
    assertEquals(CustomerMerge.Result.Failed(StrLedger.mergeChangesLimit("نصیر", StrSaleCaps.limitLocked)), r)
    //  تا سقفِ نقش (۵۰۰۰) پذیرفته؛ بیشتر نه
    assertTrue(merge(d, capRules) is CustomerMerge.Result.Ok)
    val big = merge(ledger(null, 9000.0), capRules)
    assertEquals(CustomerMerge.Result.Failed(StrLedger.mergeChangesLimit("نصیر", StrSaleCaps.limitOverCap(money(5000.0)))), big)
  }

  @Test fun mergeKeepsTodayWhenTheLimitDoesNotChangeOrTheRuleIsOff() {
    //  ماندگار سقف دارد ⇒ سقفش همان می‌ماند ⇒ حتی با «ممنوع» ادغام می‌شود
    assertTrue(merge(ledger(2000.0, 1000.0), denyRules) is CustomerMerge.Result.Ok)
    //  هیچ‌کدام سقف ندارند
    assertTrue(merge(ledger(null, null), denyRules) is CustomerMerge.Result.Ok)
    //  پرچمِ خاموش و صاحبِ دکان ⇒ همان امروز
    assertTrue(merge(ledger(null, 1000.0), CreditLimitPerm.Rules.OFF) is CustomerMerge.Result.Ok)
    assertTrue(merge(ledger(null, 1000.0), denyRules.copy(role = "owner")) is CustomerMerge.Result.Ok)
    assertTrue(CustomerMerge.merge(ledger(null, 1000.0), "k", "x", "2026-11-12", 1L, { "a" }) is CustomerMerge.Result.Ok)
  }

  @Test fun everyMergeDoorPassesTheShopRules() {
    fun src(rel: String) = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    assertTrue(src("data/ShopStore.kt").contains("CustomerMerge.merge(d, keepId, dropId, limitRules)"))
    assertTrue(src("ui/screens/CrmProfileUi.kt").contains("store.mergeCustomers(p.keep.id, p.drop.id, limitRules)"))
    assertTrue(src("ui/v3/money/MoneyRoutes.kt").contains("MoneyWriter.merge(store, toasts, context, keepId, dropId, limitRules)"))
  }
}
