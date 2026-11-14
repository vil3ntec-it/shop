package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.audit.AuditAnchor
import ir.vil3ntec.tohid.core.audit.AuditChain
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths

/**
 *  سابقهٔ عملیات (`audit?kind=`): فیلترِ نوع، گروه‌بندیِ روزانه و مقصدِ هر ردیف — خالص،
 *  با آزمون (`AuditModelTest`). دفتر فقط خوانده می‌شود؛ هیچ ردیفی ساخته یا عوض نمی‌شود.
 */
object AuditModel {
  /** همهٔ نوع‌ها به ترتیبِ برگهٔ «نوعِ رویداد» (همان AUDIT_TYPE_LABELS ِ وب) */
  val KINDS = listOf(
    "sale", "cancel_sale", "return", "purchase", "customer_payment", "supplier_payment", "stock_adjustment",
    "supplier_return", "price_change", "product_edit", "shift_open", "shift_close", "delete_product",
    "debtor_delete", "debt_tx_delete", "expense_delete", "delete_entry", "data_export", "debt_due_set",
    "credit_over_limit", "credit_limit_set", "debt_tx_reverse",
  )

  /** پاک‌کردن‌ها — قرصِ قرمزِ «حذف» */
  val DELETIONS = setOf("delete_product", "debtor_delete", "debt_tx_delete", "expense_delete", "delete_entry")

  fun isDeletion(type: String): Boolean = type in DELETIONS

  /** شمارِ هر نوع در دفتر */
  fun counts(log: List<AuditEntry>): Map<String, Int> = log.groupingBy { it.type }.eachCount()

  /** پنج تراشهٔ سریع: پرتکرارترین نوع‌های موجود (برابر ⇒ ترتیبِ `KINDS`) */
  fun quickKinds(log: List<AuditEntry>, n: Int = 5): List<String> =
    counts(log).entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { order(it.key) })
      .take(n).map { it.key }

  /** همهٔ نوع‌ها برای برگه: نوع‌های شناخته + هر نوعِ ناشناخته‌ای که در دفتر هست */
  fun sheetKinds(log: List<AuditEntry>): List<Pair<String, Int>> {
    val c = counts(log)
    return (KINDS + c.keys.filter { it !in KINDS }.sorted()).map { it to (c[it] ?: 0) }
  }

  /** ردیف‌های یک نوع (null = همه)، تازه‌ترین اول */
  fun filter(log: List<AuditEntry>, kind: String?): List<AuditEntry> =
    log.filter { kind == null || it.type == kind }.sortedWith(compareByDescending<AuditEntry> { it.date }.thenByDescending { it.createdAt })

  /** گروهِ روزانه به ترتیبِ همان فهرست */
  fun byDay(rows: List<AuditEntry>): List<Pair<String, List<AuditEntry>>> =
    rows.groupBy { it.date }.toList()

  /**
   *  مقصدِ ردیف وقتی همان مورد هنوز در دفتر هست: فاکتور، کالا، قرض‌دار، تأمین‌کننده، انبارِ
   *  کالا یا صندوق. مورد رفته (پاک‌شده) ⇒ null و صفحه جزئیاتِ فقط‌خواندنی را نشان می‌دهد.
   */
  fun target(e: AuditEntry, d: ShopData): String? {
    val ref = e.refId
    return when (e.type) {
      "shift_open", "shift_close" -> SalesPaths.DRAWER
      else -> if (ref.isNullOrBlank()) null else resolve(e.type, ref, d)
    }
  }

  private fun resolve(type: String, ref: String, d: ShopData): String? {
    fun sale() = d.sales.firstOrNull { it.id == ref }?.let { SalesPaths.invoice(it.id) }
    fun product() = d.products.firstOrNull { it.id == ref }?.let { ProductsPaths.item(it.id) }
    fun debtor(id: String = ref) = d.debtors.firstOrNull { it.id == id }?.let { MoneyPaths.debtor(it.id) }
    fun supplier(id: String) = d.suppliers.firstOrNull { it.id == id }?.let { MoneyPaths.supplier(it.id) }
    return when (type) {
      "sale", "cancel_sale", "return" -> sale()
      "price_change", "product_edit" -> product()
      //  CRM-15: «دیدنِ پروندهٔ مشتری» ⇐ همان پرونده (خروجیِ همهٔ مشتریان `*` ⇐ هیچ)
      "customer_payment", "credit_over_limit", "credit_limit_set", "debt_tx_delete", "debt_tx_reverse", "customer_view" -> debtor()
      "debt_due_set" -> d.transactions.firstOrNull { it.id == ref }?.let { debtor(it.debtorId) }
      "purchase" -> d.purchases.firstOrNull { it.id == ref }?.let { supplier(it.supplierId) }
      "supplier_payment" -> d.supplierPayments.firstOrNull { it.id == ref }?.let { supplier(it.supplierId) }
      "stock_adjustment", "supplier_return" ->
        d.warehouseEntries.firstOrNull { it.id == ref }?.productId?.takeIf { id -> d.products.any { it.id == id } }?.let { StockPaths.item(it) }
      else -> null
    }
  }

  private fun order(type: String): Int = KINDS.indexOf(type).let { if (it < 0) KINDS.size else it }

  /* ───── STF-09: چه کسی و کجا، و سلامتِ زنجیرهٔ هش ───── */

  /** کاربر (نام اگر این دستگاه می‌شناسد، وگرنه شناسه)، نقش و دستگاه؛ ردیفِ کهنهٔ بی مُهر ⇐ «نامعلوم» */
  data class Who(val user: String, val role: String?, val device: String)

  fun who(e: AuditEntry, names: Map<String, String>, thisDevice: String): Who = Who(
    user = e.userId?.let { names[it]?.takeIf(String::isNotBlank) ?: it } ?: StrReports.unknown,
    role = e.userRole?.let(::roleName),
    device = deviceName(e.deviceId, thisDevice),
  )

  /** «علی (صندوق‌دار) · این دستگاه» */
  fun whoLine(w: Who): String = StrReports.whoLine(w.user + (w.role?.let { " ($it)" } ?: ""), w.device)

  fun deviceName(id: String?, thisDevice: String): String = when {
    id.isNullOrBlank() -> StrReports.unknown
    id == thisDevice -> StrReports.thisDevice
    else -> StrReports.deviceShort(id.orEmpty().take(6))
  }

  fun roleName(role: String): String = when (role.trim().lowercase()) {
    "owner" -> StrAccount.roleOwner
    "manager" -> StrAccount.roleManager
    "staff" -> StrAccount.roleStaff
    "seller" -> StrAccount.roleSeller
    "cashier" -> StrAccount.roleCashier
    "storekeeper" -> StrAccount.roleStorekeeper
    "picker" -> StrAccount.rolePicker
    "accountant" -> StrAccount.roleAccountant
    "courier" -> StrAccount.roleCourier
    else -> role
  }

  /** وضعیتِ زنجیره برای سرِ صفحه: [ok] = `null` ⇐ هنوز هیچ ردیفِ زنجیره‌داری نیست */
  data class ChainLine(val ok: Boolean?, val title: String, val body: String?)

  /**
   *  @param server حکمِ سرور (`AuditAnchor.Verdict`: رونوشتِ همگام‌شده و لنگرِ گزارش‌شدهٔ **همهٔ** دستگاه‌ها، STF-09)؛
   *  شکستِ همین دفتر جلوتر است، وگرنه شکستِ سرور با «به گفتهٔ سرور» (دُمِ بریدهٔ دستگاهِ دیگر، انشعاب، عقب‌گرد…).
   *  `null` (پرچمِ خاموش، بی‌اینترنت، بی اجازه) ⇐ فقط سنجشِ همین دستگاه، همان پیشین.
   */
  fun chainLine(r: AuditChain.Report, thisDevice: String, server: AuditAnchor.Verdict? = null): ChainLine {
    val b = r.broken
    val s = server?.broken
    fun where(x: AuditChain.Break) = StrReports.whoLine(reasonText(x.reason), deviceName(x.chain.substringBeforeLast('/', ""), thisDevice))
    return when {
      b != null -> ChainLine(false, StrReports.chainBroken(b.seq.coerceAtLeast(1)), where(b))
      s != null -> ChainLine(false, StrReports.chainServerBroken(s.seq.coerceAtLeast(1)), where(s))
      r.chained == 0 -> ChainLine(null, StrReports.chainNotStarted, null)
      else -> ChainLine(true, StrReports.chainOk, if (r.unverified > 0) StrReports.chainUnverified(r.unverified) else StrReports.chainOkBody)
    }
  }

  /** دلیلِ شکست به زبانِ صاحب؛ `deleted` و `rewritten` فقط از سرور می‌آیند (حذفِ نرم و بازنویسی پس از رسیدن) */
  fun reasonText(reason: String): String = when (reason) {
    "hash_mismatch", "bad_doc", "rewritten" -> StrReports.chainEdited
    "seq_gap", "truncated", "deleted" -> StrReports.chainDeleted
    else -> StrReports.chainReordered
  }

  /** شناسهٔ ردیفی که زنجیره از آن شکسته (نشانِ قرمز روی همان ردیف): همین دفتر، وگرنه سرور؛ ردیفِ پاک‌شده ⇐ null */
  fun brokenId(r: AuditChain.Report?, server: AuditAnchor.Verdict? = null): String? = r?.broken?.id ?: server?.broken?.id
}
