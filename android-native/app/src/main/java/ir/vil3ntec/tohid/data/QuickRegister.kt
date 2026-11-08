package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.product.DecimalText

/**
 *  S03 (POS-17) — «ثبتِ سریعِ کالا» در خودِ صندوق: بارکدِ ناشناس ⇒ نام، قیمتِ فروش و واحد (و موجودیِ همان که در دست
 *  است) ⇒ کالا با همان بارکد ثبت و همان‌جا به فاکتور. فقط دفترِ همین دستگاه، پس آفلاین هم. صفحه پشتِ
 *  `TillGoodsPref.quickRegister` (پیش‌فرض خاموش) است؛ این‌جا فقط منطقِ خالص.
 */
object QuickRegister {

  /** یادداشتِ ردیفِ موجودیِ نخست */
  const val ENTRY_NOTE = "ثبتِ سریع در صندوق"  // داده: یادداشتِ ذخیره‌شدهٔ ورودِ انبار

  /**
   *  نقشی که کالا ثبت می‌کند — همان `canWrite(role, "products")` ِ سرور (`PermCore`): صاحب، مدیر، شاگرد و انباردار
   *  بله؛ صندوق‌دار، جمع‌آور و حسابدار نه. صاحب (نقشِ خالی) و نقشی که این نسخه نمی‌شناسد مثلِ `RoleGate` باز است
   *  (سرور خودش می‌سنجد).
   */
  fun allowed(role: String): Boolean {
    val r = role.trim().lowercase()
    return r.isEmpty() || r !in PermCore.ROLES || PermCore.canWrite(r, "products")
  }

  /** صندوق‌دارِ بی‌مجوز فقط «قلمِ آزاد» می‌بیند — آن هم اگر سقفِ قلمِ آزادش بسته (صفر) نباشد (POS-10) */
  fun freeLineOpen(policy: ir.vil3ntec.tohid.core.pos.LinePricing.Policy): Boolean =
    ir.vil3ntec.tohid.core.pos.LinePricing.checkFreeLine(policy, 0) == null

  /** بارکدِ ناشناس در صندوق: راهِ بعدی */
  enum class Next {
    /** «ثبتِ سریع» خاموش ⇒ همان «ثبتِ کالا» ⇐ فرمِ کالا */
    PRODUCT_FORM,
    /** پنجره/برگهٔ «ثبتِ سریع» در خودِ صندوق */
    QUICK,
    /** صندوق‌دارِ بی‌مجوز: فقط «قلمِ آزاد» */
    FREE_LINE,
    /** بی‌مجوز و سقفِ قلمِ آزاد بسته: فقط پیام */
    NONE,
  }

  /**
   *  راهِ بارکدِ ناشناس — یک قاعده برای صندوقِ کامپیوتر (`QuickTill`) و گوشیِ نسخهٔ ۳ (`QuickTillModel`).
   *  @param on `TillGoodsPref.quickRegister`؛ @param may [allowed]؛ @param freeLineOpen [freeLineOpen]
   */
  fun next(on: Boolean, may: Boolean, freeLineOpen: Boolean): Next = when {
    !on -> Next.PRODUCT_FORM
    may -> Next.QUICK
    freeLineOpen -> Next.FREE_LINE
    else -> Next.NONE
  }

  data class Draft(val name: String = "", val salePrice: String = "", val unit: String = "عدد", val stock: String = "1")  // داده: واحدِ پیش‌فرضِ ذخیره‌شده

  sealed interface Result {
    /**
     *  @param quantity آنچه به فاکتور می‌رود: یک واحد، یا کمتر اگر موجودیِ نوشته‌شده کمتر است
     *  @param base دفتری که [data] از آن ساخته شد (ورودیِ [register]) — پایهٔ صریحِ `ShopStore.save`
     */
    data class Ok(val data: ShopData, val productId: String, val quantity: Double, val base: ShopData) : Result
    data class Failed(val message: String) : Result
  }

  fun register(d: ShopData, barcode: String, draft: Draft, today: String, now: Long, newId: () -> String): Result {
    val code = barcode.trim()
    if (code.isEmpty()) return Result.Failed(StrLedger.barcodeEmpty)
    val price = DecimalText.scaled(draft.salePrice, 2)?.takeIf { it > 0 } ?: return Result.Failed(StrLedger.salePriceNeeded)
    val unit = draft.unit.trim()
    if (unit.isEmpty()) return Result.Failed(StrLedger.unitNeeded)
    val stock = if (draft.stock.isBlank()) 0L else DecimalText.scaled(draft.stock, 3)?.takeIf { it >= 0 }
      ?: return Result.Failed(StrLedger.stockAsNumber)
    val made = when (val r = WarehouseEngine.addProduct(
      d, WarehouseEngine.ProductDraft(name = draft.name, unit = unit, salePrice = price / 100.0, barcode = code), now, newId,
    )) {
      is WarehouseEngine.Result.Failed -> return Result.Failed(r.message)
      is WarehouseEngine.Result.Ok -> r
    }
    if (stock == 0L) return Result.Ok(made.data, made.id, 0.0, d)
    val units = stock / 1000.0
    return when (val e = WarehouseEngine.addEntry(
      made.data, WarehouseEngine.EntryDraft(productId = made.id, units = units, unit = unit, date = today, notes = ENTRY_NOTE), today, now, newId,
    )) {
      is WarehouseEngine.Result.Failed -> Result.Failed(e.message)
      is WarehouseEngine.Result.Ok -> Result.Ok(e.data, made.id, minOf(1.0, units), d)
    }
  }
}
