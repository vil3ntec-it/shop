package ir.vil3ntec.tohid.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 *  سبدِ نیمه‌کاره = پیش‌نویسِ فاکتور (POS-13، POS-14).
 *
 *  اگر وسطِ فروش برنامه بسته شود — تماس بیاید، باتری تمام شود، برق برود،
 *  کاربر برود بخشِ دیگر — سبد نباید بپرد.
 *
 *  ── کجا ──────────────────────────────────────────────────────────────
 *  فایلِ جدای `sale-draft.json` با `DiskWrite` (فایلِ کناری + `fsync` +
 *  جابه‌جاییِ اتمی): برق که برود، یا پیش‌نویسِ کهنه سرِ جایش است یا تازه،
 *  نه نصفه. پیش‌تر در SharedPreferences بود با `apply()` — یعنی ناهمگام و
 *  روی کامپیوتر بی `fsync`. پیش‌نویسِ قدیمی همان‌جا یک بار خوانده و به
 *  فایل آورده می‌شود.
 *
 *  هر تغییر همان لحظه در حافظه می‌نشیند و نوشتنِ فایل روی یک رشتهٔ جدا
 *  می‌رود (همیشه آخرین حالت) تا اسکن‌های پشتِ‌سرِ‌هم صفحه را کُند نکنند.
 *
 *  ── کلیدِ فاکتور ─────────────────────────────────────────────────────
 *  شرحش سرِ `SaleDraft` است: فروش با `key`ِ همین پیش‌نویس ثبت می‌شود و
 *  پیش‌نویس فقط **پس از** رسیدنِ دفتر به دیسک پاک می‌شود (`post`).
 */
class CartStore(context: Context) : HeldCart {

  private val prefs = context.getSharedPreferences("tohid", Context.MODE_PRIVATE)
  private val file = File(context.filesDir, FILE)
  /** برای چاپ و کشوی پس از فروش (HW-02/03) — هر دو پیش‌فرض خاموش */
  private val app: Context = context.applicationContext ?: context

  //  رسیدهایی که پیش از بسته شدنِ برنامه در صفِ چاپ ماندند، به ترتیب ادامه پیدا کنند
  init { runCatching { ir.vil3ntec.tohid.print.PrintSpooler.start(app) } }

  private val initial: SaleDraft? = load()

  @Volatile private var key: String? = initial?.key ?: initial?.takeIf { it.lines.isNotEmpty() }?.let { newKey() }

  private val _lines = MutableStateFlow(initial?.cart().orEmpty())
  val lines: StateFlow<List<SalesEngine.CartLine>> = _lines.asStateFlow()

  /** قرض‌داری که سبد به حسابش می‌رود — میان‌بری برای فروشِ نسیه */
  private val _debtorId = MutableStateFlow(initial?.debtorId)
  val debtorId: StateFlow<String?> = _debtorId.asStateFlow()

  /** کدهای تخفیفِ همین سبد (N3) — `PromoLive` می‌نویسد و از همین برمی‌گرداند */
  private val _coupons = MutableStateFlow(initial?.takeIf { it.lines.isNotEmpty() }?.coupons.orEmpty())
  val coupons: StateFlow<List<String>> = _coupons.asStateFlow()

  /**
   *  پیش‌نویسی که از بارِ پیش مانده و هنوز از کاربر پرسیده نشده
   *  (`DraftRecoveryPrompt`). تا پاسخ، همان ردیف‌ها در سبد هستند — مثلِ پیش.
   */
  private val _recovered = MutableStateFlow(initial?.takeIf { it.lines.isNotEmpty() }?.copy(key = key))
  val recovered: StateFlow<SaleDraft?> = _recovered.asStateFlow()

  private val poster = SalePost()
  /** «ثبتِ فروش» تا پایانِ ثبت قفل است (POS-14) */
  val posting: StateFlow<Boolean> = poster.busy

  private fun load(): SaleDraft? {
    if (file.isFile) return SaleDraftCodec.decode(runCatching { file.readText(Charsets.UTF_8) }.getOrNull())
    //  پیش‌نویسِ نسخهٔ پیش (SharedPreferences، همان شکلِ وب)
    val legacy = SaleDraftCodec.decode(prefs.getString(LEGACY_KEY, null)) ?: return null
    return legacy.copy(debtorId = prefs.getString(LEGACY_DEBTOR_KEY, null))
  }

  private fun persist() {
    val draft = SaleDraft.of(key, _lines.value, _debtorId.value, System.currentTimeMillis(), _coupons.value)
    write(file, draft) {
      //  پیش‌نویس به فایل رسید ⇒ نسخهٔ کهنه دیگر لازم نیست و نباید روزی دوباره زنده شود
      if (prefs.contains(LEGACY_KEY) || prefs.contains(LEGACY_DEBTOR_KEY)) {
        prefs.edit().remove(LEGACY_KEY).remove(LEGACY_DEBTOR_KEY).apply()
      }
    }
  }

  /** POS-12: جابه‌جاییِ فاکتور در جریان است ⇒ سبد دست نمی‌خورد (`HeldCart.freeze`) */
  @Volatile private var frozen = false

  /** `false` ⇒ وسطِ جابه‌جاییِ فاکتور بود و چیزی عوض نشد — صفحه باید بگوید (`NOT_ADDED`) */
  fun set(lines: List<SalesEngine.CartLine>): Boolean {
    if (frozen) return false
    //  سبدِ خالی کلید ندارد؛ نخستین قلم کلیدِ تازه می‌سازد
    key = if (lines.isEmpty()) null else key ?: newKey()
    _lines.value = lines
    if (lines.isEmpty()) _coupons.value = emptyList()
    persist()
    return true
  }

  fun setDebtor(id: String?): Boolean {
    if (frozen) return false
    _debtorId.value = id
    persist()
    return true
  }

  /** `false` ⇒ وسطِ جابه‌جاییِ فاکتور بود و کدها همان ماندند (`HeldCart.freeze`) */
  fun setCoupons(codes: List<String>): Boolean {
    if (frozen) return false
    if (codes != _coupons.value) {
      _coupons.value = codes
      persist()
    }
    return true
  }

  /** S1 — حساب عوض شد: کدهای حسابِ قبلی فقط از حافظه می‌روند (فایل را `LedgerOwner` خالی کرده) */
  fun forgetCoupons() {
    _coupons.value = emptyList()
  }

  fun clear(): Boolean {
    if (frozen) return false
    wipe()
    return true
  }

  private fun wipe() {
    key = null
    _lines.value = emptyList()
    _debtorId.value = null
    _coupons.value = emptyList()
    persist()
  }

  /** کلیدِ یکتای فاکتورِ همین سبد — شناسهٔ فروشی که از آن ثبت می‌شود */
  fun saleKey(): String = key ?: newKey().also { key = it }

  /**
   *  ثبتِ همین سبد (POS-14): یک ثبت در هر لحظه، با کلیدِ فاکتور؛ سبد فقط
   *  وقتی پاک می‌شود که `save` بگوید دفتر به دیسک رسید.
   */
  suspend fun post(store: ShopStore, record: (key: String) -> SalesEngine.Result): SalePost.Outcome {
    //  ضربه‌ای که پس از پایانِ ثبتِ قبلی رسید سبدِ کهنهٔ صفحه را در دست دارد، نه سبدِ تازه را
    //  POS-12: F9/F12ِ وسطِ جابه‌جاییِ فاکتور ثبت نمی‌شود (سبد نیمه‌جابه‌جا است)
    if (frozen || _lines.value.isEmpty()) return SalePost.Outcome.Busy
    val out = poster.run(saleKey(), record, { store.save(it) }, offMain = StockLedger.isOn(store.data.value)) { done -> if (key == done) wipe() }
    //  فروش ثبت شده و روی دیسک است؛ چاپ پس از آن و جدا از آن (S16: چاپگرِ خاموش فروش را نگه نمی‌دارد)
    if (out is SalePost.Outcome.Done) {
      runCatching { ir.vil3ntec.tohid.print.PrintSpooler.afterSale(app, out.ok.data, out.ok.saleId) }
    }
    return out
  }

  /** پاسخِ پرسشِ بازیابی: `keep` = ادامه با همین ردیف‌ها؛ `null` = دور انداختن */
  fun resolveRecovery(keep: SaleDraft?) {
    _recovered.value = null
    if (keep == null) { wipe(); return }
    key = keep.key ?: key
    _lines.value = keep.cart()
    _debtorId.value = keep.debtorId
    _coupons.value = keep.coupons
    persist()
  }

  /* ── POS-12: فاکتورهای نگه‌داشته (`HeldInvoiceStore`) ─────────────────── */

  override val busy: Boolean get() = posting.value

  override fun freeze(on: Boolean) { frozen = on }

  override val recovering: Boolean get() = _recovered.value != null

  override fun snapshot(): SaleDraft =
    SaleDraft.of(if (_lines.value.isEmpty()) null else saleKey(), _lines.value, _debtorId.value, System.currentTimeMillis(), _coupons.value)

  /**
   *  سبد را با فاکتورِ دیگری عوض می‌کند (`null` = سبدِ خالی) و تا رسیدنش به
   *  دیسک صبر می‌کند — جابه‌جاییِ فاکتورها بی این صبر می‌توانست میانِ دو فایل
   *  فاکتوری را گم کند.
   */
  override suspend fun replace(draft: SaleDraft?): Boolean {
    val lines = draft?.cart().orEmpty()
    key = if (lines.isEmpty()) null else draft?.key ?: newKey()
    _lines.value = lines
    _debtorId.value = draft?.debtorId
    //  N3: کدهای تخفیف مالِ همان فاکتورند — با آن می‌روند و برمی‌گردند، به فاکتورِ دیگر نمی‌رسند
    _coupons.value = if (lines.isEmpty()) emptyList() else draft?.coupons.orEmpty()
    //  همان نویسندهٔ تک‌رشته‌ای: نوشتن‌های پیشین پیش از این می‌روند و این یکی آخرین حالت را می‌نویسد
    return withContext(Dispatchers.IO) {
      runCatching {
        writer.submit(Callable {
          runCatching { DiskWrite.text(file, SaleDraftCodec.encode(SaleDraft.of(key, _lines.value, _debtorId.value, System.currentTimeMillis(), _coupons.value))) }.isSuccess
        }).get()
      }.getOrDefault(false)
    }
  }

  /**
   * کالاهایی که دیگر در فهرست نیستند از سبد بیرون می‌روند — وگرنه ردیفی
   * بی‌نام‌ونشان در سبد می‌ماند که نه قیمت دارد نه می‌شود حذفش کرد.
   */
  fun prune(d: ShopData) {
    //  سبدی که همین حالا فروش شده (کلیدش در دفتر هست) دوباره پیشنهاد نمی‌شود
    if (SaleDraftRules.posted(d, key)) { clear(); return }
    val known = d.products.mapTo(HashSet()) { it.id }
    //  قلمِ آزاد کالایی در فهرست ندارد و نباید قربانیِ همین پاک‌سازی شود
    val kept = _lines.value.filter { it.free || it.productId in known }
    if (kept.size != _lines.value.size) set(kept)
    if (_debtorId.value != null && d.debtors.none { it.id == _debtorId.value }) setDebtor(null)
  }

  companion object {
    const val FILE = "sale-draft.json"
    /** POS-12 — اسکنی که وسطِ جابه‌جاییِ فاکتور رسید و به سبد نرفت */
    const val NOT_ADDED = "فاکتور در حالِ جابه‌جایی بود — کالا اضافه نشد؛ دوباره اسکن کنید"
    // همان کلیدِ نسخهٔ وب — فقط برای آوردنِ پیش‌نویسِ نسخهٔ پیش
    private const val LEGACY_KEY = "tohid-shop-draft-cart-v1"
    private const val LEGACY_DEBTOR_KEY = "tohid-shop-draft-cart-debtor"

    private fun newKey(): String = ir.vil3ntec.tohid.sync.v1.Ulid.next()

    /*
     *  یک نویسنده برای همهٔ نمونه‌ها (`LedgerOwner` هم یکی می‌سازد) تا دو
     *  نوشتن هرگز روی یک فایلِ کناری نیفتند. هر بار آخرین حالت نوشته
     *  می‌شود؛ حالت‌های میانی که جا ماندند لازم نیستند.
     */
    private val pending = AtomicReference<Triple<File, SaleDraft, () -> Unit>?>(null)
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "sale-draft").apply { isDaemon = true } }

    private fun write(target: File, draft: SaleDraft, after: () -> Unit) {
      if (pending.getAndSet(Triple(target, draft, after)) != null) return
      writer.execute {
        val (f, d, then) = pending.getAndSet(null) ?: return@execute
        runCatching { DiskWrite.text(f, SaleDraftCodec.encode(d)) }.onSuccess { runCatching(then) }
      }
    }
  }
}
