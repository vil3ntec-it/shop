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

  /**
   *  دفتر خوانده شد و خلاصه‌اش هم تازه شد.
   *
   *  با باز شدنِ برنامه یک بار صدا زده می‌شود. اگر خلاصه از دست رفته
   *  باشد — نسخهٔ تازه روی دفترِ قدیمی، یا پاک شدنِ حافظهٔ برنامه — همین
   *  یک بار دوباره ساخته می‌شود و کارهای پس‌زمینه از آن به بعد ارزان
   *  می‌مانند.
   */
  suspend fun loadAndSummarize() {
    load()
    withContext(Dispatchers.IO) { LedgerSummary.write(context, _data.value) }
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
    //  خلاصهٔ کوچک، کنارِ دفتر. نگهبانِ پس‌زمینه همین را می‌خواند و دیگر
    //  لازم نیست هر ربع ساعت کلِ دفتر را تجزیه کند — شرحش سرِ
    //  `LedgerSummary`.
    LedgerSummary.write(context, next)
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

  /* ------------------------ دفتر، به نامِ حساب ------------------------ */

  /*
   *  چرا دفتر باید اسمِ صاحبش را داشته باشد.
   *
   *  تا دیروز روی یک گوشی فقط یک فایل بود: `shop-data.json`. خروج از حساب
   *  توکن را پاک می‌کرد ولی این فایل سرِ جایش می‌ماند. نفرِ بعدی که روی
   *  همان گوشی وارد می‌شد، «سایه» خالی داشت — یعنی همگام‌سازی همهٔ ردیف‌های
   *  نفرِ قبلی را «تغییرِ تازه» می‌دید و صاف می‌فرستاد داخلِ دکانِ او.
   *
   *  یک گوشیِ مشترک در دکان، یا فروختنِ گوشی، یا حتی امتحان کردنِ دو حساب
   *  کافی بود.
   *
   *  حالا دفترِ هر حساب زیرِ نامِ خودش بایگانی می‌شود. عوض شدنِ حساب یعنی
   *  دفترِ قبلی می‌رود کنار و دفترِ همین حساب باز می‌شود؛ هیچ ردیفی از یکی
   *  به دیگری نشت نمی‌کند و هیچ‌کدام هم پاک نمی‌شود.
   */

  private fun vault(key: String) = File(context.filesDir, "ledger-${safeKey(key)}.json")

  /** بایگانی کردنِ دفترِ فعلی زیرِ نامِ حساب، و خالی کردنِ دفترِ روی میز */
  suspend fun stashTo(key: String) = withContext(Dispatchers.IO) {
    runCatching {
      if (file.exists()) file.copyTo(vault(key), overwrite = true)
      file.delete()
    }
    LedgerSummary.clear(context)
    _data.value = ShopData()
    Unit
  }

  /** باز کردنِ دفترِ یک حساب — اگر بایگانی نداشت، دفترِ خالی */
  suspend fun openFrom(key: String) {
    withContext(Dispatchers.IO) {
      val saved = vault(key)
      runCatching {
        if (saved.exists()) saved.copyTo(file, overwrite = true) else file.delete()
      }
    }
    _loaded.value = false
    _data.value = ShopData()
    //  خلاصهٔ حسابِ قبلی نباید روی حسابِ تازه بماند؛ با خواندنِ دفتر از نو
    //  ساخته می‌شود
    LedgerSummary.clear(context)
    loadAndSummarize()
  }

  /** نامِ فایل نباید از کاراکترهای شناسه آسیب ببیند */
  private fun safeKey(key: String): String =
    key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(48).ifBlank { "anon" }

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
     *  جدولِ موجودی — یک بار ساخته می‌شود، بارها خوانده.
     *
     *  این کلاس یک باگِ کندیِ واقعی را می‌بندد. حسابِ قبلی برای **هر
     *  کالا** کلِ اقلامِ فروش را می‌گشت، و برای **هر قلم** هم کلِ
     *  فاکتورها را دنبالِ وضعیتِ «لغو» می‌گردید. یعنی هزینه‌اش ضربِ سه
     *  عدد بود: کالاها × اقلامِ فروش × فاکتورها.
     *
     *  صفحهٔ محصولات این را برای همهٔ کالاها صدا می‌زد و با هر حرفی که
     *  در جستجو زده می‌شد از نو. دکانی با چند صد کالا و چند هزار فاکتور،
     *  همان‌جا می‌ایستاد.
     *
     *  حالا یک بار روی هر سه فهرست رد می‌شویم و سه نگاشت می‌سازیم؛ بعد
     *  هر پرسش یک خواندن است. نتیجه‌ها مو‌به‌مو همان‌اند — فقط راهش عوض
     *  شده.
     */
    class StockIndex(d: ShopData) {
      private val entered = HashMap<String, Double>()
      private val cartonsIn = HashMap<String, Double>()
      private val sold = HashMap<String, Double>()

      init {
        d.warehouseEntries.forEach { e ->
          entered[e.productId] = (entered[e.productId] ?: 0.0) + e.units
          cartonsIn[e.productId] = (cartonsIn[e.productId] ?: 0.0) + e.cartons
        }
        // وضعیتِ فاکتورها یک بار در یک نگاشت می‌نشیند تا برای هر قلم
        // دوباره جستجو نشود
        val cancelled = HashSet<String>()
        d.sales.forEach { if (it.status == "cancelled") cancelled += it.id }
        d.saleItems.forEach { item ->
          if (item.saleId in cancelled) return@forEach
          sold[item.productId] =
            (sold[item.productId] ?: 0.0) + (item.quantity - item.returnedQty)
        }
      }

      fun soldQty(productId: String): Double = sold[productId] ?: 0.0

      fun stock(productId: String): Double =
        (entered[productId] ?: 0.0) - (sold[productId] ?: 0.0)

      fun cartons(productId: String): Double = cartonsIn[productId] ?: 0.0

      fun status(product: Product): String {
        val s = stock(product.id)
        return when {
          s <= 0 -> "out"
          s <= product.minStock -> "low"
          else -> "ok"
        }
      }
    }

    /*
     *  آخرین جدولِ ساخته‌شده، با همان دفتری که از رویش ساخته شد.
     *
     *  `ShopData` تغییرناپذیر است، پس اگر همان شیء باشد جدول هم هنوز
     *  درست است. یک خانه بس است: در هر لحظه یک دفتر بیشتر روی صفحه
     *  نیست.
     */
    private var indexedData: ShopData? = null
    private var indexed: StockIndex? = null

    @Synchronized
    fun index(d: ShopData): StockIndex {
      val ready = indexed
      if (ready != null && indexedData === d) return ready
      val fresh = StockIndex(d)
      indexedData = d
      indexed = fresh
      return fresh
    }

    /**
     *  جدولِ فروش — همان کارِ `StockIndex`، برای سمتِ فاکتورها.
     *
     *  ── چه اشکالی را می‌بندد ──────────────────────────────────────
     *  `ReportEngine` سه جای پیاپی همان اشتباهی را می‌کرد که یک بار در
     *  `StockIndex` درست شده بود:
     *
     *   • گزارشِ بازه، برای **هر فاکتور** کلِ اقلامِ فروش را می‌گشت.
     *   • گزارشِ سودِ محصولات، برای **هر کالا** کلِ اقلام را می‌گشت و
     *     برای هر قلمِ پیداشده یک بار هم کلِ فاکتورها را — یعنی ضربِ سه
     *     عدد: کالاها × اقلام × فاکتورها.
     *   • مرجوعی‌ها هم برای هر ردیف، فاکتور و قلمش را جستجو می‌کردند.
     *
     *  دکانی با سیصد کالا و بیست هزار قلمِ فروش، سرِ باز کردنِ تبِ
     *  «محصولات» گزارشات می‌ایستاد.
     *
     *  حالا یک بار روی دو فهرست رد می‌شویم و چهار نگاشت می‌سازیم؛ بعد
     *  هر پرسش یک خواندن است. نتیجه‌ها مو‌به‌مو همان‌اند.
     *  ──────────────────────────────────────────────────────────────
     */
    class SalesIndex(d: ShopData) {

      /** فاکتور از روی شناسه */
      val saleById: Map<String, Sale> = d.sales.associateBy { it.id }

      /** اقلامِ هر فاکتور */
      val itemsBySale: Map<String, List<SaleItem>> = d.saleItems.groupBy { it.saleId }

      /** قلمِ فروش از روی شناسه — برای ردیف‌های مرجوعی */
      val itemById: Map<String, SaleItem> = d.saleItems.associateBy { it.id }

      /** تعدادِ خالص و سودِ یک کالا، از آغاز تا حالا */
      data class Sold(val quantity: Double, val profit: Double)

      //  [۰] = تعداد، [۱] = سود. یک آرایه به‌جای دو نگاشت، چون این جدول
      //  ممکن است برای چند صد کالا ساخته شود.
      private val perProduct = HashMap<String, DoubleArray>()

      init {
        /*
         *  فاکتورِ لغوشده اصلاً شمرده نمی‌شود. قلمی که فاکتورش در دفتر
         *  نیست (ردیفِ یتیم از یک دفترِ قدیمی) عمداً شمرده **می‌شود** —
         *  دقیقاً همان کاری که حسابِ قبلی می‌کرد، تا عددِ گزارش با
         *  به‌روزرسانی عوض نشود.
         */
        val cancelled = HashSet<String>()
        d.sales.forEach { if (it.status == "cancelled") cancelled += it.id }
        d.saleItems.forEach { item ->
          if (item.saleId in cancelled) return@forEach
          val net = item.quantity - item.returnedQty
          val slot = perProduct.getOrPut(item.productId) { DoubleArray(2) }
          slot[0] += net
          slot[1] += net * (item.unitPrice - item.purchasePrice)
        }
      }

      fun product(productId: String): Sold {
        val slot = perProduct[productId] ?: return Sold(0.0, 0.0)
        return Sold(slot[0], slot[1])
      }
    }

    private var salesIndexedData: ShopData? = null
    private var salesIndexed: SalesIndex? = null

    /** جدولِ فروش، با همان قاعدهٔ یک‌خانه‌ایِ `index` */
    @Synchronized
    fun salesIndex(d: ShopData): SalesIndex {
      val ready = salesIndexed
      if (ready != null && salesIndexedData === d) return ready
      val fresh = SalesIndex(d)
      salesIndexedData = d
      salesIndexed = fresh
      return fresh
    }

    /**
     * مقدارِ فروخته‌شدهٔ یک کالا.
     * فروشِ لغوشده و مقدارِ مرجوعی حساب نمی‌شوند — عیناً مثل نسخهٔ وب.
     */
    fun soldQty(d: ShopData, productId: String): Double = index(d).soldQty(productId)

    /** موجودی = آنچه وارد انبار شده، منهای آنچه فروخته شده */
    fun stock(d: ShopData, productId: String): Double = index(d).stock(productId)

    fun cartons(d: ShopData, productId: String): Double = index(d).cartons(productId)

    /** تمام‌شده | موجودی کم | موجودی کافی */
    fun stockStatus(d: ShopData, product: Product): String = index(d).status(product)

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
