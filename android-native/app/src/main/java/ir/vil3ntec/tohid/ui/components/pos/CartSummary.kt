package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine

/**
 *  خلاصهٔ سبدِ گوشی (نسخهٔ ۳، تصویرهای مرجعِ r3 و r2) — خالص، بی Compose.
 *
 *  نوارِ سیاهِ پایین فقط وقتی سبد پُر است دیده می‌شود و دو چیز نشان می‌دهد: عکسِ گردِ
 *  تازه‌ترین قلم‌ها (یا حرفِ اولِ نامشان) و شمارِ قلم‌ها در دایرهٔ زرد.
 *
 *   • «شمار» شمارِ **ردیف‌ها**ست، نه جمعِ مقدار — دو کیلو برنج یک قلم است
 *     (همان «۳ قلم»ِ نوارِ پیشینِ ویترین، و همان «3»ِ r3 با سه عکس).
 *   • عکس‌ها از **تازه‌ترین** ردیف‌اند تا هر افزودن بی‌درنگ در نوار دیده شود؛
 *     بیش از `MAX_THUMBS` ⇐ «+N».
 *   • مبلغ همان جمعِ ردیف‌هاست که صدا زننده می‌دهد (`SalesEngine.cartTotal`) —
 *     این‌جا حسابِ تازه‌ای نیست.
 */
object CartSummary {
  const val MAX_THUMBS = 3

  /** یک ردیفِ سبد برای نوار */
  data class Line(val id: String, val name: String, val hasProduct: Boolean)

  data class Thumb(val id: String, val initials: String, val hasProduct: Boolean)

  data class Model(val count: Int, val total: Double, val thumbs: List<Thumb>, val more: Int) {
    val visible: Boolean get() = count > 0
  }

  fun of(lines: List<Line>, total: Double): Model {
    val recent = lines.takeLast(MAX_THUMBS).asReversed()
    return Model(
      count = lines.size,
      total = total,
      thumbs = recent.map { Thumb(it.id, initials(it.name), it.hasProduct) },
      more = (lines.size - recent.size).coerceAtLeast(0),
    )
  }

  /** همان `of` از روی سبد و کالاها — ردیفِ آزاد نامِ خودش را دارد */
  fun of(cart: List<SalesEngine.CartLine>, products: List<Product>, total: Double): Model {
    val byId = products.associateBy { it.id }
    return of(
      cart.map { line ->
        val product = byId[line.productId]
        Line(line.productId, product?.name ?: line.label, product != null)
      },
      total,
    )
  }

  /**
   *  حرفِ اولِ نام برای جای‌خالیِ عکس: نخستین حرف یا رقمِ واقعی، بی فاصله، نیم‌فاصله
   *  (ZWNJ) و نشانه؛ لاتین بزرگ. نامِ خالی ⇐ «؟».
   */
  fun initials(name: String): String {
    val c = name.firstOrNull { it.isLetterOrDigit() } ?: return "؟"
    return c.uppercaseChar().toString()
  }

  /**
   *  یک ردیفِ سبدِ باز (r2): نام، مقدار، قیمتِ واحد، جمعِ ردیف و پلهٔ −/+.
   *
   *  `step` همان `SalesEngine.cartStep`ِ واحدِ کالاست (کیلو ۰٫۱، گرم ۱۰، دانه ۱)؛
   *  ردیفِ آزاد همیشه ۱. `lastStep` یعنی «−» بعدی ردیف را برمی‌دارد ⇐ سطل.
   */
  data class Row(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val lineTotal: Double,
    val step: Double,
    /** قیمتِ دستی (یا ردیفِ آزاد که همیشه قیمتِ خودش را دارد) */
    val custom: Boolean,
    val free: Boolean,
    val hasProduct: Boolean,
  ) {
    val lastStep: Boolean get() = quantity <= step
    val thumb: Thumb get() = Thumb(id, initials(name), hasProduct)
  }

  /**
   *  ردیف‌های سبدِ باز به همان ترتیبِ سبد. ردیفی که کالایش حذف شده و آزاد هم نیست
   *  کنار می‌رود — همان قاعدهٔ پیشینِ برگه (و `cartTotal` آن را صفر می‌شمارد).
   */
  fun rows(cart: List<SalesEngine.CartLine>, products: List<Product>): List<Row> {
    val byId = products.associateBy { it.id }
    return cart.mapNotNull { line ->
      val product = byId[line.productId]
      if (product == null && !line.free) return@mapNotNull null
      val price = line.unitPrice ?: product?.salePrice ?: 0.0
      Row(
        id = line.productId,
        name = product?.name ?: line.label,
        quantity = line.quantity,
        unit = product?.unit.orEmpty(),
        unitPrice = price,
        lineTotal = SalesEngine.lineTotal(price, line.quantity),
        step = if (product != null) SalesEngine.cartStep(product.unit) else 1.0,
        custom = line.free || line.unitPrice != null,
        free = line.free,
        hasProduct = product != null,
      )
    }
  }

  /**
   *  ردیفِ «تخفیف»ِ سبدِ باز — فقط وقتی فروشنده قیمتِ ردیفی را دستی **کمتر** از
   *  قیمتِ کالا گذاشته است: جمعِ (قیمتِ کالا × مقدار) − جمعِ ردیف. جمعِ سبد همین
   *  حالا هم با قیمتِ دستی حساب شده؛ این فقط نشان می‌دهد چقدر کم شده.
   *  ردیفِ آزاد قیمتِ پایه ندارد و حساب نمی‌شود. صفر ⇐ ردیف دیده نمی‌شود.
   */
  fun manualDiscount(cart: List<SalesEngine.CartLine>, products: List<Product>): Double {
    val byId = products.associateBy { it.id }
    var off = 0.0
    for (line in cart) {
      val price = line.unitPrice ?: continue
      if (line.free) continue
      val product = byId[line.productId] ?: continue
      val full = SalesEngine.lineTotal(product.salePrice, line.quantity)
      val paid = SalesEngine.lineTotal(price, line.quantity)
      off += (full - paid).coerceAtLeast(0.0)
    }
    return off
  }
}

/**
 *  متن‌ها و اندازه‌های خالصِ صندوقِ گوشی (نسخهٔ ۳) — بی Compose تا آزمون‌پذیر باشند.
 *
 *  مبلغ‌ها هیچ‌وقت نباید دو خط شوند یا بیرون بزنند: «۱۲٬۳۴۵٬۶۷۸ ؋» هم باید در کاشیِ
 *  کوچکِ کالا و کنارِ «جمع:»ِ درشتِ سبد (r2 ≈ ۳۴sp) بنشیند؛ پس اندازهٔ قلم با درازیِ
 *  متن پله‌پله کوچک می‌شود.
 */
object PhoneSaleText {
  /** قیمتِ پررنگِ کاشیِ کوچکِ کالا (۱۴sp؛ متنِ کامل با «؋») — کاشی ۱۰۱dp جا دارد */
  fun priceSp(text: String): Int = when {
    text.length <= 9 -> 14
    text.length <= 11 -> 13
    text.length <= 13 -> 12
    else -> 11
  }

  /** جمعِ درشتِ سبدِ باز (متنِ کامل با «؋») */
  fun totalSp(text: String): Int = when {
    text.length <= 11 -> 34
    text.length <= 14 -> 30
    else -> 26
  }

  /** خطِ کوچکِ خاکستریِ کاشی — موجودی با واحد، یا «تمام شد» (سرخ) */
  fun stock(stock: Double, unit: String): String =
    if (stock <= 0) "تمام شد"
    else "${ir.vil3ntec.tohid.qty(stock)}${if (unit.isNotBlank()) " $unit" else ""} مانده"
}
