package ir.vil3ntec.tohid.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
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
class CartStore(context: Context) {

  private val prefs = context.getSharedPreferences("tohid", Context.MODE_PRIVATE)
  private val file = File(context.filesDir, FILE)

  private val initial: SaleDraft? = load()

  @Volatile private var key: String? = initial?.key ?: initial?.takeIf { it.lines.isNotEmpty() }?.let { newKey() }

  private val _lines = MutableStateFlow(initial?.cart().orEmpty())
  val lines: StateFlow<List<SalesEngine.CartLine>> = _lines.asStateFlow()

  /** قرض‌داری که سبد به حسابش می‌رود — میان‌بری برای فروشِ نسیه */
  private val _debtorId = MutableStateFlow(initial?.debtorId)
  val debtorId: StateFlow<String?> = _debtorId.asStateFlow()

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
    val draft = SaleDraft.of(key, _lines.value, _debtorId.value, System.currentTimeMillis())
    write(file, draft) {
      //  پیش‌نویس به فایل رسید ⇒ نسخهٔ کهنه دیگر لازم نیست و نباید روزی دوباره زنده شود
      if (prefs.contains(LEGACY_KEY) || prefs.contains(LEGACY_DEBTOR_KEY)) {
        prefs.edit().remove(LEGACY_KEY).remove(LEGACY_DEBTOR_KEY).apply()
      }
    }
  }

  fun set(lines: List<SalesEngine.CartLine>) {
    //  سبدِ خالی کلید ندارد؛ نخستین قلم کلیدِ تازه می‌سازد
    key = if (lines.isEmpty()) null else key ?: newKey()
    _lines.value = lines
    persist()
  }

  fun setDebtor(id: String?) {
    _debtorId.value = id
    persist()
  }

  fun clear() {
    key = null
    _lines.value = emptyList()
    _debtorId.value = null
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
    if (_lines.value.isEmpty()) return SalePost.Outcome.Busy
    return poster.run(saleKey(), record, { store.save(it) }) { done -> if (key == done) clear() }
  }

  /** پاسخِ پرسشِ بازیابی: `keep` = ادامه با همین ردیف‌ها؛ `null` = دور انداختن */
  fun resolveRecovery(keep: SaleDraft?) {
    _recovered.value = null
    if (keep == null) { clear(); return }
    key = keep.key ?: key
    _lines.value = keep.cart()
    _debtorId.value = keep.debtorId
    persist()
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
