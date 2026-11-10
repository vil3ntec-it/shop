package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.calendar.ShopWeek
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrMarketPages
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSettingsPages
import ir.vil3ntec.tohid.core.i18n.StrShrink
import ir.vil3ntec.tohid.core.i18n.StrStockPages
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import kotlin.math.roundToLong

/**
 *  متن‌ها و فرمِ صفحهٔ «تخفیف‌ها» — خالص، بی Compose، تا آزموده شود
 *  (`PromoTextTest`). همان پیام‌های `server/src/routes/shop-promos.js`.
 */
object PromoText {

  val KINDS: List<Pair<String, String>> get() = listOf(
    "percent" to StrSale.percentWord,
    "amount" to StrMarketPages.kindAmount,
    "price" to StrMarketPages.kindPrice,
    "tiers" to StrMarketPages.kindTiers,
    "bxgy" to StrMarketPages.kindBxgy,
    "bundle" to StrMarketPages.kindBundle,
    "threshold" to StrMarketPages.kindThreshold,
    "expiry" to StrMarketPages.kindExpiry,
  )

  /** PRM-10 — نوع‌های فرم: «نزدیکِ انقضا» فقط با پرچمِ `expiry` ِ سرور (یا قاعده‌ای که از پیش چنین است) */
  fun kinds(expiry: Boolean): List<Pair<String, String>> = if (expiry) KINDS else KINDS.filter { it.first != "expiry" }
  val STACKS: List<Pair<String, String>> get() = listOf(
    "best" to StrMarketPages.stackBest,
    "combine" to StrMarketPages.stackCombine,
    "exclusive" to StrSettingsPages.stackExclusive,
  )
  val STACK_HINTS: Map<String, String> get() = mapOf(
    "best" to StrMarketPages.hintBest,
    "combine" to StrMarketPages.hintCombine,
    "exclusive" to StrMarketPages.hintExclusive,
  )
  /** روزهای هفته به ترتیبِ دکان (آغازِ هفتهٔ «تاریخ و رقم»، پیش‌فرض شنبه اول؛ LOC-03) ⇒ شمارهٔ ISO */
  val DAYS: List<Pair<Long, String>> get() = days(ir.vil3ntec.tohid.ShopDayChoice.weekStart)
  fun days(weekStart: Int): List<Pair<Long, String>> =
    ShopWeek.order(weekStart).map { it.toLong() to StrReports.weekdaysShort[it - 1] }
  val ERRORS: Map<String, String> get() = mapOf(
    "id" to StrMarketPages.errId, "name" to StrMarketPages.errName, "kind" to StrMarketPages.errKind,
    "stack" to StrMarketPages.errStack, "priority" to StrMarketPages.errPriority,
    "code" to StrMarketPages.errCode, "uses" to StrMarketPages.errUses,
    "channel" to StrMarketPages.errChannel, "window" to StrMarketPages.errWindow,
    "bp" to StrMarketPages.errBp, "amount" to StrMarketPages.errAmount,
    "prices" to StrMarketPages.errPrices, "tiers" to StrMarketPages.errTiers,
    "bxgy" to StrMarketPages.errBxgy,
    "bundle" to StrMarketPages.errBundle,
    "expiry" to StrMarketPages.errExpiry,
  )

  fun kindLabel(k: String): String = KINDS.firstOrNull { it.first == k }?.second ?: k
  fun stackLabel(s: String): String = STACKS.firstOrNull { it.first == s }?.second ?: s
  fun errors(codes: List<String>): String = codes.joinToString("؛ ") { ERRORS[it] ?: it }

  private fun pct(bp: Long): String {
    val v = bp / 100.0
    return (if (v == Math.floor(v)) v.roundToLong().toString() else v.toString()) + "٪"
  }
  private fun af(minor: Long): String = money(minor / 100.0)
  private fun qty(milli: Long): String {
    val v = milli / 1000.0
    return if (v == Math.floor(v)) v.roundToLong().toString() else v.toString()
  }

  /** «۱۰٪»، «بخر ۲ بگیر ۱ رایگان»، … */
  fun valueText(r: PromoCore.Rule): String = when (r.kind) {
    "percent" -> pct(r.bp)
    "amount" -> StrMarketPages.amountEach(af(r.amountMinor))
    "price" -> r.prices.firstOrNull()?.let { StrLine.tierUnitText(af(it.unitMinor)) } ?: "—"
    "tiers" -> r.tiers.joinToString("، ") { "${qty(it.min)}+ ⇐ " + if (it.unitMinor >= 0) StrLine.tierUnitText(af(it.unitMinor)) else pct(it.bp) }
    "bxgy" -> StrMarketPages.buyGet(r.buy, r.get) + if (r.bp >= 10_000) StrMarketPages.freeWord else StrMarketPages.withPctOff(pct(r.bp))
    "bundle" -> StrMarketPages.bundleFor(r.qty, af(r.priceMinor))
    "threshold" -> r.tiers.joinToString("، ") { t -> StrMarketPages.fromAmount(af(t.min)) + if (t.amountMinor > 0) "${af(t.amountMinor)} ؋" else pct(t.bp) }
    "expiry" -> StrMarketPages.nearExpiryText(pct(r.bp), r.nearDays)
    else -> "—"
  }

  fun scopeText(r: PromoCore.Rule, d: ShopData): String {
    fun name(id: String) = d.products.firstOrNull { it.id == id }?.name ?: StrShrink.deletedProduct
    val s = r.scope
    val parts = buildList {
      if (s.categories.isNotEmpty()) add(StrMarketPages.categoryPrefix + s.categories.joinToString("، "))
      if (s.brands.isNotEmpty()) add(StrMarketPages.brandPrefix + s.brands.joinToString("، "))
      if (s.suppliers.isNotEmpty()) add(StrMarketPages.supplierPrefix + s.suppliers.joinToString("، ") { supplierName(it, d) })
      val ids = if (r.kind == "price") r.prices.map { it.productId } else s.products
      if (ids.isNotEmpty()) add(if (ids.size == 1) name(ids[0]) else Str.itemsCount(ids.size))
    }
    var t = if (parts.isEmpty()) StrStockPages.allItems else parts.joinToString(" + ")
    if (s.exProducts.isNotEmpty()) t += StrMarketPages.exceptOf(if (s.exProducts.size == 1) name(s.exProducts[0]) else Str.itemsCount(s.exProducts.size))
    if (s.exBrands.isNotEmpty()) t += StrMarketPages.exceptBrand(s.exBrands.joinToString("، "))
    if (s.exCategories.isNotEmpty()) t += StrMarketPages.exceptCategory(s.exCategories.joinToString("، "))
    if (s.exSuppliers.isNotEmpty()) t += StrMarketPages.exceptSupplier(s.exSuppliers.joinToString("، ") { supplierName(it, d) })
    //  PRM-11: قاعدهٔ «فقط این گروه‌های مشتری»
    if (r.groups.isNotEmpty()) t += " · " + ir.vil3ntec.tohid.core.i18n.StrLine.promoOnlyGroups(r.groups.joinToString("، "))
    return t
  }

  /** PRM-08 — نامِ تأمین‌کننده از شناسه؛ حذف‌شده ⇒ برچسبِ ثابت (شناسه به کاربر نشان داده نمی‌شود) */
  fun supplierName(id: String, d: ShopData): String = d.suppliers.firstOrNull { it.id == id }?.name?.trim()?.ifEmpty { null } ?: StrMarketPages.deletedMark

  fun hm(m: Long): String = if (m < 0) "" else "${(m / 60 % 24).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"

  fun windowText(r: PromoCore.Rule): String {
    val w = r.window
    return buildList {
      if (w.days.isNotEmpty()) add(DAYS.filter { it.first in w.days }.joinToString("، ") { it.second })
      if (w.start >= 0 && w.end >= 0) add("${hm(w.start)}–${hm(w.end)}")
      if (w.from.isNotEmpty() || w.to.isNotEmpty()) add(StrReports.rangeSpan(w.from.ifEmpty { "…" }, w.to.ifEmpty { "…" }))
    }.joinToString(" · ").ifEmpty { StrMarketPages.always }
  }

  enum class Status(private val text: () -> String) { ACTIVE({ StrSettings.active }), OFF({ StrProducts.filterInactive }), ENDED({ StrMarketPages.ended }), SCHEDULED({ StrMarketPages.scheduled }); val label: String get() = text() }

  fun status(r: PromoCore.Rule, today: String): Status = when {
    !r.active -> Status.OFF
    r.window.to.isNotEmpty() && r.window.to < today -> Status.ENDED
    r.window.from.isNotEmpty() && r.window.from > today -> Status.SCHEDULED
    else -> Status.ACTIVE
  }

  enum class Tab(private val text: () -> String) { ALL({ Str.all }), ACTIVE({ StrSettings.active }), OFF({ StrProducts.filterInactive }); val label: String get() = text() }

  fun filter(rules: List<PromoCore.Rule>, query: String, tab: Tab, today: String): List<PromoCore.Rule> {
    val q = ir.vil3ntec.tohid.normalizeDigits(query).trim().lowercase()
    return rules.filter { r ->
      val okTab = when (tab) {
        Tab.ALL -> true
        Tab.ACTIVE -> status(r, today) == Status.ACTIVE
        Tab.OFF -> status(r, today) != Status.ACTIVE
      }
      okTab && (q.isEmpty() || r.name.lowercase().contains(q) || r.code.lowercase().contains(q))
    }
  }
}

/**
 *  فرمِ ویرایشگر — هر کادر یک رشته، همان‌طور که کاربر می‌نویسد. `toRule` آن را
 *  به قاعده برمی‌گرداند و `PromoCore.validate` داوری می‌کند (نه این‌جا).
 */
data class PromoForm(
  val name: String = "",
  val kind: String = "percent",
  val pct: String = "10",
  val amount: String = "",
  val priceProduct: String = "",
  val priceUnit: String = "",
  /** هر پله: آستانه (تعداد یا افغانی) و تخفیف (درصد یا افغانی) */
  val tiers: List<Pair<String, String>> = listOf("" to ""),
  /** آستانهٔ مبلغ: تخفیفِ هر پله افغانی است، نه درصد؛ پله‌ای بر تعداد (POS-19): قیمتِ ثابتِ هر واحد به افغانی */
  val tierAmount: Boolean = false,
  val buy: String = "2",
  val get: String = "1",
  val bundleQty: String = "3",
  val bundlePrice: String = "",
  /** PRM-10 — «چند روز پیش از انقضا» */
  val nearDays: String = "7",
  val categories: Set<String> = emptySet(),
  /** PRM-08: برندهای شامل و مستثنا (PRD-06، `Product.brand`) */
  val brands: Set<String> = emptySet(),
  val exBrands: Set<String> = emptySet(),
  /** PRM-08: دسته‌های مستثنا و تأمین‌کنندگانِ شامل/مستثنا (شناسهٔ `Supplier`) */
  val exCategories: Set<String> = emptySet(),
  val suppliers: Set<String> = emptySet(),
  val exSuppliers: Set<String> = emptySet(),
  val products: Set<String> = emptySet(),
  val exProducts: Set<String> = emptySet(),
  /** PRM-11 — فقط مشتریانِ این گروه‌ها (`Debtor.group`، CRM-08)؛ خالی = همه */
  val groups: Set<String> = emptySet(),
  val days: Set<Long> = emptySet(),
  val start: String = "",
  val end: String = "",
  val from: String = "",
  val to: String = "",
  val stack: String = "best",
  val priority: String = "0",
  val code: String = "",
  val active: Boolean = true,
  val allowBelowCost: Boolean = false,
  /**
   *  PRM-02 — «سقفِ مصرفِ کد»؛ `null` = فرم نشانش نمی‌دهد (پرچمِ `redeem` ِ سرور خاموش) و سقفِ قاعدهٔ پایه
   *  دست نمی‌خورد (همان رفتارِ پیشین)؛ خالی = بی سقف.
   */
  val maxUses: String? = null,
) {
  companion object {
    private fun num(d: Double): String = if (d == Math.floor(d)) d.roundToLong().toString() else d.toString()

    /** @param uses پرچمِ `redeem` ِ سرور روشن است ⇒ فرم «سقفِ مصرف» را نشان می‌دهد */
    fun of(r: PromoCore.Rule, uses: Boolean = false): PromoForm = PromoForm(
      name = r.name,
      kind = r.kind.ifEmpty { "percent" },
      pct = if (r.kind == "bxgy" || r.kind == "percent" || r.kind == "expiry") num(r.bp / 100.0) else "10",
      amount = if (r.amountMinor > 0) num(r.amountMinor / 100.0) else "",
      priceProduct = r.prices.firstOrNull()?.productId.orEmpty(),
      priceUnit = r.prices.firstOrNull()?.let { num(it.unitMinor / 100.0) }.orEmpty(),
      tiers = if (r.tiers.isEmpty()) listOf("" to "") else r.tiers.map { t ->
        val min = if (r.kind == "threshold") num(t.min / 100.0) else num(t.min / 1000.0)
        val v = if (t.unitMinor >= 0) num(t.unitMinor / 100.0) else if (t.amountMinor > 0) num(t.amountMinor / 100.0) else num(t.bp / 100.0)
        min to v
      },
      tierAmount = (r.kind == "threshold" && r.tiers.any { it.amountMinor > 0 }) || PromoCore.tierPrice(r),
      buy = if (r.buy > 0) r.buy.toString() else "2",
      get = if (r.get > 0) r.get.toString() else "1",
      bundleQty = if (r.qty > 0) r.qty.toString() else "3",
      bundlePrice = if (r.priceMinor > 0) num(r.priceMinor / 100.0) else "",
      nearDays = if (r.nearDays > 0) r.nearDays.toString() else "7",
      categories = r.scope.categories.toSet(),
      brands = r.scope.brands.toSet(),
      exBrands = r.scope.exBrands.toSet(),
      exCategories = r.scope.exCategories.toSet(),
      suppliers = r.scope.suppliers.toSet(),
      exSuppliers = r.scope.exSuppliers.toSet(),
      products = r.scope.products.toSet(),
      exProducts = r.scope.exProducts.toSet(),
      groups = r.groups.toSet(),
      days = r.window.days.toSet(),
      start = PromoText.hm(r.window.start),
      end = PromoText.hm(r.window.end),
      from = r.window.from,
      to = r.window.to,
      stack = r.stack,
      priority = r.priority.toString(),
      code = r.code,
      active = r.active,
      allowBelowCost = r.allowBelowCost,
      maxUses = if (!uses) null else if (r.maxUses > 0) r.maxUses.toString() else "",
    )

    /** «۱۴:۳۰» ⇒ دقیقه؛ خالی ⇒ -1؛ نادرست ⇒ -2 (که `validate` رد می‌کند) */
    fun minutes(s: String): Long {
      val t = ir.vil3ntec.tohid.normalizeDigits(s).trim()
      if (t.isEmpty()) return -1
      val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(t) ?: return -2
      val h = m.groupValues[1].toLong()
      val mi = m.groupValues[2].toLong()
      if (h > 24 || mi > 59 || (h == 24L && mi > 0)) return -2
      return h * 60 + mi
    }
  }

  private fun dbl(s: String): Double? = ir.vil3ntec.tohid.parseNumber(s)
  private fun keep(now: Set<String>, was: List<String>): List<String> = if (now == was.toSet()) was else now.sorted()
  private fun minor(s: String): Long = dbl(s)?.let { (it * 100).roundToLong() } ?: -1
  private fun bp(s: String): Long = dbl(s)?.let { (it * 100).roundToLong() } ?: 0
  private fun int(s: String, dflt: Long): Long = dbl(s)?.takeIf { it == Math.floor(it) }?.roundToLong() ?: dflt

  /**
   *  فرم ⇒ قاعده، روی `base` (شناسه، کانال و شمارِ مصرف از همان می‌مانند؛ گروه‌ها از فرم، PRM-11).
   *  قاعدهٔ تازه فقط برای صندوق است (`channels = ["pos"]` را خودِ `base` دارد).
   *  ⛔ هر چه فرم نشان نمی‌دهد دست نمی‌خورد (N2): کانالِ خالی (= همه) خالی می‌ماند،
   *  و از «قیمتِ ویژه»ی چندکالایی فقط ردیفِ نخست (همان که فرم دارد) عوض می‌شود.
   */
  fun toRule(base: PromoCore.Rule): PromoCore.Rule {
    var s = minutes(start)
    var e = minutes(end)
    //  «تا ۰۰:۰۰» در بازهٔ همان روز یعنی تا نیمه‌شب
    if (e == 0L && s > 0) e = 1440
    if (s == -1L && e >= 0) s = -2
    if (e == -1L && s >= 0) e = -2
    val tierRows = tiers.filter { it.first.isNotBlank() || it.second.isNotBlank() }
    return base.copy(
      name = name.trim(),
      kind = kind,
      active = active,
      stack = stack,
      priority = int(priority, 0),
      code = code.trim(),
      allowBelowCost = allowBelowCost,
      channels = base.channels,
      //  PRM-11: گروه‌های دست‌نخورده همان ترتیبِ پیشین را نگه می‌دارند (N2)
      groups = if (groups == base.groups.toSet()) base.groups else groups.sorted(),
      scope = base.scope.copy(
        categories = categories.sorted(),
        brands = brands.sorted(),
        exBrands = exBrands.sorted(),
        //  PRM-08: دست‌نخورده ⇒ همان ترتیبِ پیشین (N2)
        exCategories = keep(exCategories, base.scope.exCategories),
        suppliers = keep(suppliers, base.scope.suppliers),
        exSuppliers = keep(exSuppliers, base.scope.exSuppliers),
        products = if (kind == "price") base.scope.products.takeIf { base.kind == "price" }.orEmpty() else products.sorted(),
        exProducts = exProducts.sorted(),
      ),
      window = PromoCore.Window(
        from = from.trim(), to = to.trim(), days = days.sorted(), start = s, end = e,
        except = base.window.except,
      ),
      bp = when (kind) { "percent", "bxgy", "expiry" -> bp(pct); else -> 0 },
      amountMinor = if (kind == "amount") minor(amount) else 0,
      prices = if (kind != "price") emptyList() else {
        val first = if (priceProduct.isNotEmpty()) listOf(PromoCore.Price(priceProduct, minor(priceUnit))) else emptyList()
        //  بقیهٔ کالاهای «قیمتِ ویژه» که فرم نشان نمی‌دهد، همان‌طور که بودند
        val rest = if (base.kind == "price") base.prices.drop(1).filter { it.productId != priceProduct } else emptyList()
        first + rest
      },
      tiers = when (kind) {
        "tiers" -> tierRows.map { (a, b) ->
          val min = dbl(a)?.let { (it * 1000).roundToLong() } ?: -1
          if (tierAmount) PromoCore.Tier(min = min, unitMinor = minor(b)) else PromoCore.Tier(min = min, bp = bp(b))
        }
        "threshold" -> tierRows.map { (a, b) ->
          if (tierAmount) PromoCore.Tier(min = minor(a), amountMinor = minor(b)) else PromoCore.Tier(min = minor(a), bp = bp(b))
        }
        else -> emptyList()
      },
      //  سقف فقط برای کد شمرده می‌شود (دفترِ مصرفِ سرور)؛ نادرست ⇒ -1 تا `validate` بگیرد («uses»)
      maxUses = when {
        maxUses == null -> base.maxUses
        code.isBlank() || maxUses.isBlank() -> 0L
        else -> int(maxUses, -1L)
      },
      buy = if (kind == "bxgy") int(buy, 0) else 0,
      get = if (kind == "bxgy") int(get, 0) else 0,
      qty = if (kind == "bundle") int(bundleQty, 0) else 0,
      priceMinor = if (kind == "bundle") minor(bundlePrice) else 0,
      nearDays = if (kind == "expiry") int(nearDays, 0) else 0,
    )
  }
}
