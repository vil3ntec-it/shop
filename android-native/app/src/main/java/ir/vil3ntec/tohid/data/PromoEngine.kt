package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import kotlin.math.roundToLong

/**
 *  تخفیف‌های خودکار در صندوق (PRM-01..09، 12، 17، 18) — پلِ `PromoCore` و سبدِ
 *  `SalesEngine`.
 *
 *  ⛔ پیش‌فرض خاموش: کلیدِ دکان خاموش، بی قاعدهٔ فعال یا سبدِ خالی ⇒ `null`، و
 *  صندوق همان سبدِ دست‌نخورده را جمع و ثبت می‌کند — عدد به عدد همان امروز.
 *
 *  ⛔ تخفیفِ خودکار در `Sale.discount` نمی‌نشیند. قیمتِ ردیف را پایین می‌آورد،
 *  دقیقاً از همان راهِ «قیمتِ دستیِ ردیف» (`CartLine.unitPrice`): قیمتِ واحد =
 *  خالصِ ردیف ÷ مقدار، پس `lineTotal` همان خالص است. چرا:
 *    • Sync v1/v3 فاکتورِ شاگرد با `discount > 0` را رد می‌کند (`sales.discount`)،
 *      ولی تخفیفِ خودکار را صاحب/مدیر از پیش تعریف کرده و شاگرد هم باید بزند؛
 *    • جمعِ ردیف‌ها = جمعِ فاکتور، برگشت سهمش را از همان ردیف می‌گیرد و سود از
 *      همان قیمتِ واحد حساب می‌شود؛ هیچ عددِ دفتر معنای تازه نمی‌گیرد.
 *  تخفیفِ دستیِ فاکتور (با سقفِ نقش) همان‌طور روی جمعِ پس از تخفیفِ خودکار می‌آید.
 *
 *  تا سرور تخفیفِ خودکار را از قیمتِ دستیِ ردیف جدا کند، `stamp` فیلدهای
 *  **اختیاری** می‌گذارد (همه به واحدِ کوچک): `SaleItem.listPriceMinor` و
 *  `SaleItem.promo`، `Sale.promoDiscountMinor` و `Sale.coupons`. خاموش ⇒ هیچ‌کدام.
 *
 *  ردیفِ آزاد و ردیفِ با قیمتِ دستی تخفیفِ خودکار نمی‌گیرند (قیمتِ دستی نهایی است).
 */
object PromoEngine {

  /** آنچه از سرور رسیده (یا نسخهٔ همین دستگاه) — کلیدِ دکان، قاعده‌ها و مجوزها */
  data class Snapshot(
    val enabled: Boolean = false,
    val rules: List<PromoCore.Rule> = emptyList(),
    /** صاحب/مدیر (`settings.write`) */
    val manage: Boolean = false,
    /** فقط صاحبِ دکان «زیرِ بهای خرید» را روشن می‌کند */
    val belowCost: Boolean = false,
    /** زمانِ آخرین پاسخِ سرور (میلی‌ثانیه)؛ ۰ = هرگز */
    val at: Long = 0,
    /** شمارهٔ نسخهٔ هر قاعده در سرور (`rev`) — روی قلمِ فاکتور می‌نشیند (`SalePromo.rev`) */
    val revs: Map<String, Long> = emptyMap(),
    /** «حساب@دکان»ی که این حال مالِ اوست (`PromoLive.ensure`)؛ خالی = هنوز هیچ‌کس */
    val account: String = "",
    /**
     *  پرچمِ `shop.promo_stamp` ِ سرور (`live_config`، پیش‌فرض خاموش): نشانِ تخفیف روی
     *  فاکتور (`stamp`) و ردیف‌های تازهٔ فاکتورِ چاپی و صفحه. خاموش ⇒ فاکتور و ردیفِ دفتر
     *  بایت‌به‌بایت همان پیشین.
     */
    val stamp: Boolean = false,
    /**
     *  PRM-02 — پرچمِ `shop.promo_uses` ِ سرور (`redeem` در پاسخ، پیش‌فرض خاموش): کدِ سقف‌دار پیش از ثبتِ
     *  فاکتور از سرور گرفته می‌شود (`PromoLive.redeemGate`) و ویرایشگر «سقفِ مصرف» را نشان می‌دهد.
     *  خاموش ⇒ همان رفتارِ پیشین.
     */
    val redeem: Boolean = false,
  ) {
    val active: Boolean get() = enabled && rules.any { it.active }
    val hasCodes: Boolean get() = rules.any { it.active && it.code.isNotBlank() }
  }

  data class Applied(val id: String, val name: String, val discount: Double)

  /**
   *  آنچه از تخفیف روی قلمِ فاکتور می‌نشیند: قیمتِ فهرستِ کالا (واحدِ کوچک؛ قلمِ
   *  آزاد ندارد) و سهمِ هر قاعده از تخفیفِ همان ردیف.
   */
  data class LineTag(val listMinor: Long?, val promo: List<SalePromo>)

  /**
   *  @param gross جمعِ سبد پیش از تخفیفِ خودکار (`SalesEngine.cartTotal`)
   *  @param cart همان سبد با قیمتِ پایین‌آمدهٔ ردیف‌هایی که تخفیف گرفته‌اند — جمع و ثبت از همین
   */
  data class Outcome(
    val gross: Double,
    val discount: Double,
    val applied: List<Applied>,
    val codes: List<PromoCore.CodeStatus>,
    val cart: List<SalesEngine.CartLine>,
    /** به کلیدِ ردیف (`CartLine.productId`) — `stamp` آن را روی قلم‌های فاکتور می‌گذارد */
    val tags: Map<String, LineTag> = emptyMap(),
    /** همان `Snapshot.stamp` هنگامِ سنجش */
    val stamp: Boolean = false,
  )

  /** سقفِ `Sale.coupons` — کدِ هسته ۳ تا ۳۲ نویسه است */
  const val MAX_COUPONS = 10
  const val MAX_COUPON_LEN = 32
  /** سقفِ `SalePromo.name` — همان سقفِ نامِ قاعده در سرور */
  const val MAX_NAME_LEN = 80

  /**
   *  S1 — پاسخِ سرور برای حسابِ [request] فقط وقتی می‌نشیند که حالِ حافظه هنوز مالِ همان
   *  است ([bound]) **و** حسابِ واردشدهٔ همین لحظه ([current]) هم همان است. پاسخی که پیش از
   *  جابه‌جاییِ حساب رفته بود، پس از آن نه در حافظه می‌نشیند و نه در نسخهٔ دستگاه.
   */
  fun accepts(bound: String?, request: String, current: String): Boolean = bound == request && current == request

  /**
   *  S1 — حالِ حافظه فقط برای همان «حساب@دکان»ی که مالِ اوست؛ حسابِ دیگر ⇒ حالِ
   *  خالی (خاموش). تا ده دقیقه پس از عوض شدنِ حساب، فروشِ دکانِ دوم تخفیف‌های دکانِ
   *  اول را می‌گرفت.
   */
  fun forAccount(s: Snapshot, account: String): Snapshot = if (s.account == account) s else Snapshot(account = account)

  /**
   *  S1 — نشانِ «حالِ حافظه مالِ کیست»؛ `PromoLive` آن را پیش از هر خواندن و
   *  نوشتن می‌پرسد. کلیدِ تازه ⇒ `true` و «نسخهٔ دستگاه خوانده شد» و «آخرین
   *  تلاش» صفر می‌شوند تا صدازننده عکس و کدها را دور بریزد و از نو بخواند.
   */
  class Binding {
    @Volatile var key: String? = null
      private set
    @Volatile var loaded: Boolean = false
    @Volatile var triedAt: Long = 0L

    @Synchronized fun bind(now: String): Boolean {
      if (now == key) return false
      key = now
      loaded = false
      triedAt = 0L
      return true
    }

    /** ورود و خروج: مالِ هیچ‌کس — نخستین `bind` از نو می‌خواند */
    @Synchronized fun reset() {
      key = null
      loaded = false
      triedAt = 0L
    }
  }

  /** ردیف‌های سبد ⇒ ورودیِ هسته. مبلغِ هر ردیف همان `SalesEngine.lineTotal`ِ امروز است. */
  fun lines(d: ShopData, cart: List<SalesEngine.CartLine>): List<PromoCore.Line> = cart.map { line ->
    val qtyMilli = (line.quantity * 1000).roundToLong()
    val p = if (line.free) null else d.products.find { it.id == line.productId }
    if (p == null) {
      val price = line.unitPrice ?: 0.0
      PromoCore.Line(
        key = line.productId, productId = "", qtyMilli = qtyMilli, unitMinor = (price * 100).roundToLong(),
        amountMinor = (SalesEngine.lineTotal(price, line.quantity) * 100).roundToLong(),
      )
    } else {
      val price = line.unitPrice ?: p.salePrice
      PromoCore.Line(
        key = line.productId,
        productId = p.id,
        qtyMilli = qtyMilli,
        unitMinor = (price * 100).roundToLong(),
        amountMinor = (SalesEngine.lineTotal(price, line.quantity) * 100).roundToLong(),
        costMinor = (p.purchasePrice * 100).roundToLong().coerceAtLeast(0),
        category = p.category,
        supplierId = p.supplierId,
        noPromo = line.unitPrice != null,
      )
    }
  }

  /**
   *  سبد + قاعده‌ها ⇒ نتیجه، یا `null` وقتی چیزی برای اعمال نیست (خاموش، بی
   *  قاعدهٔ فعال، سبدِ خالی، یا عددی بیرون از بازهٔ امنِ هسته).
   *
   *  @param date تاریخِ محلیِ دکان «YYYY-MM-DD»
   *  @param minute دقیقه از نیمه‌شب
   */
  fun evaluate(
    d: ShopData,
    cart: List<SalesEngine.CartLine>,
    snap: Snapshot,
    coupons: List<String>,
    date: String,
    minute: Int,
  ): Outcome? {
    if (!snap.active || cart.isEmpty()) return null
    val input = PromoCore.Cart(lines(d, cart), coupons, group = "", channel = "pos", at = PromoCore.At(date, minute.toLong()), stepMinor = 100)
    val r = runCatching { PromoCore.evaluate(input, snap.rules) }.getOrNull() ?: return null
    val net = r.lines.associate { it.key to it }
    val parts = runCatching { split(input, snap.rules, r) }.getOrNull()
    val nameOf = snap.rules.associate { it.id to it.name.trim().take(MAX_NAME_LEN) }
    val priced = cart.map { line ->
      val o = net[line.productId]
      if (o == null || o.discountMinor <= 0 || line.quantity <= 0) line
      else line.copy(unitPrice = (o.netMinor / 100.0) / line.quantity)
    }
    val tags = cart.associate { line ->
      val p = if (line.free) null else d.products.find { it.id == line.productId }
      val o = net[line.productId]
      val promo = if (o == null || o.discountMinor <= 0 || line.quantity <= 0) emptyList()
      //  تقسیم نشد (هرگز نباید) ⇒ جمعِ ردیف درست می‌ماند، فقط بی نامِ قاعده
      else parts?.get(line.productId)?.map { (id, minor) -> SalePromo(id, snap.revs[id] ?: 0, nameOf[id].orEmpty(), minor) }
        ?: listOf(SalePromo(discountMinor = o.discountMinor))
      line.productId to LineTag(p?.let { (it.salePrice * 100).roundToLong() }, promo)
    }
    return Outcome(
      gross = SalesEngine.cartTotal(d, cart),
      discount = r.discountMinor / 100.0,
      applied = r.applied.map { Applied(it.id, it.name, it.discountMinor / 100.0) },
      codes = r.codes,
      cart = priced,
      tags = tags,
      stamp = snap.stamp,
    )
  }

  /**
   *  سهمِ هر قاعده از تخفیفِ هر ردیف (S2). هسته جمعِ هر ردیف و جمعِ هر قاعده را
   *  می‌دهد، نه جدولِ ردیف × قاعده؛ پس قاعده‌های اعمال‌شده با **همان ترتیبِ هسته**
   *  یکی‌یکی دوباره سنجیده می‌شوند (همه «ترکیبی» تا هیچ‌کدام کنار نرود) و تفاضلِ
   *  دو پیشوند سهمِ قاعدهٔ تازه است — هسته هر قاعده را روی ماندهٔ قاعده‌های پیشین
   *  می‌زند و قاعدهٔ بی‌اثر چیزی از مانده کم نمی‌کند. هر دو جمع با خروجیِ اصلی
   *  سنجیده می‌شوند؛ ناهمخوان ⇒ `null`.
   *
   *  @return کلیدِ ردیف ⇒ [(شناسهٔ قاعده، تخفیف به واحدِ کوچک)] به ترتیبِ اعمال
   */
  internal fun split(cart: PromoCore.Cart, rules: List<PromoCore.Rule>, r: PromoCore.Result): Map<String, List<Pair<String, Long>>>? {
    val out = LinkedHashMap<String, MutableList<Pair<String, Long>>>()
    if (r.applied.size == 1) {
      val id = r.applied[0].id
      r.lines.forEach { if (it.discountMinor > 0) out[it.key] = mutableListOf(id to it.discountMinor) }
    } else if (r.applied.size > 1) {
      val byId = rules.associateBy { it.id }
      val prefix = ArrayList<PromoCore.Rule>()
      var before = LongArray(r.lines.size)
      for (a in r.applied) {
        prefix += (byId[a.id] ?: return null).copy(stack = "combine")
        val step = PromoCore.evaluate(cart, prefix)
        if (step.lines.size != r.lines.size) return null
        var got = 0L
        val now = LongArray(step.lines.size) { step.lines[it].discountMinor }
        for (i in now.indices) {
          val share = now[i] - before[i]
          if (share < 0) return null
          if (share > 0) { out.getOrPut(r.lines[i].key) { mutableListOf() } += a.id to share; got += share }
        }
        if (got != a.discountMinor) return null
        before = now
      }
    }
    for (l in r.lines) if ((out[l.key]?.sumOf { it.second } ?: 0L) != l.discountMinor) return null
    return out
  }

  /**
   *  S2 — فاکتورِ ثبت‌شده + نتیجهٔ تخفیف ⇒ همان فاکتور با فیلدهای اختیاری: روی
   *  هر قلمِ کالایی `listPriceMinor` و `promo`، روی فاکتور `promoDiscountMinor`
   *  (جمعِ همان سهم‌ها) و `coupons`. عددِ هیچ قلم و جمعی عوض نمی‌شود — فقط نشان
   *  تا سرور تخفیفِ خودکار را از پایین آوردنِ دستیِ قیمت جدا کند.
   *
   *  `coupons` فقط کدهایی که اعمال شدند (`applied`)، با سقفِ [MAX_COUPONS] و [MAX_COUPON_LEN].
   *
   *  ⛔ کلیدِ دکان خاموش (`o == null`)، پرچمِ `shop.promo_stamp` خاموش یا ثبتِ ناموفق ⇒
   *  **همان شیء**؛ فاکتور و قلم‌ها بایت‌به‌بایت همان امروز.
   */
  fun stamp(r: SalesEngine.Result, o: Outcome?): SalesEngine.Result {
    if (o == null || !o.stamp || r !is SalesEngine.Result.Ok) return r
    val si = r.data.sales.indexOfLast { it.id == r.saleId }
    if (si < 0) return r
    val items = r.data.saleItems.toMutableList()
    var promoMinor = 0L
    var seen = false
    //  قلم‌های همین فاکتور تهِ فهرست‌اند (`SalesEngine.record`)
    for (i in items.indices.reversed()) {
      val item = items[i]
      if (item.saleId != r.saleId) { if (seen) break else continue }
      seen = true
      if (item.productId.isEmpty()) continue
      val tag = o.tags[item.productId] ?: continue
      items[i] = item.copy(listPriceMinor = tag.listMinor, promo = tag.promo)
      promoMinor += tag.promo.sumOf { it.discountMinor }
    }
    val sales = r.data.sales.toMutableList()
    val applied = o.codes.filter { it.status == "applied" }.map { it.code.take(MAX_COUPON_LEN) }.filter { it.isNotEmpty() }
      .distinct().take(MAX_COUPONS)
    sales[si] = sales[si].copy(promoDiscountMinor = promoMinor, coupons = applied)
    return r.copy(data = r.data.copy(sales = sales, saleItems = items))
  }

  /** سبدی که جمع و ثبت از آن می‌رود: با تخفیف اگر هست، وگرنه همان سبد (همان شیء) */
  fun priced(cart: List<SalesEngine.CartLine>, outcome: Outcome?): List<SalesEngine.CartLine> = outcome?.cart ?: cart

  /** پیامِ فارسیِ وضعیتِ یک کدِ واردشده */
  fun codeText(status: String): String = when (status) {
    "applied" -> "اعمال شد"
    "no_effect" -> "روی این سبد اثری ندارد"
    "used_up" -> "ظرفیتِ این کد پر شده است"
    "inactive" -> "این کد الآن فعال نیست"
    else -> "این کد پیدا نشد"
  }
}
