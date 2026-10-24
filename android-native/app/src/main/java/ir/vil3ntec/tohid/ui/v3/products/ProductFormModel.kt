package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.WarehouseEngine

/**
 *  یک کالا در فرمِ «کالای تازه» (چندردیفی) یا «ویرایش کالا» — همه رشته، همان‌طور
 *  که تایپ می‌شود. قیمت‌ها و مقدارها خام‌اند (رقمِ لاتین، بی جداکننده؛ کادرِ پولِ
 *  کیت خودش جدا می‌کند).
 */
data class ProductRowDraft(
  val key: Int = 0,
  val name: String = "",
  val category: String = "",
  val unit: String = "",
  val purchase: String = "",
  val sale: String = "",
  val minStock: String = "",
  val barcode: String = "",
  //  فقط ویرایش («بیشتر»)
  val subcategory: String = "",
  val supplierId: String = "",
  val active: Boolean = true,
  val saleUnit: String = "",
  val unitFactor: String = "",
)

/**
 *  قاعده‌های فرمِ کالا — عیناً همان‌هایی که امروز هست، فقط جای نشان دادنشان عوض
 *  شده (زیرِ همان کادر، نه یک پیامِ کلی):
 *
 *   • کالای تازه = قاعدهٔ `BulkProductSheet` (`bpReadyRows` ِ وب): نام، دسته، واحد و
 *     هر دو قیمت (≥ ۰). ردیفِ دست‌نخورده نادیده گرفته می‌شود، ردیفِ نیمه‌پر خطاست.
 *   • ویرایش = قاعدهٔ `ProductDialog`: نام و قیمتِ فروش؛ قیمتِ خریدِ خالی ⇐ ۰؛ واحدِ
 *     فروشِ دوم ضریب می‌خواهد. باقیِ سنجش (نامِ تکراری، بارکدِ تکراری) کارِ
 *     `WarehouseEngine` است و پیامش بالای فرم می‌آید.
 *
 *  پیش‌نویسِ موتور (`ProductDraft`) هم عیناً همان است که آن دو پنجره می‌ساختند.
 *  آزمون: `ProductFormModelTest`.
 */
object ProductFormModel {

  /** کادرهایی که خطا می‌گیرند — به ترتیبِ دیده شدن در فرم (نخستین خطا = کمترین) */
  enum class Field { NAME, CATEGORY, PURCHASE, SALE, UNIT, UNIT_FACTOR }

  /** نخستین کادرِ خراب به ترتیبِ فرم، یا null */
  fun firstField(errors: Map<Field, Problem>): Field? = errors.keys.minByOrNull { it.ordinal }

  /** خطای درجای یک کادر */
  enum class Problem { REQUIRED, NOT_A_NUMBER }

  enum class Mode { NEW, EDIT }

  /** ردیفی که هیچ چیزش پر نشده — نادیده گرفته می‌شود، خطا نیست */
  fun untouched(r: ProductRowDraft): Boolean =
    r.name.isBlank() && r.purchase.isBlank() && r.sale.isBlank() && r.barcode.isBlank() && r.minStock.isBlank()

  private fun nonNegative(s: String): Boolean = (s.toDoubleOrNull() ?: -1.0) >= 0

  /** آمادهٔ ثبت؟ — همان شرطِ `ProductRow.ready` (کالای تازه) */
  fun ready(r: ProductRowDraft): Boolean =
    r.name.isNotBlank() && nonNegative(r.purchase) && nonNegative(r.sale) && r.unit.isNotBlank() && r.category.isNotBlank()

  /** خطای هر کادر — صفحه فقط خطای کادرهایی را نشان می‌دهد که لمس شده‌اند یا پس از «ذخیره» */
  fun errors(r: ProductRowDraft, mode: Mode): Map<Field, Problem> = buildMap {
    if (r.name.isBlank()) put(Field.NAME, Problem.REQUIRED)
    when (mode) {
      Mode.NEW -> {
        if (r.category.isBlank()) put(Field.CATEGORY, Problem.REQUIRED)
        if (r.unit.isBlank()) put(Field.UNIT, Problem.REQUIRED)
        number(r.purchase, required = true)?.let { put(Field.PURCHASE, it) }
      }
      Mode.EDIT -> {
        number(r.purchase, required = false)?.let { put(Field.PURCHASE, it) }
        if (r.saleUnit.isNotBlank()) {
          val f = r.unitFactor.toDoubleOrNull()
          when {
            r.unitFactor.isBlank() -> put(Field.UNIT_FACTOR, Problem.REQUIRED)
            f == null || f <= 0 -> put(Field.UNIT_FACTOR, Problem.NOT_A_NUMBER)
          }
        }
      }
    }
    number(r.sale, required = true)?.let { put(Field.SALE, it) }
    //  حداقلِ موجودیِ ناخوانا امروز هم بی‌صدا صفر است — خطا نیست
  }

  private fun number(s: String, required: Boolean): Problem? = when {
    s.isBlank() -> if (required) Problem.REQUIRED else null
    nonNegative(s) -> null
    else -> Problem.NOT_A_NUMBER
  }

  /** نخستین ردیفِ نیمه‌پر (اندیس) یا -۱ — «ذخیره» به همان‌جا می‌پرد */
  fun firstBroken(rows: List<ProductRowDraft>): Int = rows.indexOfFirst { !untouched(it) && !ready(it) }

  fun readyCount(rows: List<ProductRowDraft>): Int = rows.count(::ready)

  /** نتیجهٔ «ذخیرهٔ همه»: یا پیش‌نویس‌ها، یا ردیفِ خراب، یا «هیچ ردیفِ کاملی نیست» */
  sealed interface Check {
    data class Ok(val drafts: List<WarehouseEngine.ProductDraft>) : Check
    /** شمارهٔ ردیف از یک */
    data class Broken(val index: Int) : Check
    data object Empty : Check
  }

  fun check(rows: List<ProductRowDraft>): Check {
    val broken = firstBroken(rows)
    if (broken >= 0) return Check.Broken(broken)
    val ready = rows.filter(::ready)
    if (ready.isEmpty()) return Check.Empty
    return Check.Ok(ready.map(::draftNew))
  }

  /** پیش‌نویسِ کالای تازه — همان `ProductRow.draft()` */
  fun draftNew(r: ProductRowDraft) = WarehouseEngine.ProductDraft(
    name = r.name,
    category = r.category,
    unit = r.unit,
    purchasePrice = r.purchase.toDoubleOrNull() ?: 0.0,
    salePrice = r.sale.toDoubleOrNull() ?: -1.0,
    minStock = r.minStock.toDoubleOrNull() ?: 0.0,
    barcode = r.barcode,
  )

  /** پیش‌نویسِ ویرایش — همان دکمهٔ «ذخیره تغییرات» ِ `ProductDialog` */
  fun draftEdit(r: ProductRowDraft) = WarehouseEngine.ProductDraft(
    name = r.name,
    category = r.category,
    unit = r.unit,
    purchasePrice = r.purchase.toDoubleOrNull() ?: 0.0,
    salePrice = r.sale.toDoubleOrNull() ?: -1.0,
    minStock = r.minStock.toDoubleOrNull() ?: 0.0,
    barcode = r.barcode,
    subcategory = r.subcategory,
    supplierId = r.supplierId,
    active = r.active,
    saleUnit = r.saleUnit,
    //  خالی یا نادرست ⇒ NaN تا موتور با پیامِ روشن ردش کند
    unitFactor = if (r.saleUnit.isBlank()) 1.0 else r.unitFactor.toDoubleOrNull() ?: Double.NaN,
  )

  /** فرمِ ویرایش از کالای موجود — همان `ProductFormState.of` */
  fun of(p: Product) = ProductRowDraft(
    name = p.name,
    category = p.category,
    unit = p.unit,
    purchase = numberText(p.purchasePrice),
    sale = numberText(p.salePrice),
    minStock = if (p.minStock == 0.0) "" else numberText(p.minStock),
    barcode = p.barcodes.firstOrNull().orEmpty(),
    subcategory = p.subcategory,
    supplierId = p.supplierId,
    active = p.active,
    saleUnit = p.saleUnit,
    unitFactor = if (p.saleUnit.isBlank()) "" else p.unitFactor.toBigDecimal().stripTrailingZeros().toPlainString(),
  )

  /** عدد برای کادر: بی اعشارِ بی‌مورد و با رقمِ لاتین */
  fun numberText(value: Double): String =
    if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

  /** سودِ هر واحد وقتی هر دو قیمت خوانا و نامنفی‌اند؛ وگرنه null */
  fun unitProfit(r: ProductRowDraft): Double? {
    val buy = r.purchase.toDoubleOrNull() ?: return null
    val sell = r.sale.toDoubleOrNull() ?: return null
    return if (buy >= 0 && sell >= 0) sell - buy else null
  }

  /** تکثیرِ ردیف — بی بارکد (بارکد یکتاست) */
  fun duplicate(r: ProductRowDraft, key: Int): ProductRowDraft = r.copy(key = key, barcode = "")

  /** یک ردیف را عوض می‌کند و بقیه دست نمی‌خورند */
  fun update(rows: List<ProductRowDraft>, key: Int, change: (ProductRowDraft) -> ProductRowDraft): List<ProductRowDraft> =
    rows.map { if (it.key == key) change(it) else it }

  /** کلیک روی تراشهٔ انتخاب‌شده آن را برمی‌دارد (همان `ChipPicker`) */
  fun toggle(current: String, option: String): String = if (current == option) "" else option
}

/**
 *  کدام خطاها دیده شوند: خطای هر کادر پس از ترکِ همان کادر (`touch`)، و همه پس از
 *  زدنِ «ذخیره» (`all`) — نه از همان لحظهٔ باز شدنِ فرمِ خالی.
 */
data class FormTouched(val keys: Set<String> = emptySet(), val all: Boolean = false) {
  fun touch(rowKey: Int, f: ProductFormModel.Field): FormTouched = copy(keys = keys + "$rowKey:${f.name}")

  fun visible(rowKey: Int, errors: Map<ProductFormModel.Field, ProductFormModel.Problem>): Map<ProductFormModel.Field, ProductFormModel.Problem> =
    if (all) errors else errors.filterKeys { "$rowKey:${it.name}" in keys }
}
