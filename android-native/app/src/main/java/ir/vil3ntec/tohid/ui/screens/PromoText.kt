package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import kotlin.math.roundToLong

/**
 *  متن‌ها و فرمِ صفحهٔ «تخفیف‌ها» — خالص، بی Compose، تا آزموده شود
 *  (`PromoTextTest`). همان پیام‌های `server/src/routes/shop-promos.js`.
 */
object PromoText {

  val KINDS: List<Pair<String, String>> = listOf(
    "percent" to "درصدی",
    "amount" to "مبلغ از هر واحد",
    "price" to "قیمتِ ویژه",
    "tiers" to "پله‌ای بر تعداد",
    "bxgy" to "بخر X بگیر Y",
    "bundle" to "بسته با قیمتِ ثابت",
    "threshold" to "آستانهٔ مبلغِ خرید",
  )
  val STACKS: List<Pair<String, String>> = listOf(
    "best" to "بهترین قیمت",
    "combine" to "ترکیبی",
    "exclusive" to "انحصاری",
  )
  val STACK_HINTS: Map<String, String> = mapOf(
    "best" to "از میانِ «بهترین قیمت»ها فقط پرسودترین برای مشتری",
    "combine" to "روی بقیه هم اعمال می‌شود",
    "exclusive" to "اگر اعمال شود، هیچ تخفیفِ دیگری نه",
  )
  /** روزهای هفته به ترتیبِ دکان (شنبه اول) ⇒ شمارهٔ ISO */
  val DAYS: List<Pair<Long, String>> = listOf(
    6L to "شنبه", 7L to "یکشنبه", 1L to "دوشنبه", 2L to "سه‌شنبه", 3L to "چهارشنبه", 4L to "پنجشنبه", 5L to "جمعه",
  )
  val ERRORS: Map<String, String> = mapOf(
    "id" to "شناسه لازم است", "name" to "نام لازم است", "kind" to "نوعِ تخفیف را انتخاب کنید",
    "stack" to "شیوهٔ ترکیب نادرست است", "priority" to "اولویت باید بینِ ‎-۱۰۰۰ و ۱۰۰۰ باشد",
    "code" to "کد باید ۳ تا ۳۲ حرف یا رقمِ لاتین باشد", "uses" to "سقفِ مصرف نادرست است",
    "channel" to "کانال نادرست است", "window" to "بازهٔ زمانی نادرست است (از ≤ تا، ساعتِ شروع ≠ پایان)",
    "bp" to "درصد باید بیش از ۰ و حداکثر ۱۰۰ باشد", "amount" to "مبلغ باید مثبت و افغانیِ کامل باشد",
    "prices" to "کالا و قیمتِ ویژه را بدهید", "tiers" to "پله‌ها باید صعودی باشند — هم آستانه، هم تخفیف",
    "bxgy" to "«بخر» و «بگیر» دست‌کم ۱، تخفیفِ بخشِ «بگیر» بینِ ۱ تا ۱۰۰٪",
    "bundle" to "بسته دست‌کم ۲ قلم و قیمتِ افغانیِ کامل",
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
    "amount" -> "${af(r.amountMinor)} ؋ از هر واحد"
    "price" -> r.prices.firstOrNull()?.let { "${af(it.unitMinor)} ؋ هر واحد" } ?: "—"
    "tiers" -> r.tiers.joinToString("، ") { "${qty(it.min)}+ ⇐ ${pct(it.bp)}" }
    "bxgy" -> "بخر ${r.buy} بگیر ${r.get}" + if (r.bp >= 10_000) " رایگان" else " با ${pct(r.bp)} تخفیف"
    "bundle" -> "${r.qty} تا ${af(r.priceMinor)} ؋"
    "threshold" -> r.tiers.joinToString("، ") { t -> "از ${af(t.min)} ⇐ " + if (t.amountMinor > 0) "${af(t.amountMinor)} ؋" else pct(t.bp) }
    else -> "—"
  }

  fun scopeText(r: PromoCore.Rule, d: ShopData): String {
    fun name(id: String) = d.products.firstOrNull { it.id == id }?.name ?: "کالای حذف‌شده"
    val s = r.scope
    val parts = buildList {
      if (s.categories.isNotEmpty()) add("دستهٔ " + s.categories.joinToString("، "))
      val ids = if (r.kind == "price") r.prices.map { it.productId } else s.products
      if (ids.isNotEmpty()) add(if (ids.size == 1) name(ids[0]) else "${ids.size} کالا")
    }
    var t = if (parts.isEmpty()) "همهٔ کالاها" else parts.joinToString(" + ")
    if (s.exProducts.isNotEmpty()) t += " (جز ${if (s.exProducts.size == 1) name(s.exProducts[0]) else "${s.exProducts.size} کالا"})"
    return t
  }

  fun hm(m: Long): String = if (m < 0) "" else "${(m / 60 % 24).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"

  fun windowText(r: PromoCore.Rule): String {
    val w = r.window
    return buildList {
      if (w.days.isNotEmpty()) add(DAYS.filter { it.first in w.days }.joinToString("، ") { it.second })
      if (w.start >= 0 && w.end >= 0) add("${hm(w.start)}–${hm(w.end)}")
      if (w.from.isNotEmpty() || w.to.isNotEmpty()) add("${w.from.ifEmpty { "…" }} تا ${w.to.ifEmpty { "…" }}")
    }.joinToString(" · ").ifEmpty { "همیشه" }
  }

  enum class Status(val label: String) { ACTIVE("فعال"), OFF("غیرفعال"), ENDED("تمام‌شده"), SCHEDULED("زمان‌بندی‌شده") }

  fun status(r: PromoCore.Rule, today: String): Status = when {
    !r.active -> Status.OFF
    r.window.to.isNotEmpty() && r.window.to < today -> Status.ENDED
    r.window.from.isNotEmpty() && r.window.from > today -> Status.SCHEDULED
    else -> Status.ACTIVE
  }

  enum class Tab(val label: String) { ALL("همه"), ACTIVE("فعال"), OFF("غیرفعال") }

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
  /** آستانهٔ مبلغ: تخفیفِ هر پله افغانی است، نه درصد */
  val tierAmount: Boolean = false,
  val buy: String = "2",
  val get: String = "1",
  val bundleQty: String = "3",
  val bundlePrice: String = "",
  val categories: Set<String> = emptySet(),
  val products: Set<String> = emptySet(),
  val exProducts: Set<String> = emptySet(),
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
      pct = if (r.kind == "bxgy" || r.kind == "percent") num(r.bp / 100.0) else "10",
      amount = if (r.amountMinor > 0) num(r.amountMinor / 100.0) else "",
      priceProduct = r.prices.firstOrNull()?.productId.orEmpty(),
      priceUnit = r.prices.firstOrNull()?.let { num(it.unitMinor / 100.0) }.orEmpty(),
      tiers = if (r.tiers.isEmpty()) listOf("" to "") else r.tiers.map { t ->
        val min = if (r.kind == "threshold") num(t.min / 100.0) else num(t.min / 1000.0)
        val v = if (t.amountMinor > 0) num(t.amountMinor / 100.0) else num(t.bp / 100.0)
        min to v
      },
      tierAmount = r.kind == "threshold" && r.tiers.any { it.amountMinor > 0 },
      buy = if (r.buy > 0) r.buy.toString() else "2",
      get = if (r.get > 0) r.get.toString() else "1",
      bundleQty = if (r.qty > 0) r.qty.toString() else "3",
      bundlePrice = if (r.priceMinor > 0) num(r.priceMinor / 100.0) else "",
      categories = r.scope.categories.toSet(),
      products = r.scope.products.toSet(),
      exProducts = r.scope.exProducts.toSet(),
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
  private fun minor(s: String): Long = dbl(s)?.let { (it * 100).roundToLong() } ?: -1
  private fun bp(s: String): Long = dbl(s)?.let { (it * 100).roundToLong() } ?: 0
  private fun int(s: String, dflt: Long): Long = dbl(s)?.takeIf { it == Math.floor(it) }?.roundToLong() ?: dflt

  /**
   *  فرم ⇒ قاعده، روی `base` (شناسه، کانال، شمارِ مصرف و گروه‌ها از همان می‌مانند).
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
      scope = base.scope.copy(
        categories = categories.sorted(),
        products = if (kind == "price") base.scope.products.takeIf { base.kind == "price" }.orEmpty() else products.sorted(),
        exProducts = exProducts.sorted(),
      ),
      window = PromoCore.Window(
        from = from.trim(), to = to.trim(), days = days.sorted(), start = s, end = e,
        except = base.window.except,
      ),
      bp = when (kind) { "percent", "bxgy" -> bp(pct); else -> 0 },
      amountMinor = if (kind == "amount") minor(amount) else 0,
      prices = if (kind != "price") emptyList() else {
        val first = if (priceProduct.isNotEmpty()) listOf(PromoCore.Price(priceProduct, minor(priceUnit))) else emptyList()
        //  بقیهٔ کالاهای «قیمتِ ویژه» که فرم نشان نمی‌دهد، همان‌طور که بودند
        val rest = if (base.kind == "price") base.prices.drop(1).filter { it.productId != priceProduct } else emptyList()
        first + rest
      },
      tiers = when (kind) {
        "tiers" -> tierRows.map { (a, b) -> PromoCore.Tier(min = dbl(a)?.let { (it * 1000).roundToLong() } ?: -1, bp = bp(b)) }
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
    )
  }
}
