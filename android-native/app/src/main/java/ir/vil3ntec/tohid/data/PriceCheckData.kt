package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.PriceCheck
import kotlin.math.roundToLong

/**
 *  POS-15 — قیمت‌پرسیِ یک کالا از دفتر (هسته: `core/…/pos/PriceCheck.kt`). فقط می‌خواند.
 *  موجودی همان `ShopStore.stock`، محموله‌ها همان FEFO ِ `ExpiryEngine.lots`، خریدها از `ShopData.purchases`.
 *
 *  خالص — بی `Context`؛ آزمون: `PriceCheckDataTest`.
 */
object PriceCheckData {

  data class View(
    val product: Product,
    val stock: Double,
    val card: PriceCheck.Card,
    /** نامِ تأمین‌کنندهٔ آخرین خرید؛ نبود ⇒ `null` */
    val supplier: String?,
  )

  private fun minor(afn: Double): Long = if (afn.isFinite()) (afn * 100).roundToLong() else 0L
  private fun milli(q: Double): Long = if (q.isFinite()) (q * 1000).roundToLong() else 0L

  fun of(d: ShopData, product: Product, today: String): View {
    val names = d.suppliers.associate { it.id to it.name }
    val purchases = d.purchases.filter { it.productId == product.id }.map {
      PriceCheck.Purchase(it.date, it.createdAt, minor(it.purchasePrice), milli(it.quantity), it.supplierId)
    }
    val lots = ExpiryEngine.lots(d, today).filter { it.product.id == product.id }
      .map { PriceCheck.Lot(it.entry.expiryDate, milli(it.remaining)) }
    val card = PriceCheck.card(minor(product.salePrice), minor(product.purchasePrice), purchases, lots, today)
    return View(product, ShopStore.stock(d, product.id), card, card.last?.supplier?.let { names[it]?.ifBlank { null } })
  }

  /** رنگِ یک سطرِ کارت — رنگ هرگز تنها نیست، متنِ سطر خودش می‌گوید */
  enum class Tone { NORMAL, MUTED, WARN, DANGER }

  /** یک سطرِ کارتِ قیمت‌پرسی؛ [label] خالی ⇒ سطرِ دنبالهٔ سطرِ بالا */
  data class Line(val label: String, val value: String, val tone: Tone = Tone.NORMAL)

  /**
   *  سطرهای کارت — یک قاعده برای پنجرهٔ Ctrl+I ِ کامپیوتر و برگهٔ «قیمت‌پرسی» ِ گوشی (کدِ مشترک، آیفون هم): قیمتِ فروش و
   *  عمده، قیمتِ خرید و سود فقط با [showCost] (`reports.money`)، موجودی، آخرین خرید و نزدیک‌ترین انقضا.
   */
  fun lines(v: View, showCost: Boolean): List<Line> = buildList {
    val S = ir.vil3ntec.tohid.core.i18n.StrPriceCheck
    val p = v.product
    val unit = p.unit.ifBlank { "" }
    add(Line(S.retail, ir.vil3ntec.tohid.money(p.salePrice) + " ؋"))
    if (p.wholesalePrice > 0) add(Line(S.wholesale, ir.vil3ntec.tohid.money(p.wholesalePrice) + " ؋"))
    if (showCost && p.purchasePrice > 0) {
      add(Line(S.cost, ir.vil3ntec.tohid.money(p.purchasePrice) + " ؋"))
      v.card.marginBp?.let { add(Line("", S.margin((it / 100).toString()), if (it < 0) Tone.DANGER else Tone.MUTED)) }
    }
    add(Line(S.stock, (ir.vil3ntec.tohid.qty(v.stock) + " " + unit).trim(), if (v.stock <= 0) Tone.DANGER else Tone.NORMAL))
    val last = v.card.last
    add(
      Line(
        S.lastPurchase,
        if (last == null) S.noPurchase
        else listOfNotNull(
          ir.vil3ntec.tohid.formatDate(last.date),
          v.supplier,
          if (showCost) ir.vil3ntec.tohid.money(last.unitMinor / 100.0) + " ؋" else null,
        ).joinToString(" · "),
      ),
    )
    val near = v.card.nearest
    val days = v.card.nearestDays
    add(
      Line(
        S.expiry,
        if (near == null || days == null) S.noExpiry else ir.vil3ntec.tohid.formatDate(near.expiry) + " · " + S.daysLeft(days),
        if (v.card.near) Tone.WARN else Tone.NORMAL,
      ),
    )
    if (v.card.expiredMilli > 0) add(Line("", S.expired((ir.vil3ntec.tohid.qty(v.card.expiredMilli / 1000.0) + " " + unit).trim()), Tone.DANGER))
  }

  /** پیشنهادهای نام یا بارکد (حداکثر ۸، فقط کالای فعال) — همان جست‌وجوی صندوق */
  fun matches(d: ShopData, term: String): List<Product> = ir.vil3ntec.tohid.ui.components.pos.PosCatalog.matches(d.products, term.trim()).take(8)

  /** کالای کارت: برگزیده، یا بارکدِ دقیق، یا تنها پیشنهاد؛ وگرنه `null` (فهرستِ پیشنهادها) */
  fun pick(d: ShopData, index: Map<String, String>, term: String, picked: String?): String? =
    picked ?: (BarcodeMatch.find(term.trim(), index) as? BarcodeMatch.Hit.Product)?.id ?: matches(d, term).singleOrNull()?.id
}
