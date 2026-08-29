package ir.vil3ntec.tohid.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 *  دفترِ دکان.
 *
 *  همان یک فایلِ JSON که نسخهٔ وب هم می‌نوشت — با همان کلیدها. پس هم داده‌های
 *  قدیمی مستقیم خوانده می‌شوند و هم فایلِ پشتیبان بین دو نسخه یکی می‌ماند.
 *
 *  محاسبه‌ها عمداً همان فرمولِ نسخهٔ وب‌اند، حتی جاهایی که می‌شد «بهترش» کرد:
 *  عددی که فروشنده روی کاغذ دارد نباید با به‌روزرسانی عوض شود.
 */
class ShopStore(private val context: Context) {

  private val file = File(context.filesDir, "shop-data.json")
  private val json = Json {
    ignoreUnknownKeys = true      // نسخهٔ وب ممکن است فیلدی اضافه کند
    encodeDefaults = true
    isLenient = true
  }

  private val _data = MutableStateFlow(ShopData())
  val data: StateFlow<ShopData> = _data.asStateFlow()

  /**
   *  آیا دفتر از روی دیسک خوانده شده؟
   *
   *  تا پیش از این، صفحه‌ها پیش از خوانده‌شدنِ فایل با دفترِ خالی کشیده
   *  می‌شدند و کاربری که صد قلم کالا داشت، یک لحظه «هنوز محصولی ثبت نشده»
   *  می‌دید. حالا تا وقتی خواندن تمام نشده، اسکلتِ بارگذاری نشان داده
   *  می‌شود.
   */
  private val _loaded = MutableStateFlow(false)
  val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

  suspend fun load() {
    withContext(Dispatchers.IO) {
      if (file.exists()) {
        runCatching { json.decodeFromString<ShopData>(file.readText()) }
          .onSuccess { _data.value = it }
      }
    }
    _loaded.value = true
  }

  suspend fun save(next: ShopData) = withContext(Dispatchers.IO) {
    _data.value = next
    runCatching {
      // اول در فایلِ کنارى، بعد جابه‌جایی: اگر وسطِ نوشتن برق برود،
      // دفترِ دکان نصفه‌نیمه نمی‌ماند
      val tmp = File(file.parentFile, "${file.name}.tmp")
      tmp.writeText(json.encodeToString(next))
      tmp.renameTo(file)
    }
  }

  /**
   *  خواندنِ فایلِ پشتیبان — بدونِ نوشتن.
   *
   *  جدا از `importJson` است تا بشود پیش از جایگزین کردنِ دفتر، نشان داد
   *  داخلِ فایل چه چیزی هست. بازیابی کاری است که برنمی‌گردد؛ کاربر باید
   *  ببیند چه چیزی جای چه چیزی می‌نشیند.
   *
   *  فایلی که JSON درستی باشد ولی مالِ این برنامه نباشد، همه‌جا خالی
   *  می‌خواند و بی‌صدا دفتر را پاک می‌کند. برای همین یک بررسیِ ساده هست:
   *  فایلِ پشتیبانِ واقعی دستِ‌کم یکی از فهرست‌هایش پر است، یا صریحاً
   *  مُهرِ `exportedAt` دارد.
   */
  fun parseBackup(raw: String): Result<ShopData> = runCatching {
    val tree = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
      ?: error("فایل پشتیبان معتبر نیست")
    val parsed = json.decodeFromJsonElement(ShopData.serializer(), tree)
    val stamped = tree.containsKey("exportedAt")
    if (!stamped && backupSize(parsed) == 0) error("این فایل، پشتیبانِ توحید نیست")
    parsed
  }

  /** واردکردنِ داده‌ای که از نسخهٔ وب می‌آید */
  suspend fun importJson(raw: String): Result<ShopData> = withContext(Dispatchers.IO) {
    parseBackup(raw).onSuccess { save(it) }
  }

  /* -------------------------- پشتیبانِ ایمنی -------------------------- */

  private val safety = File(context.filesDir, "before-restore.json")

  /**
   *  یک نسخه از دفترِ فعلی، پیش از بازیابی.
   *
   *  اگر کاربر فایلِ اشتباهی را بازیابی کند، کارِ چند ماهش رفته است. این
   *  نسخه همان‌جا روی گوشی می‌ماند تا یک دکمه بتواند برش گرداند.
   */
  suspend fun keepSafetyCopy() = withContext(Dispatchers.IO) {
    runCatching { safety.writeText(json.encodeToString(_data.value)) }
    Unit
  }

  fun hasSafetyCopy(): Boolean = safety.exists()

  /** برگرداندن به لحظهٔ پیش از بازیابی */
  suspend fun undoRestore(): Result<ShopData> = withContext(Dispatchers.IO) {
    runCatching {
      val parsed = json.decodeFromString<ShopData>(safety.readText())
      save(parsed)
      safety.delete()
      parsed
    }
  }

  fun dropSafetyCopy() {
    runCatching { safety.delete() }
  }

  fun hasData(): Boolean = file.exists()

  /**
   * فایلِ پشتیبان.
   *
   * همان قالبی که نسخهٔ وب می‌سازد و می‌خواند — به‌علاوهٔ `exportedAt`.
   * پس پشتیبانی که اینجا گرفته می‌شود در آنجا باز می‌شود و برعکس؛ کسی که
   * بین دو نسخه جابه‌جا می‌شود دفترش را دوباره نمی‌سازد.
   */
  suspend fun exportBackup(storeName: String = ""): String = withContext(Dispatchers.IO) {
    val tree = json.encodeToJsonElement(ShopData.serializer(), _data.value)
      .let { it as kotlinx.serialization.json.JsonObject }
      .toMutableMap()
    tree["exportedAt"] = kotlinx.serialization.json.JsonPrimitive(
      java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }.format(java.util.Date())
    )
    tree["settings"] = kotlinx.serialization.json.buildJsonObject {
      put("storeName", kotlinx.serialization.json.JsonPrimitive(storeName))
    }
    kotlinx.serialization.json.JsonObject(tree).toString()
  }

  companion object {

    /** چند رکورد در این دفتر هست — برای خلاصهٔ پیش از بازیابی */
    fun backupSize(d: ShopData): Int =
      d.products.size + d.sales.size + d.debtors.size + d.expenses.size +
        d.suppliers.size + d.purchases.size + d.warehouseEntries.size + d.transactions.size

    /* ------------------------- محاسبه‌ها ------------------------- */

    /**
     * مقدارِ فروخته‌شدهٔ یک کالا.
     * فروشِ لغوشده و مقدارِ مرجوعی حساب نمی‌شوند — عیناً مثل نسخهٔ وب.
     */
    fun soldQty(d: ShopData, productId: String): Double =
      d.saleItems
        .filter { item ->
          if (item.productId != productId) return@filter false
          val sale = d.sales.find { it.id == item.saleId }
          sale == null || sale.status != "cancelled"
        }
        .sumOf { it.quantity - it.returnedQty }

    /** موجودی = آنچه وارد انبار شده، منهای آنچه فروخته شده */
    fun stock(d: ShopData, productId: String): Double =
      d.warehouseEntries.filter { it.productId == productId }.sumOf { it.units } -
        soldQty(d, productId)

    fun cartons(d: ShopData, productId: String): Double =
      d.warehouseEntries.filter { it.productId == productId }.sumOf { it.cartons }

    /** تمام‌شده | موجودی کم | موجودی کافی */
    fun stockStatus(d: ShopData, product: Product): String {
      val s = stock(d, product.id)
      return when {
        s <= 0 -> "out"
        s <= product.minStock -> "low"
        else -> "ok"
      }
    }

    /** بدهیِ یک قرض‌دار: آنچه گرفته منهای آنچه پس داده */
    fun debt(d: ShopData, debtorId: String): Double =
      d.transactions.filter { it.debtorId == debtorId }
        .sumOf { if (it.type == "give") it.amount else -it.amount }

    /**
     *  بدهیِ ما به یک تأمین‌کننده.
     *
     *  عمداً از `totalAmount − paidAmount` حساب می‌شود، نه از فیلدِ `debt`
     *  که روی خودِ خرید نوشته شده — همان کاری که نسخهٔ وب می‌کند.
     *
     *  دلیلش یک اشکالِ واقعی است: فایلِ پشتیبانِ نسخه‌های قدیمی فیلدِ
     *  `debt` را ندارد. آن‌وقت این‌طرف صفر خوانده می‌شد و بدهیِ
     *  تأمین‌کننده صفر یا حتی منفی درمی‌آمد، در حالی که نسخهٔ وب همان
     *  فایل را درست می‌خواند. این فرمول از خودِ دو عددِ اصلی حساب می‌کند،
     *  پس فایلِ ناقص هم درست خوانده می‌شود.
     */
    fun supplierDebt(d: ShopData, supplierId: String): Double {
      val mine = d.purchases.filter { it.supplierId == supplierId }
      val billed = mine.sumOf { it.totalAmount }
      val paid = mine.sumOf { it.paidAmount } +
        d.supplierPayments.filter { it.supplierId == supplierId }.sumOf { it.amount }
      return billed - paid
    }

    fun barcodeIndex(d: ShopData): Map<String, String> = buildMap {
      d.products.forEach { p -> p.barcodes.forEach { code -> put(code, p.id) } }
    }
  }
}
