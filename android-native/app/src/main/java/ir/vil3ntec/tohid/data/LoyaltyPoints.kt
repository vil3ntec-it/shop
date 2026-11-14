package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.promo.Loyalty
import kotlin.math.roundToLong

/**
 *  PRM-13 — امتیازِ وفاداری روی دفترِ دکان. خالص: نه فایل، نه ساعت، نه Android. قاعده‌ها در هستهٔ `core/promo/Loyalty`؛
 *  این‌جا فقط دفتر ⇄ رویداد، سبد و قلاب‌های ثبت/برگشت/لغو.
 *
 *  ── بی جدولِ تازه و بی مهاجرت (همان الگوی کارتِ هدیه و سفارشِ ویژه) ─────────────
 *  ردیف‌های سابقه ([EARN]/[SPEND]/[EARN_BACK]/[SPEND_BACK])، هر کدام یک `Loyalty.Event`: شناسهٔ ردیف = شناسهٔ رویداد،
 *  `refId` = فاکتور (برگشت و لغو هم همان فاکتور)، `payRef` = شناسهٔ مشتری، `points` = شمارِ امتیاز (همیشه مثبت)،
 *  `amount` = ارزشِ پولیِ مصرف (افغانی؛ کسب صفر). هیچ ردیفی ویرایش نمی‌شود؛ مانده از رویدادها ([balance]).
 *  مشتری همان هویتِ پروندهٔ مشتری است (`Debtor.id`، با ادغامِ CRM-10 ⇒ مشتریِ ماندگار، `CustomerCrm.resolve`).
 *
 *  ── مصرف = تخفیفِ شبیهِ پرداخت، نه `Sale.discount` ─────────────────────────────
 *  Sync v1/v3 فاکتورِ شاگرد با `discount > 0` را رد می‌کند؛ پس مصرف مثلِ تخفیفِ خودکار (`PromoEngine`) قیمتِ ردیف‌ها
 *  را پایین می‌آورد ([apply]): ارزشِ امتیاز به نسبتِ مبلغِ ردیف‌ها (عددِ صحیح، بیشترین باقی‌مانده) پخش می‌شود، پس
 *  جمعِ ردیف‌ها = جمعِ فاکتور، سود از همان قیمتِ واحد، و برگشت سهمِ خودش را از همان ردیف می‌گیرد.
 *
 *  ── هم‌اتم و بی تکرار ─────────────────────────────────────────────────
 *  ردیف‌های فاکتور در همان دفترِ نتیجهٔ `SalesEngine.record` اضافه می‌شوند ([post]) — همه یا هیچ؛ شناسه‌ها از شناسهٔ
 *  فاکتور (`<فاکتور>-lp-s`/`-lp-e`)، برگشت از سندِ برگشت (`<سند>-lp-*`) و لغو `<فاکتور>-lx-*`؛ تلاشِ دوباره هرگز
 *  ردیفِ دوم نمی‌سازد. مصرف روی دفترِ همان لحظه سنجیده می‌شود: یک دستگاه هرگز مانده را منفی نمی‌کند.
 *
 *  ⛔ خاموش ([post] با `on = false`)، بی مشتری یا فاکتورِ بی ردیفِ امتیاز ⇒ **همان شیء**: صندوق، فاکتور، برگشت، لغو و
 *  همگام‌سازی بایت‌به‌بایت همان امروز.
 *
 *  ── انقضا و اصلاحِ دستی ─────────────────────────────────────────────
 *  ماندهٔ هر سنجش [balanceAt] است: با [Loyalty.Rule.expiryMonths] ِ قاعده (۰ ⇒ همان جمعِ ساده) و انقضای FIFO ِ هسته، از
 *  خودِ دفتر و بی هیچ ردیفِ «سوخت». برگشت و لغو قاعده را از `LoyaltyRules.current` (قاعدهٔ در کارِ دکان) می‌گیرند.
 *  [adjust] = ردیفِ فقط‌افزودنیِ [ADJUST] با علامت و دلیل، فقط صاحب/مدیر، با شناسهٔ کار (`lpa-<کار>`؛ تلاشِ دوباره ردیفِ
 *  دوم نمی‌سازد) و هرگز زیرِ صفر. (داوریِ نقشِ سرور برای `auditLog` در #802/#805 هنوز مرج نشده.)
 */
object LoyaltyPoints {

  const val EARN = "loyalty_earn"
  const val SPEND = "loyalty_spend"
  const val EARN_BACK = "loyalty_earn_back"
  const val SPEND_BACK = "loyalty_spend_back"
  const val ADJUST = "loyalty_adjust"

  private val TYPE = mapOf(
    Loyalty.EARN to EARN, Loyalty.SPEND to SPEND, Loyalty.EARN_BACK to EARN_BACK, Loyalty.SPEND_BACK to SPEND_BACK,
    Loyalty.ADJUST to ADJUST,
  )
  private val KIND = TYPE.entries.associate { (k, v) -> v to k }

  /** پنجرهٔ سطح: ۳۶۵ روزِ پیش از فاکتور */
  const val TIER_WINDOW_MS = 365L * 24 * 60 * 60 * 1000

  fun isRow(a: AuditEntry): Boolean = a.type in KIND

  fun eventOf(a: AuditEntry): Loyalty.Event? {
    val kind = KIND[a.type] ?: return null
    val points = a.points ?: 0L
    return Loyalty.Event(
      a.id, kind, a.payRef.orEmpty(),
      if (kind == Loyalty.ADJUST) points.coerceIn(-Loyalty.MAX_POINTS, Loyalty.MAX_POINTS) else points.coerceAtLeast(0),
      a.refId.orEmpty(), a.createdAt,
    )
  }

  /** رویدادها با مشتریِ ماندگار (پس از ادغام) */
  fun events(d: ShopData): List<Loyalty.Event> {
    val byId = d.debtors.associateBy { it.id }
    return d.auditLog.mapNotNull(::eventOf).map { e -> if (e.customer.isEmpty()) e else e.copy(customer = CustomerCrm.resolve(byId, e.customer)) }
  }

  fun customerOf(d: ShopData, debtorId: String?): String? = debtorId?.takeIf { it.isNotBlank() }?.let { CustomerCrm.resolve(d, it) }

  fun balance(d: ShopData, customer: String): Long = Loyalty.balance(events(d), CustomerCrm.resolve(d, customer))

  /** مانده در لحظهٔ [now] با انقضای [rule]؛ بی انقضا ⇒ همان [balance] */
  fun balanceAt(d: ShopData, customer: String, rule: Loyalty.Rule, now: Long): Long =
    Loyalty.balanceAt(events(d), CustomerCrm.resolve(d, customer), rule, now)

  /** خریدِ ۱۲ ماهِ پیش از [now] (افغانی): فاکتورهای ثبت‌شدهٔ همین مشتری منهای برگشت‌هایشان؛ [except] بیرون */
  fun spendAfn(d: ShopData, customer: String, now: Long, except: String? = null): Long {
    val byId = d.debtors.associateBy { it.id }
    val root = CustomerCrm.resolve(byId, customer)
    val from = now - TIER_WINDOW_MS
    val sales = d.sales.filter { s ->
      s.id != except && s.status != "cancelled" && s.createdAt in from until now &&
        s.debtorId?.let { CustomerCrm.resolve(byId, it) } == root
    }
    if (sales.isEmpty()) return 0
    val ids = sales.mapTo(HashSet()) { it.id }
    val back = d.saleReturns.filter { it.saleId in ids }.sumOf { PayFlow.minor(it.amount) }
    return ((sales.sumOf { PayFlow.minor(it.finalTotal) } - back).coerceAtLeast(0)) / 100
  }

  /** [soon] امتیازی که تا ۳۰ روزِ دیگر می‌سوزد و [soonAt] نخستین لحظه‌اش (بی انقضا ⇒ ۰ و `null`) */
  data class View(val customer: String, val balance: Long, val tier: Loyalty.Tier, val spendAfn: Long, val soon: Long = 0, val soonAt: Long? = null)

  fun view(d: ShopData, rule: Loyalty.Rule, debtorId: String, now: Long): View {
    val c = CustomerCrm.resolve(d, debtorId)
    val spend = spendAfn(d, c, now)
    if (rule.expiryMonths <= 0) return View(c, balance(d, c), Loyalty.tier(rule, spend), spend)
    val st = Loyalty.state(events(d), c, rule, now)
    return View(c, st.balance, Loyalty.tier(rule, spend), spend, st.soon, st.soonAt)
  }

  fun tierName(key: String): String = when (key) {
    Loyalty.GOLD -> StrLoyalty.tierGold
    Loyalty.SILVER -> StrLoyalty.tierSilver
    else -> StrLoyalty.tierBronze
  }

  /** «۱٫۲۵» از ۱۲۵ درصد */
  fun multText(pct: Int): String = ir.vil3ntec.tohid.qty(pct / 100.0)

  // ── صندوق: مصرفِ امتیاز روی سبد ───────────────────────────────────
  /**
   *  @param baseMinor جمعِ سبد پیش از امتیاز (پس از تخفیفِ خودکار) — سقفِ سهم از همین
   *  @param netMinor جمعِ سبد پس از امتیاز — `Sale.total` ِ همان فاکتور باید همین باشد
   */
  data class Plan(val customer: String, val points: Long, val valueMinor: Long, val baseMinor: Long, val netMinor: Long)

  data class Applied(val cart: List<SalesEngine.CartLine>, val plan: Plan)

  /** مبلغِ هر ردیف (افغانیِ کامل) — همان حسابِ `SalesEngine.cartTotal` */
  private fun lineAfn(d: ShopData, line: SalesEngine.CartLine): Long {
    val v = if (line.free) SalesEngine.lineTotal(line.unitPrice ?: 0.0, line.quantity)
    else d.products.find { it.id == line.productId }?.let { SalesEngine.lineTotal(line.unitPrice ?: ProductPlansMap.price(it), line.quantity) } ?: 0.0
    return if (v.isNaN() || v.isInfinite() || v <= 0) 0 else v.roundToLong()
  }

  /**
   *  مصرفِ [points] امتیازِ [debtorId] روی [cart] ⇒ همان سبد با قیمتِ پایین‌آمدهٔ ردیف‌ها و [Plan] ِ ثبت.
   *  امتیازِ نادرست، بیش از مانده/سهم یا سبدی که پخش در آن دقیق درنمی‌آید ⇒ `null` (سبد همان).
   */
  fun apply(d: ShopData, cart: List<SalesEngine.CartLine>, rule: Loyalty.Rule, debtorId: String?, points: Long, now: Long = 0L): Applied? {
    if (points <= 0 || cart.isEmpty()) return null
    val customer = customerOf(d, debtorId) ?: return null
    val lines = cart.map { if (it.quantity > 0) lineAfn(d, it) else 0L }
    val baseAfn = lines.sum()
    val spend = Loyalty.spend(rule, balanceAt(d, customer, rule, now), baseAfn * 100, points) as? Loyalty.Spend.Ok ?: return null
    if (spend.valueMinor % 100 != 0L) return null
    val valueAfn = spend.valueMinor / 100
    val shares = Loyalty.split(lines, valueAfn) ?: return null
    val out = cart.mapIndexed { i, line ->
      if (shares[i] == 0L) line else line.copy(unitPrice = (lines[i] - shares[i]).toDouble() / line.quantity)
    }
    val net = baseAfn - valueAfn
    //  ممیزِ شناور: هر ردیف باید دقیقاً همان مبلغ را بدهد، وگرنه هیچ
    if (out.indices.any { i -> lineAfn(d, out[i]) != lines[i] - shares[i] } || SalesEngine.cartTotal(d, out) != net.toDouble()) return null
    return Applied(out, Plan(customer, spend.points, spend.valueMinor, baseAfn * 100, net * 100))
  }

  /** بیشترین امتیازِ قابلِ مصرف روی [cart] برای [debtorId] (با انقضا در [now]) */
  fun capFor(d: ShopData, cart: List<SalesEngine.CartLine>, rule: Loyalty.Rule, debtorId: String?, now: Long = 0L): Long {
    val customer = customerOf(d, debtorId) ?: return 0
    val base = cart.sumOf { if (it.quantity > 0) lineAfn(d, it) else 0L }
    return Loyalty.cap(rule, balanceAt(d, customer, rule, now), base * 100)
  }

  // ── ثبتِ فاکتور ───────────────────────────────────────────────────
  private fun row(
    id: String, kind: String, customer: String, points: Long, valueMinor: Long, saleId: String, notes: String,
    today: String, now: Long, by: String?,
  ) = AuditEntry(
    id = id, type = TYPE.getValue(kind), date = today, refId = saleId, notes = notes, createdAt = now,
    amount = PayFlow.major(valueMinor), by = by?.trim()?.ifEmpty { null }, payRef = customer, points = points,
  )

  private fun label(s: Sale): String = s.invoiceLabel() ?: "0"
  private fun afn(minor: Long): String = ir.vil3ntec.tohid.money(PayFlow.major(minor))

  /**
   *  پس از `SalesEngine.record` (و `PromoEngine.stamp`): مصرفِ [plan] و کسبِ امتیازِ همین فاکتور در همان دفتر.
   *  خاموش، ثبتِ ناموفق، یا بی مشتری و بی مصرف ⇒ همان شیء. مصرفِ ناهمخوان (مشتریِ دیگر، سبدِ عوض‌شده، بیش از مانده یا
   *  سهم) ⇒ `Failed` و هیچ چیز نوشته نمی‌شود.
   */
  fun post(r: SalesEngine.Result, on: Boolean, rule: Loyalty.Rule, plan: Plan?, by: String?): SalesEngine.Result {
    if (r !is SalesEngine.Result.Ok) return r
    //  سبدِ پایین‌آمده بی دفترِ امتیاز هرگز ثبت نمی‌شود
    if (!on) return if (plan != null && plan.points > 0) SalesEngine.Result.Failed(StrLoyalty.errRule) else r
    val d = r.data
    val sale = d.sales.lastOrNull { it.id == r.saleId } ?: return r
    val customer = customerOf(d, sale.debtorId)
    //  همان روز و لحظهٔ فاکتور — سطح هم از ۱۲ ماهِ پیش از همین فاکتور
    val today = sale.date
    val now = sale.createdAt
    val rows = ArrayList<AuditEntry>()
    if (plan != null && plan.points > 0) {
      if (customer == null || customer != CustomerCrm.resolve(d, plan.customer)) return SalesEngine.Result.Failed(StrLoyalty.errCustomer)
      if (PayFlow.minor(sale.total) != plan.netMinor) return SalesEngine.Result.Failed(StrLoyalty.errChanged)
      when (val s = Loyalty.spend(rule, balanceAt(d, customer, rule, now), plan.baseMinor, plan.points)) {
        is Loyalty.Spend.Bad -> return SalesEngine.Result.Failed(message(s.code))
        is Loyalty.Spend.Ok -> {
          if (s.valueMinor != plan.valueMinor || plan.baseMinor - plan.netMinor != s.valueMinor) return SalesEngine.Result.Failed(StrLoyalty.errChanged)
          rows += row(
            "${sale.id}-lp-s", Loyalty.SPEND, customer, s.points, s.valueMinor, sale.id,
            "مصرفِ ${s.points} امتیاز — فاکتور #${label(sale)} — ${afn(s.valueMinor)} افغانی", today, now, by,  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
          )
        }
      }
    }
    if (customer != null && rule.valid) {
      val paid = minOf(PayFlow.minor(sale.paidAmount), PayFlow.minor(sale.finalTotal)).coerceAtLeast(0)
      val tier = Loyalty.tier(rule, spendAfn(d, customer, now, except = sale.id))
      val pts = Loyalty.earn(rule, tier, paid)
      if (pts > 0) rows += row(
        "${sale.id}-lp-e", Loyalty.EARN, customer, pts, 0, sale.id,
        "کسبِ $pts امتیاز (${tierName(tier.key)} ×${multText(tier.multiplierPct)}) — فاکتور #${label(sale)}", today, now, by,  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
      )
    }
    val fresh = rows.filter { n -> d.auditLog.none { it.id == n.id } }
    if (fresh.isEmpty()) return r
    return r.copy(data = d.copy(auditLog = d.auditLog + fresh))
  }

  fun message(code: String): String = when (code) {
    Loyalty.Code.OVER_BALANCE -> StrLoyalty.errBalance
    Loyalty.Code.OVER_SHARE -> StrLoyalty.errShare
    Loyalty.Code.BAD_RULE -> StrLoyalty.errRule
    else -> StrLoyalty.errChanged
  }

  // ── برگشت و لغو ────────────────────────────────────────────────
  private class OfSale(val customer: String, val earned: Long, val earnedBack: Long, val spent: Long, val spentBack: Long)

  private fun ofSale(d: ShopData, saleId: String): OfSale? {
    val ev = events(d).filter { it.saleId == saleId }
    val first = ev.firstOrNull { it.kind == Loyalty.EARN || it.kind == Loyalty.SPEND } ?: return null
    return OfSale(
      first.customer, Loyalty.total(ev, saleId, Loyalty.EARN), Loyalty.total(ev, saleId, Loyalty.EARN_BACK),
      Loyalty.total(ev, saleId, Loyalty.SPEND), Loyalty.total(ev, saleId, Loyalty.SPEND_BACK),
    )
  }

  /** بخشِ برگشته از فاکتور: پولِ برگشت‌ها از پولِ فاکتور؛ فاکتورِ صفر ⇒ مقدارِ قلم‌ها (هزارم) */
  private fun returned(d: ShopData, sale: Sale): Pair<Long, Long> {
    val whole = PayFlow.minor(sale.finalTotal)
    if (whole > 0) return d.saleReturns.filter { it.saleId == sale.id }.sumOf { PayFlow.minor(it.amount) } to whole
    val items = d.saleItems.filter { it.saleId == sale.id }
    return items.sumOf { (it.returnedQty * 1000).roundToLong() } to items.sumOf { (it.quantity * 1000).roundToLong() }
  }

  private fun reversalRows(
    d: ShopData, sale: Sale, of: OfSale, rv: Loyalty.Reversal, key: String, why: String, today: String, now: Long, by: String?,
  ): List<AuditEntry> = buildList {
    if (rv.spendBack > 0) {
      val value = Loyalty.valueMinor(Loyalty.Rule(pointAfn = valuePer(d, sale.id)), rv.spendBack)
      add(row("$key-s", Loyalty.SPEND_BACK, of.customer, rv.spendBack, value, sale.id,
        "برگشتِ ${rv.spendBack} امتیازِ مصرف‌شده — $why — فاکتور #${label(sale)}", today, now, by))  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
    }
    if (rv.earnBack > 0) add(row("$key-e", Loyalty.EARN_BACK, of.customer, rv.earnBack, 0, sale.id,
      "پس‌گرفتنِ ${rv.earnBack} امتیاز — $why — فاکتور #${label(sale)}" +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        if (rv.shortfall > 0) " (${rv.shortfall} امتیاز پیش‌تر خرج شده بود)" else "", today, now, by))  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
  }.filter { n -> d.auditLog.none { it.id == n.id } }

  /** ماندهٔ برگشت/لغو: با انقضای قاعدهٔ در کار (`LoyaltyRules.current`؛ وصل‌نشده ⇒ بی انقضا، همان جمعِ ساده) */
  private fun roomOf(d: ShopData, customer: String, now: Long): Long =
    Loyalty.balanceAt(events(d), customer, LoyaltyRules.current().rule, now)

  /** ارزشِ هر امتیاز هنگامِ مصرفِ همین فاکتور (افغانی) — بازپرداختِ مصرف به همان نرخ، نه نرخِ امروز */
  private fun valuePer(d: ShopData, saleId: String): Long {
    val s = d.auditLog.firstOrNull { it.type == SPEND && it.refId == saleId } ?: return 1
    val pts = s.points ?: 0L
    val minor = PayFlow.minor(s.amount)
    return if (pts > 0 && minor % (pts * 100) == 0L) (minor / (pts * 100)).coerceAtLeast(1) else 1
  }

  /**
   *  پس از برگشتِ قلم‌های فاکتورِ [saleId] ([before] ⇒ [after]): سهمِ تجمعیِ برگشت از امتیازِ کسب‌شده پس گرفته و از
   *  امتیازِ مصرف‌شده به مشتری برمی‌گردد. [docKey] = سندِ برگشت (`ReturnFlow`)؛ `null` ⇒ نخستین ردیفِ برگشتِ تازه.
   *  فاکتورِ بی ردیفِ امتیاز ⇒ همان شیء.
   */
  fun afterReturn(before: ShopData, after: ShopData, saleId: String?, docKey: String?, today: String, now: Long, by: String?): ShopData {
    if (saleId == null || after === before) return after
    val of = ofSale(after, saleId) ?: return after
    val sale = after.sales.find { it.id == saleId } ?: return after
    val key = docKey?.takeIf { it.isNotBlank() }
      ?: after.saleReturns.firstOrNull { r -> r.saleId == saleId && before.saleReturns.none { it.id == r.id } }?.id
      ?: return after
    val (part, whole) = returned(after, sale)
    val rv = Loyalty.reverse(of.earned, of.earnedBack, of.spent, of.spentBack, part, whole, roomOf(after, of.customer, now))
    val rows = reversalRows(after, sale, of, rv, "$key-lp", "برگشت", today, now, by)  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
    return if (rows.isEmpty()) after else after.copy(auditLog = after.auditLog + rows)
  }

  fun afterReturn(before: ShopData, r: SalesEngine.Result, saleId: String, today: String, now: Long, by: String?): SalesEngine.Result =
    if (r !is SalesEngine.Result.Ok) r else afterReturn(before, r.data, saleId, null, today, now, by).let { if (it === r.data) r else r.copy(data = it) }

  /** پس از `SalesEngine.cancel`: هرچه از امتیازِ این فاکتور هنوز برنگشته (کسب پس، مصرف به مشتری) */
  fun afterCancel(r: SalesEngine.Result, today: String, now: Long, by: String?): SalesEngine.Result {
    if (r !is SalesEngine.Result.Ok) return r
    val d = r.data
    val of = ofSale(d, r.saleId) ?: return r
    val sale = d.sales.find { it.id == r.saleId } ?: return r
    val rv = Loyalty.reverseAll(of.earned, of.earnedBack, of.spent, of.spentBack, roomOf(d, of.customer, now))
    val rows = reversalRows(d, sale, of, rv, "${sale.id}-lx", "لغوِ فاکتور", today, now, by)  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
    return if (rows.isEmpty()) r else r.copy(data = d.copy(auditLog = d.auditLog + rows))
  }

  // ── کسب روی پرداختِ بعدیِ نسیه ─────────────────────────────────────
  /**
   *  PRM-13 — «کسب = پولِ پرداخت‌شده»: سهمِ نسیهٔ فاکتور هنگامِ ثبت امتیاز نمی‌گیرد ([post] فقط `paidAmount`)؛ پرداختِ بعدیِ
   *  همان مشتری (رسیدِ دستیِ «دریافت»، `kind` تهی — نه برگشت، لغو، اعتبار یا امانی) آن را می‌گیرد. روی دفترِ پس از رسید:
   *
   *   • همهٔ پرداخت‌های دستیِ مشتری (هر زمان) با FIFO ِ زمان روی بدهی‌هایش پخش می‌شوند: نسیهٔ بازِ هر فاکتور
   *     (`debtGiven − debtSettled`؛ برگشت و لغو آن را کم کرده‌اند) و بردِ دستی (`give` با `kind` تهی) — پس پولی که بدهیِ
   *     کهنه‌تر یا قرضِ دستی را بست امتیازِ فاکتورِ تازه‌تر نمی‌شود. فقط فاکتورهای پس از آغازِ امتیازِ دکان (نخستین
   *     ردیفِ `loyalty_*`) امتیاز می‌گیرند.
   *   • هدفِ هر فاکتور = `earn(قاعده، سطحِ همان فاکتور، پرداختِ هنگامِ ثبت + سهمِ پرداخت‌شدهٔ نسیه)` تا سقفِ خالصِ فاکتور پس از
   *     برگشت؛ فقط **افزایش** تا هدف (کسبِ فاکتور منهای پس‌گرفته‌ها) ردیفِ [EARN] ِ تازه با شناسهٔ `<رسید>-lp-<فاکتور>` و
   *     `refId` = همان فاکتور — پس برگشت و لغو ([afterReturn]، [afterCancel]) آن را هم به همان نسبت پس می‌گیرند.
   *   • تجمعی و از خودِ دفتر: تلاشِ دوباره یا رسیدِ دیگری که پیش‌تر پردازش شده هرگز امتیازِ دوم نمی‌سازد (هدف همان است)،
   *     و رسیدی که ردیفی با پیشوندِ خودش دارد دوباره دیده نمی‌شود.
   *  خاموش ([cfg] `on = false`)، رسیدِ غیرِ دستی، بی مشتری یا بی نسیهٔ باز ⇒ **همان شیء** [after].
   */
  fun afterPayment(after: ShopData, txId: String, by: String?, cfg: LoyaltyRules.Config = LoyaltyRules.current()): ShopData {
    if (!cfg.on || !cfg.rule.valid) return after
    val tx = after.transactions.firstOrNull { it.id == txId && it.type == "receive" && it.kind == null } ?: return after
    if (after.auditLog.any { it.id.startsWith("$txId-lp-") }) return after
    val start = after.auditLog.filter { isRow(it) }.minOfOrNull { it.createdAt } ?: return after
    val byId = after.debtors.associateBy { it.id }
    val customer = CustomerCrm.resolve(byId, tx.debtorId)
    fun mine(id: String?) = id != null && CustomerCrm.resolve(byId, id) == customer
    //  بدهی‌ها به ترتیبِ زمان: (زمان، شناسه، مبلغِ باز، فاکتور یا null برای بردِ دستی)
    class Debt(val at: Long, val id: String, val open: Long, val sale: Sale?)
    //  S89: جفتِ «اصل + سندِ معکوس» بیرون — رسیدِ معکوس‌شده در استخر نیست و بردِ معکوس‌شده بدهی نیست (همان حذفِ امروز)
    val txs = DebtReverse.effective(after.transactions)
    val debts = (
      after.sales.filter { s -> s.status != "cancelled" && mine(s.debtorId) }
        .map { Debt(it.createdAt, it.id, PayFlow.minor(it.debtGiven - it.debtSettled).coerceAtLeast(0), it) } +
        txs.filter { it.type == "give" && it.kind == null && mine(it.debtorId) }
          .map { Debt(it.createdAt, it.id, PayFlow.minor(it.amount).coerceAtLeast(0), null) }
      ).filter { it.open > 0 }.sortedWith(compareBy({ it.at }, { it.id }))
    if (debts.none { it.sale != null && it.at >= start }) return after
    var pool = txs.filter { it.type == "receive" && it.kind == null && mine(it.debtorId) }
      .sumOf { PayFlow.minor(it.amount).coerceAtLeast(0) }
    val ev = events(after)
    val rows = ArrayList<AuditEntry>()
    for (debt in debts) {
      if (pool <= 0) break
      val paidCredit = minOf(pool, debt.open)
      pool -= paidCredit
      val s = debt.sale ?: continue
      if (s.createdAt < start) continue
      val whole = PayFlow.minor(s.finalTotal)
      val back = after.saleReturns.filter { it.saleId == s.id }.sumOf { PayFlow.minor(it.amount) }
      val base = minOf(minOf(PayFlow.minor(s.paidAmount), whole).coerceAtLeast(0) + paidCredit, (whole - back).coerceAtLeast(0))
      val tier = Loyalty.tier(cfg.rule, spendAfn(after, customer, s.createdAt, except = s.id))
      val target = Loyalty.earn(cfg.rule, tier, base)
      val have = Loyalty.total(ev, s.id, Loyalty.EARN) - Loyalty.total(ev, s.id, Loyalty.EARN_BACK)
      val add = target - have
      if (add > 0) rows += row(
        "$txId-lp-${s.id}", Loyalty.EARN, customer, add, 0, s.id,
        "کسبِ $add امتیاز روی پرداختِ نسیه (${tierName(tier.key)} ×${multText(tier.multiplierPct)}) — فاکتور #${label(s)}",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
        tx.date, tx.createdAt, by,
      )
    }
    return if (rows.isEmpty()) after else after.copy(auditLog = after.auditLog + rows)
  }

  /**
   *  PRM-13 — رسیدِ دستیِ [txId] حذف شد (`DebtorEngine.deleteTransaction`): امتیازی که همان رسید داد (`<رسید>-lp-<فاکتور>`)
   *  پس گرفته می‌شود — هر ردیف یک [EARN_BACK] با شناسهٔ `<همان ردیف>-x` و همان فاکتور، تا ماندهٔ مشتری (هرگز منفی؛ با
   *  انقضای قاعدهٔ در کار). دوباره ⇒ همان؛ رسیدِ بی ردیفِ امتیاز ⇒ همان شیء.
   */
  fun afterPaymentDeleted(after: ShopData, txId: String, today: String, now: Long): ShopData {
    val granted = after.auditLog.filter { it.type == EARN && it.id.startsWith("$txId-lp-") }
    if (granted.isEmpty()) return after
    var d = after
    for (g in granted) {
      val id = g.id + "-x"
      if (d.auditLog.any { it.id == id }) continue
      val customer = CustomerCrm.resolve(d, g.payRef.orEmpty())
      val back = minOf(g.points ?: 0L, roomOf(d, customer, now))
      if (back <= 0) continue
      d = d.copy(auditLog = d.auditLog + row(
        id, Loyalty.EARN_BACK, customer, back, 0, g.refId.orEmpty(), "پس‌گرفتنِ $back امتیاز — حذفِ رسیدِ پرداختِ نسیه", today, now, null,  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
      ))
    }
    return d
  }

  // ── رسید و پروندهٔ مشتری ──────────────────────────────────────────
  data class OnSale(val earned: Long, val spent: Long, val spentMinor: Long)

  fun onSale(d: ShopData, saleId: String): OnSale? {
    val rows = d.auditLog.filter { it.refId == saleId && (it.type == EARN || it.type == SPEND) }
    if (rows.isEmpty()) return null
    val spend = rows.filter { it.type == SPEND }
    return OnSale(
      rows.filter { it.type == EARN }.sumOf { it.points ?: 0L }, spend.sumOf { it.points ?: 0L }, spend.sumOf { PayFlow.minor(it.amount) },
    )
  }

  /** ردیف‌های رسید (برچسب، افغانی): مصرف با ارزشش، کسب بی مبلغ (`null`) — فاکتورِ بی امتیاز ⇒ هیچ */
  fun receiptLines(d: ShopData, saleId: String): List<Pair<String, Double?>> {
    val s = onSale(d, saleId) ?: return emptyList()
    return buildList {
      if (s.spent > 0) add(StrLoyalty.receiptUsed(s.spent) to PayFlow.major(s.spentMinor))
      if (s.earned > 0) add(StrLoyalty.receiptEarned(s.earned) to null)
    }
  }

  data class HistoryRow(val id: String, val at: Long, val date: String, val kind: String, val signed: Long, val saleId: String, val invoice: String, val notes: String)

  /** برچسبِ ردیفِ سابقه: فاکتورِ ردیف، یا دلیلِ اصلاحِ دستی */
  fun rowText(r: HistoryRow): String =
    if (r.kind == Loyalty.ADJUST) kindText(r.kind) + " — " + r.notes else kindText(r.kind) + " — " + StrLoyalty.invoice(r.invoice)

  /** سابقهٔ امتیازِ مشتری (همان مشتریِ ماندگار)، تازه‌ترین بالا */
  fun history(d: ShopData, debtorId: String): List<HistoryRow> {
    val byId = d.debtors.associateBy { it.id }
    val root = CustomerCrm.resolve(byId, debtorId)
    val labels = d.sales.associate { it.id to (it.invoiceLabel() ?: "—") }
    return d.auditLog.filter { a -> isRow(a) && a.payRef?.let { CustomerCrm.resolve(byId, it) } == root }
      .mapNotNull { a ->
        val e = eventOf(a) ?: return@mapNotNull null
        HistoryRow(a.id, a.createdAt, a.date, e.kind, Loyalty.signed(e), e.saleId, labels[e.saleId] ?: "—", a.notes)
      }
      .sortedByDescending { it.at }
  }

  fun kindText(kind: String): String = when (kind) {
    Loyalty.EARN -> StrLoyalty.kindEarn
    Loyalty.SPEND -> StrLoyalty.kindSpend
    Loyalty.EARN_BACK -> StrLoyalty.kindEarnBack
    Loyalty.ADJUST -> StrLoyalty.kindAdjust
    else -> StrLoyalty.kindSpendBack
  }

  // ── اصلاحِ دستی ──────────────────────────────────────────────────
  const val REASON_MAX = 120

  sealed interface Adjusted {
    /** نوشته شد، یا همان کار پیش‌تر نوشته شده بود ([again]) — [data] دفترِ پس از آن */
    data class Ok(val data: ShopData, val again: Boolean) : Adjusted
    data class Bad(val message: String) : Adjusted
  }

  /** شناسهٔ ردیفِ اصلاح از شناسهٔ کار — یک کار، یک ردیف */
  fun adjustId(opId: String): String = "lpa-" + opId.trim()

  /**
   *  افزودن/کم‌کردنِ [delta] امتیاز برای [debtorId] با [reason] — فقط صاحب/مدیر ([may])، هرگز زیرِ صفر (با انقضای [rule] در
   *  [now])، فقط‌افزودنی. همان [opId] دوباره ⇒ همان دفتر و `again` (بی ردیفِ دوم، حتی اگر مانده عوض شده باشد).
   */
  fun adjust(
    d: ShopData, debtorId: String?, delta: Long, reason: String, opId: String, rule: Loyalty.Rule,
    may: Boolean, today: String, now: Long, by: String?,
  ): Adjusted {
    if (!may) return Adjusted.Bad(StrLoyalty.adjustOwnerOnly)
    val op = opId.trim()
    if (op.isEmpty() || op.length > 80) return Adjusted.Bad(StrLoyalty.errChanged)
    val id = adjustId(op)
    if (d.auditLog.any { it.id == id }) return Adjusted.Ok(d, again = true)
    val why = reason.trim().replace(Regex("\\s+"), " ")
    if (why.isEmpty()) return Adjusted.Bad(StrLoyalty.adjustNeedsReason)
    if (why.length > REASON_MAX) return Adjusted.Bad(StrLoyalty.adjustReasonLong(REASON_MAX))
    val customer = customerOf(d, debtorId)?.takeIf { c -> d.debtors.any { it.id == c } } ?: return Adjusted.Bad(StrLoyalty.errCustomer)
    return when (val a = Loyalty.adjust(balanceAt(d, customer, rule, now), delta)) {
      is Loyalty.Adjust.Bad -> Adjusted.Bad(if (a.code == Loyalty.Code.OVER_BALANCE) StrLoyalty.errBalance else StrLoyalty.adjustBadPoints)
      is Loyalty.Adjust.Ok -> Adjusted.Ok(
        d.copy(auditLog = d.auditLog + AuditEntry(
          id = id, type = ADJUST, date = today, refId = null, notes = why, createdAt = now, amount = 0.0,
          by = by?.trim()?.ifEmpty { null }, payRef = customer, points = a.points,
        )),
        again = false,
      )
    }
  }
}
