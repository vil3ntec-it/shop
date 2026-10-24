package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.keymap.KeyName
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.CartQuery
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData

/*
 *  منطقِ خالصِ صندوقِ کامپیوتر (VISUAL-DIRECTION §۲) — بی Compose و بی اندروید،
 *  تا آزمونِ JVM همان راهی را بسنجد که صفحه می‌رود. هر قاعده‌ای این‌جاست
 *  پیش‌تر درونِ `DeskSaleScreen` بود و فقط جابه‌جا شده است؛ رفتار همان است.
 */

/** کاشیِ ستونِ دسته‌ها. `name == null` یعنی «همه». */
data class PosCategory(val name: String?, val label: String, val count: Int)

object PosCatalog {
  const val ALL_LABEL = "همه"

  /**
   *  «همه» و دسته‌های واقعیِ دکان با شمارِ کالای **فعال**. ترتیب همان
   *  `productCategories`ِ دفتر است و دسته‌ای که فقط روی کالا نوشته شده پس
   *  از آن می‌آید. دسته‌ای که کالای فعال ندارد کاشی نمی‌گیرد (کاشیِ بی‌کالا
   *  فقط شبکهٔ خالی نشان می‌داد).
   */
  fun categories(d: ShopData): List<PosCategory> {
    val active = d.products.filter { it.active }
    val counts = active.groupingBy { it.category.trim() }.eachCount()
    val names = (d.productCategories.map { it.trim() } + active.map { it.category.trim() })
      .filter { it.isNotEmpty() }
      .distinct()
      .filter { (counts[it] ?: 0) > 0 }
    return listOf(PosCategory(null, ALL_LABEL, active.size)) + names.map { PosCategory(it, it, counts.getValue(it)) }
  }

  /**
   *  همان پیشنهادهای کادرِ صندوق: فقط کالای فعال (PRD-18)، نام یا آغازِ
   *  بارکد (رقمِ فارسی و لاتین یکی)، نامِ آغازشونده با عبارت اول. پیش‌تر
   *  فقط ۹ تای اول در فهرستِ کشویی می‌آمد؛ حالا همه در شبکه دیده می‌شوند و
   *  نخستینِ فهرست همان نخستینِ پیشین است.
   */
  fun matches(products: List<Product>, term: String): List<Product> =
    if (term.isEmpty()) emptyList()
    else products.asSequence()
      .filter { it.active }
      .filter { p -> p.name.contains(term, ignoreCase = true) || CartQuery.barcodeStarts(p.barcodes, term) }
      .sortedWith(compareBy({ !it.name.startsWith(term, ignoreCase = true) }, { it.name }))
      .toList()

  /** شبکهٔ بی جست‌وجو: کالاهای فعالِ دستهٔ انتخابی (`null` = همه) به ترتیبِ نام */
  fun byCategory(products: List<Product>, category: String?): List<Product> =
    products.filter { it.active && (category == null || it.category.trim() == category) }.sortedBy { it.name }

  /**
   *  آن‌چه شبکه نشان می‌دهد. با عبارت ⇒ نتیجهٔ جست‌وجو در **همهٔ** دسته‌ها،
   *  تا Enter همان کالایی را بردارد که پیش از این برمی‌داشت (دستهٔ انتخابی
   *  جست‌وجو را تنگ نمی‌کند).
   */
  fun shown(products: List<Product>, category: String?, term: String): List<Product> =
    if (term.isNotEmpty()) matches(products, term) else byCategory(products, category)
}

/** نتیجهٔ Enter در کادرِ صندوق */
sealed interface PosHit {
  data class Add(val product: Product, val count: Double) : PosHit
  /** بارکدِ کالای غیرفعال — پیام، نه افزودن */
  data class Inactive(val product: Product) : PosHit
  /** نه بارکد و نه پیشنهاد — «پیدا نشد» با راهِ ثبتِ کالا */
  data class Unknown(val code: String) : PosHit
  /** کادرِ خالی، یا بارکدی که کالایش در دفتر نیست — کاری نمی‌شود */
  data object None : PosHit
}

object PosSearch {
  /**
   *  Enter در کادر: بارکدِ کامل ⇐ همان کالا؛ وگرنه پیشنهادِ برجسته
   *  (`matches[hint]`)؛ وگرنه «پیدا نشد». «۳*» پیش از بارکد یا نام ⇐ سه تا
   *  (`CartQuery`، رقمِ فارسی هم — F0-06).
   */
  fun resolve(query: String, index: Map<String, String>, products: List<Product>, matches: List<Product>, hint: Int): PosHit {
    val parsed = CartQuery.parse(query)
    val code = parsed.term.trim()
    if (code.isEmpty()) return PosHit.None
    val hit = BarcodeMatch.find(code, index)
    if (hit is BarcodeMatch.Hit.Product) {
      val p = products.firstOrNull { it.id == hit.id } ?: return PosHit.None
      return if (!p.active) PosHit.Inactive(p) else PosHit.Add(p, parsed.count)
    }
    val pick = matches.getOrNull(hint)
    return if (pick != null) PosHit.Add(pick, parsed.count) else PosHit.Unknown(code)
  }
}

/** − / تعداد / + روی کاشی، ردیفِ جدول و ردیفِ سبد */
object PosCartOps {
  fun qtyOf(cart: List<SalesEngine.CartLine>, productId: String): Double =
    cart.firstOrNull { it.productId == productId }?.quantity ?: 0.0

  /**
   *  یک پله بالا یا پایین (`cartStep`: دانه ۱، کیلو ۰٫۱، گرم ۱۰) — همان
   *  `step`ِ پیشینِ سبد و کلیدهای + و −. صفر یا کمتر ⇒ ردیف برداشته می‌شود؛
   *  کالای بیرونِ سبد دست نمی‌خورد.
   */
  fun step(cart: List<SalesEngine.CartLine>, productId: String, unit: String, sign: Int): List<SalesEngine.CartLine> {
    val line = cart.find { it.productId == productId } ?: return cart
    return SalesEngine.setCartQty(cart, productId, line.quantity + sign * SalesEngine.cartStep(unit))
  }

  /** + روی کاشیِ کالا = افزودنِ یک پله با سقفِ موجودی، مثلِ اسکن (`addToCartCapped`) */
  fun addStep(cart: List<SalesEngine.CartLine>, product: Product, available: Double): SalesEngine.Added =
    SalesEngine.addToCartCapped(cart, product.id, SalesEngine.cartStep(product.unit), available)
}

/** روشِ پرداختِ شبکهٔ پنلِ سفارش */
data class PosPayMethod(val payment: SalesEngine.Payment, val label: String, val needsCustomer: Boolean)

object PosPayments {
  /**
   *  **فقط** روش‌هایی که موتورِ فروش واقعاً ثبت می‌کند (VISUAL-DIRECTION §۲،
   *  PAY-06). امروز نقد و نسیه؛ روشِ تازه که به `SalesEngine.Payment` بیاید
   *  بی برچسب کامپایل نمی‌شود (`when`ِ کامل). کارت، حواله، کیف‌پول و … تا
   *  ثبتشان ساخته نشده دکمه نمی‌گیرند.
   */
  fun configured(): List<PosPayMethod> = SalesEngine.Payment.entries.map {
    when (it) {
      SalesEngine.Payment.CASH -> PosPayMethod(it, "نقدی", needsCustomer = false)
      SalesEngine.Payment.CREDIT -> PosPayMethod(it, "نسیه", needsCustomer = true)
    }
  }
}

/** ردیفِ خلاصهٔ پرداخت */
data class PosSummaryRow(val label: String, val amount: Double, val negative: Boolean = false)

object PosSummary {
  /**
   *  فقط آن‌چه موتورِ فروش حساب می‌کند: جمع و (اگر هست) تخفیف. حمل، مالیات و
   *  گردکردنِ نقدی (PAY-09، POS-37) هنوز در موتور نیستند، پس ردیف نمی‌گیرند.
   *  «مبلغِ قابلِ پرداخت» جدا و درشت نشان داده می‌شود (`Totals.finalTotal`).
   */
  fun rows(t: SalesEngine.Totals): List<PosSummaryRow> = buildList {
    add(PosSummaryRow("جمع", t.subtotal))
    if (t.discount > 0) add(PosSummaryRow("تخفیف", t.discount, negative = true))
  }
}

object PosLayout {
  /*
   *  نسخهٔ ۲: ستونِ دسته‌ها برداشته شد (دسته‌ها تراشهٔ افقیِ بالای کاتالوگ‌اند).
   *  آن‌چه با پهنا عوض می‌شود جای سبد است: از ۷۶۰dp به بالا کنارِ کاتالوگ با
   *  پهنای پله‌ای، کمتر (تبلتِ ایستاده، پنجرهٔ باریک) زیرِ کاتالوگ.
   */
  /** پهنای ناحیهٔ صندوق که از آن به بالا سبد کنارِ کاتالوگ است (کمتر ⇒ زیرِ آن) */
  const val SIDE_CART_MIN_DP = 760f
  fun sideCart(widthDp: Float): Boolean = widthDp >= SIDE_CART_MIN_DP

  /** پهنای سبدِ کناری: پنجرهٔ پهن ⇒ سبدِ پهن‌تر، ولی کاتالوگ همیشه بیشترِ جا را دارد */
  fun cartWidth(widthDp: Float): Int = when {
    widthDp >= 1500f -> 420
    widthDp >= 1100f -> 372
    else -> 336
  }

  /** کمینهٔ هدفِ لمس: لمسی ۴۸ (۵.۳)، کامپیوتر ۳۶ (کمینهٔ دکمهٔ کامپیوتر ۳۴ است) */
  fun minTarget(touch: Boolean): Int = if (touch) 48 else 36
}

/** کارِ هر کلید در صندوق */
enum class PosAction {
  FINISH, CUSTOMER, EDIT_ROW, FREE_ITEM, REPRINT,
  SUBMIT, HINT_DOWN, HINT_UP, ROW_DOWN, ROW_UP, CLEAR_QUERY, DELETE_ROW, QTY_UP, QTY_DOWN,
}

/**
 *  نگاشتِ کلید ⇒ کارِ صندوق. همان دو `when`ِ پیشینِ `DeskSaleScreen` (سطحِ صفحه
 *  و کادرِ اسکن)، کلمه به کلمه، روی نامِ کلیدِ فیزیکی (`KeyName`، مستقل از
 *  چیدمان — ۵.۱ بند ۳). صفحه با `KeyName.matches(Key)` نام را می‌گیرد و
 *  آزمونِ «فروشِ سه‌قلمی با ≤ ۸ کلید» همین جدول را می‌راند.
 */
object PosKeys {
  /** پیش‌نمایشِ کلید روی کلِ صفحه — پیش از کادر */
  fun screen(key: KeyName?, primary: Boolean): PosAction? = when {
    key == KeyName.F9 || key == KeyName.F12 || (primary && key == KeyName.Enter) -> PosAction.FINISH
    key == KeyName.F2 -> PosAction.CUSTOMER
    key == KeyName.F3 -> PosAction.EDIT_ROW
    key == KeyName.F4 -> PosAction.FREE_ITEM
    key == KeyName.F8 -> PosAction.REPRINT
    else -> null
  }

  /** کادرِ اسکن/جست‌وجو */
  fun field(key: KeyName?, primary: Boolean, queryEmpty: Boolean, hasMatches: Boolean, hasSelection: Boolean): PosAction? = when (key) {
    KeyName.Enter -> if (!primary) PosAction.SUBMIT else null
    KeyName.Down -> if (hasMatches) PosAction.HINT_DOWN else PosAction.ROW_DOWN
    KeyName.Up -> if (hasMatches) PosAction.HINT_UP else PosAction.ROW_UP
    KeyName.Escape -> if (!queryEmpty) PosAction.CLEAR_QUERY else null
    KeyName.Delete -> if (queryEmpty && hasSelection) PosAction.DELETE_ROW else null
    KeyName.Plus, KeyName.Equals -> if (queryEmpty && hasSelection) PosAction.QTY_UP else null
    KeyName.Minus -> if (queryEmpty && hasSelection) PosAction.QTY_DOWN else null
    else -> null
  }
}
