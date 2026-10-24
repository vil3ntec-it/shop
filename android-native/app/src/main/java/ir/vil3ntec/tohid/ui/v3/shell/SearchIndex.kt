package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSearch
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.latinDigits
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.analytics.AnalyticsPaths
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths

/**
 *  جست‌وجوی برنامه — **یک** پیاده‌سازی برای جست‌وجوی گوشی (`search`) و فهرستِ فرمانِ
 *  کامپیوتر (`palette`). روی دادهٔ همین دستگاه، بی اینترنت هم. قاعده‌های تطبیق همان‌اند که
 *  بودند (`paletteSearch` و `invoiceSearch` ِ پیشین): کالا با نام یا بارکد، قرض‌دار با نام یا
 *  تلفن، فاکتور با شماره، نامِ مشتری یا مبلغِ نهایی؛ نوعِ نتیجهٔ تازه‌ای نیست.
 *
 *  هر نتیجه مسیرِ **همان سند** را دارد: کالا ⇐ `products/{id}`، قرض‌دار ⇐ `debtors/{id}`،
 *  فاکتور ⇐ `sales/{saleId}` (پیش‌تر فهرست‌ها). `match` بازهٔ متنِ پیداشده در عنوان است تا
 *  صفحه همان را برندرنگ کند. آزمون: `SearchIndexTest`.
 */
object SearchIndex {
  enum class Kind { PRODUCT, DEBTOR, INVOICE, SECTION }

  data class Hit(
    val kind: Kind,
    val id: String,
    val title: String,
    val hint: String?,
    val route: String,
    /** بازهٔ تطبیق در `title` (برای برجسته کردن)، یا null */
    val match: IntRange? = null,
  )

  /** یک بخشِ برنامه برای میان‌بُر؛ `keys` واژه‌های دیگرِ همان بخش (نامِ قدیم، شناسه) */
  data class Section(val route: String, val label: String, val keys: List<String> = emptyList())

  data class Group(val kind: Kind, val hits: List<Hit>, val total: Int)

  data class Results(val query: String, val groups: List<Group>) {
    val isEmpty: Boolean get() = groups.all { it.hits.isEmpty() }
    fun of(kind: Kind): Group? = groups.firstOrNull { it.kind == kind }
    /** نخستین سند (نه بخش) — Enter ِ صفحه‌کلید همین را باز می‌کند */
    val first: Hit? get() = groups.filter { it.kind != Kind.SECTION }.firstNotNullOfOrNull { it.hits.firstOrNull() }
  }

  /** بیشترِ نتیجه که از هر گروه ساخته می‌شود (شمارِ کل جدا شمرده می‌شود) */
  const val CAP = 60

  /**
   *  بخش‌های برنامه (همان فهرستِ پیشینِ «برو به…» با مسیرهای نوع‌دار). نوشته در زبانِ جاری؛
   *  دسترسی را صفحه با مسیریاب می‌سنجد.
   */
  fun sections(): List<Section> = listOf(
    Section(ShellPaths.HOME, Str.home, listOf("dashboard", Str.dashboard)),
    Section(SalesPaths.SALE, Str.sale, listOf("sale")),
    Section(SalesPaths.QUICK, StrSearch.quickSale, listOf("quick")),
    Section(MoneyPaths.DEBTORS, Str.debtors, listOf("debtors")),
    Section(ProductsPaths.LIST, Str.products, listOf("products", StrHome.tileProducts)),
    Section(StockPaths.LIST, Str.warehouse, listOf("warehouse", "stock")),
    Section(SalesPaths.HISTORY, Str.salesHistory, listOf("sales")),
    Section(MoneyPaths.PURCHASING, Str.purchasing, listOf("purchasing")),
    Section(AnalyticsPaths.REPORTS, Str.reports, listOf("reports")),
    Section(MoneyPaths.EXPENSES, Str.expenses, listOf("expenses")),
    Section(MoneyPaths.RECEIPTS, Str.receipts, listOf("receipts")),
    Section(AccountPaths.TEAM, Str.staff, listOf("team")),
    Section(AnalyticsPaths.audit(), Str.auditLog, listOf("audit")),
    Section(AnalyticsPaths.ADMIN, Str.adminCenter, listOf("admin")),
    Section(SettingsPaths.DEVICES, Str.devices, listOf("devices")),
    Section(SettingsPaths.ROOT, Str.settings, listOf("settings")),
    Section(AccountPaths.SUBSCRIPTION, Str.mySubscription, listOf("myplan", "subscription")),
    Section(AccountPaths.SUPPORT, Str.support, listOf("support")),
    Section(ShellPaths.notifications(), StrKit.notifications, listOf("notifications", Str.alerts)),
    Section("more", Str.more, listOf("more")),
  )

  /** همان یکسان‌سازیِ پیشین: رقمِ لاتین، ی/ک ِ فارسی، حروفِ کوچک — طول ثابت می‌ماند */
  fun norm(s: String): String = latinDigits(s).replace('ي', 'ی').replace('ك', 'ک').lowercase()

  /** بازهٔ `q` (یکسان‌شده) در `text`، یا null */
  fun matchIn(text: String, q: String): IntRange? {
    if (q.isEmpty()) return null
    val at = norm(text).indexOf(q)
    return if (at < 0 || at + q.length > text.length) null else at until at + q.length
  }

  fun search(d: ShopData, query: String, sections: List<Section> = sections()): Results {
    val q = norm(query).trim()
    val pages = sections.filter { s -> q.isEmpty() || norm(s.label).contains(q) || s.keys.any { norm(it).contains(q) } }
      .map { Hit(Kind.SECTION, it.route, it.label, null, it.route, matchIn(it.label, q)) }
    if (q.isEmpty()) return Results(query, listOf(Group(Kind.SECTION, pages, pages.size)))
    return Results(
      query,
      listOf(products(d, q), debtors(d, q), invoices(d, q), Group(Kind.SECTION, pages, pages.size)),
    )
  }

  /** بارکدِ اسکن‌شده: کالای ثبت‌شده ⇐ همان کالا؛ ناشناس ⇐ فرمِ کالای تازه با همین بارکد */
  fun scanTarget(d: ShopData, code: String): String =
    ShopStore.barcodeIndex(d)[code]?.let(ShellTargets::product) ?: ShellTargets.newProduct(code)

  private fun products(d: ShopData, q: String): Group {
    val stock = ShopStore.index(d)
    val found = d.products.filter { norm(it.name).contains(q) || it.barcodes.any { b -> b.contains(q) } }
    return Group(Kind.PRODUCT, found.take(CAP).map { p ->
      Hit(Kind.PRODUCT, p.id, p.name, StrSearch.productHint(money(stock.stock(p.id)), money(p.salePrice)), ShellTargets.product(p.id), matchIn(p.name, q))
    }, found.size)
  }

  private fun debtors(d: ShopData, q: String): Group {
    val found = d.debtors.filter { norm(it.name).contains(q) || norm(it.phone).contains(q) || LocaleText.phoneMatches(it.phone, q) }
    return Group(Kind.DEBTOR, found.take(CAP).map { b ->
      Hit(Kind.DEBTOR, b.id, b.name, StrSearch.debtorHint(money(ShopStore.debt(d, b.id))), ShellTargets.debtor(b.id), matchIn(b.name, q))
    }, found.size)
  }

  /** فاکتورها: شماره (`B1-154` یا `154`)، نامِ مشتری، یا مبلغِ نهایی — تازه‌ترین اول */
  private fun invoices(d: ShopData, q: String): Group {
    val names = d.debtors.associate { it.id to it.name }
    val digits = q.all { it.isDigit() || it == ',' }
    val found = d.sales.filter { s ->
      s.invoiceLabel()?.lowercase()?.contains(q) == true ||
        (s.debtorId?.let { names[it] }?.let { norm(it).contains(q) } == true) ||
        (digits && money(s.finalTotal).replace(",", "") == q.replace(",", ""))
    }.sortedByDescending { it.createdAt }
    return Group(Kind.INVOICE, found.take(CAP).map { s -> invoiceHit(s, names[s.debtorId], q) }, found.size)
  }

  private fun invoiceHit(s: Sale, customer: String?, q: String): Hit {
    val title = StrSearch.invoiceTitle(s.invoiceLabel() ?: "—")
    val hint = "${formatDate(s.date)} · ${money(s.finalTotal)} ؋" + (customer?.let { " · $it" } ?: "")
    return Hit(Kind.INVOICE, s.id, title, hint, ShellTargets.invoice(s.id), matchIn(title, q))
  }
}
