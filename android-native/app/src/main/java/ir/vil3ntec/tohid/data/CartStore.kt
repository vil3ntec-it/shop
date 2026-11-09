package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.i18n.StrLedger
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
  //  OPS-07: پیش‌نویسِ سبدِ حالتِ آموزشی در `files/training` — نمونه‌ای که در آموزش ساخته شد هرگز سبدِ واقعی را نمی‌بیند
  private val file = File(TrainingLedger.filesDir(context), FILE)
  private val training = TrainingMode.on
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

  /** POS-11 — یادداشتِ فاکتورِ همین سبد؛ مثلِ کدها با پیش‌نویس و فاکتورِ نگه‌داشته می‌رود و برمی‌گردد */
  private val _note = MutableStateFlow(initial?.takeIf { it.lines.isNotEmpty() }?.note)
  val note: StateFlow<String?> = _note.asStateFlow()

  /**
   *  POS-18 — لیستِ قیمتِ **دستیِ** همین فاکتور (`retail`/`wholesale`، `CartPriceList`): با پیش‌نویس و فاکتورِ نگه‌داشته
   *  می‌رود و برمی‌گردد تا فاکتورِ عمدهٔ دستی پس از بازگشت دوباره خرده قیمت نخورد. `null` ⇒ لیستِ مشتری یا خرده.
   */
  private val _priceList = MutableStateFlow(initial?.takeIf { it.lines.isNotEmpty() }?.priceList)
  val priceList: StateFlow<String?> = _priceList.asStateFlow()

  /**
   *  POS-13 — تخفیف، روشِ پرداخت، دریافتی و فروشندهٔ همین فاکتور (`SaleDraft.page`): صفحه می‌نویسد ([setPage]) و با پیش‌نویس
   *  و فاکتورِ نگه‌داشته برمی‌گردد؛ بی آن بسته شدنِ برنامه فاکتور را بی تخفیفش برمی‌گرداند. سبدِ خالی ⇒ `null`.
   */
  private val _page = MutableStateFlow(initial?.takeIf { it.lines.isNotEmpty() }?.page)
  val page: StateFlow<SaleDraft.Page?> = _page.asStateFlow()

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
    //  پیش‌نویسِ نسخهٔ پیش (SharedPreferences، همان شکلِ وب) — مالِ دفترِ واقعی، پس آموزش نه می‌خواندش نه پاکش می‌کند
    if (training) return null
    val legacy = SaleDraftCodec.decode(prefs.getString(LEGACY_KEY, null)) ?: return null
    return legacy.copy(debtorId = prefs.getString(LEGACY_DEBTOR_KEY, null))
  }

  private fun persist() {
    val draft = SaleDraft.of(key, _lines.value, _debtorId.value, System.currentTimeMillis(), _coupons.value, _note.value, _priceList.value, _page.value)
    write(file, draft) {
      //  پیش‌نویس به فایل رسید ⇒ نسخهٔ کهنه دیگر لازم نیست و نباید روزی دوباره زنده شود
      if (!training && (prefs.contains(LEGACY_KEY) || prefs.contains(LEGACY_DEBTOR_KEY))) {
        prefs.edit().remove(LEGACY_KEY).remove(LEGACY_DEBTOR_KEY).apply()
      }
    }
  }

  /** POS-12: جابه‌جاییِ فاکتور در جریان است ⇒ سبد دست نمی‌خورد (`HeldCart.freeze`) */
  @Volatile private var frozen = false

  /**
   *  RPT-09 — ردیف‌هایی که کاربر از همین فاکتورِ باز حذف کرد، با کلیدِ همان فاکتور: [set] بی آن ردیف
   *  (Delete، منفی تا صفر، حذف در پنجرهٔ ردیف) یا [clear] (خالی کردنِ فاکتور). فقط کارِ کاربر:
   *  ثبتِ فروش ([post])، جابه‌جاییِ فاکتورِ نگه‌داشته ([replace])، دور انداختنِ پیش‌نویسِ بازیابی و
   *  [prune] ِ کالای پاک‌شده صدا نمی‌زنند. `null` (پیش‌فرض) ⇒ هیچ؛ `FraudTrailPref.attach` می‌گذاردش
   *  و تنظیمِ خاموش را خودش می‌سنجد. خطای آن هرگز سبد را نمی‌شکند.
   */
  @Volatile var onLinesRemoved: ((lines: List<SalesEngine.CartLine>, draftKey: String?) -> Unit)? = null

  private fun removed(before: List<SalesEngine.CartLine>, after: List<SalesEngine.CartLine>, draftKey: String?) {
    val hook = onLinesRemoved ?: return
    val gone = FraudTrail.removed(before, after).filterNot(AutoFeeLines::isFee) // POS-36: هزینهٔ خودکار ردِ حذف نیست
    if (gone.isNotEmpty()) runCatching { hook(gone, draftKey) }
  }

  /** `false` ⇒ وسطِ جابه‌جاییِ فاکتور بود و چیزی عوض نشد — صفحه باید بگوید (`NOT_ADDED`) */
  fun set(lines: List<SalesEngine.CartLine>): Boolean = put(lines, byUser = true)

  private fun put(input: List<SalesEngine.CartLine>, byUser: Boolean): Boolean {
    if (frozen) return false
    val lines = AutoFeeLines.apply(input, AutoFeesPref.rules(app)) // POS-36؛ بی قاعده همان فهرست
    val before = _lines.value
    val draftKey = key
    //  سبدِ خالی کلید ندارد؛ نخستین قلم کلیدِ تازه می‌سازد
    key = if (lines.isEmpty()) null else key ?: newKey()
    _lines.value = lines
    if (lines.isEmpty()) { _coupons.value = emptyList(); _note.value = null; _priceList.value = null; _page.value = null }
    persist()
    if (byUser) removed(before, lines, draftKey)
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

  /** POS-11 — `false` ⇒ وسطِ جابه‌جاییِ فاکتور بود؛ متن از پیش `SaleNotes.clean` شده است */
  fun setNote(text: String?): Boolean {
    if (frozen) return false
    //  یادداشتِ فاکتور بی سبد معنا ندارد (مثلِ کدها)
    val next = if (_lines.value.isEmpty()) null else text
    if (next != _note.value) {
      _note.value = next
      persist()
    }
    return true
  }

  /**
   *  POS-18 — صندوق‌دار لیستِ همین فاکتور را دستی عوض کرد (`PriceListAtPos`). `false` ⇒ وسطِ جابه‌جاییِ فاکتور بود.
   *  سبدِ خالی ⇒ فقط در حافظه (نخستین قلم با همین لیست نوشته می‌شود)؛ نه هیچ نوشتنی.
   */
  fun setPriceList(list: String?): Boolean {
    if (frozen) return false
    if (list != _priceList.value) {
      _priceList.value = list
      if (_lines.value.isNotEmpty()) persist()
    }
    return true
  }

  /**
   *  POS-13 — صفحه تخفیف، روشِ پرداخت یا دریافتیِ همین فاکتور را عوض کرد (یا «اصلاحِ فاکتور» فروشنده‌اش را آورد).
   *  `false` ⇒ وسطِ جابه‌جاییِ فاکتور، یا پرسشِ بازیابی هنوز پاسخ نگرفته: صفحه‌ای که تازه با کادرهای خالی باز شد نباید
   *  تخفیفِ پیش‌نویسِ بازیابی‌نشده را پاک کند. سبدِ خالی ⇒ چیزی نگه داشته نمی‌شود (مثلِ یادداشت).
   */
  fun setPage(x: HeldExtras): Boolean {
    if (frozen || _recovered.value != null) return false
    val next = if (_lines.value.isEmpty()) null else SaleDraft.Page.of(x)
    if (next != _page.value) {
      _page.value = next
      persist()
    }
    return true
  }

  /** S1 — حساب عوض شد: کدهای حسابِ قبلی فقط از حافظه می‌روند (فایل را `LedgerOwner` خالی کرده) */
  fun forgetCoupons() {
    _coupons.value = emptyList()
  }

  fun clear(): Boolean = drop(byUser = true)

  private fun drop(byUser: Boolean): Boolean {
    if (frozen) return false
    val before = _lines.value
    val draftKey = key
    wipe()
    if (byUser) removed(before, emptyList(), draftKey)
    return true
  }

  private fun wipe() {
    key = null
    _lines.value = emptyList()
    _debtorId.value = null
    _coupons.value = emptyList()
    _note.value = null
    _priceList.value = null
    _page.value = null
    persist()
  }

  /** کلیدِ یکتای فاکتورِ همین سبد — شناسهٔ فروشی که از آن ثبت می‌شود */
  fun saleKey(): String = key ?: newKey().also { key = it }

  /**
   *  ثبتِ همین سبد (POS-14): یک ثبت در هر لحظه، با کلیدِ فاکتور؛ سبد فقط
   *  وقتی پاک می‌شود که `save` بگوید دفتر به دیسک رسید.
   */
  /**
   *  @param base دفتری که [record] فاکتور را روی آن می‌سازد (همان `d` ِ صفحه) — پایهٔ صریحِ `ShopStore.save`: فروشی که
   *         از صفحهٔ کهنه ثبت شد ردیف‌های تازه‌ترِ دفتر (فروشِ پیشینِ همین صفحه، Pull ِ کارگر) را نمی‌برد
   *  @param gate پیش از ساختنِ فاکتور با کلیدِ همین سبد (PRM-02، `PromoLive.redeemGate`)؛ پیام ⇒ ثبت نه.
   */
  suspend fun post(
    store: ShopStore,
    base: ShopData,
    gate: (suspend (key: String) -> String?)? = null,
    record: (key: String) -> SalesEngine.Result,
  ): SalePost.Outcome {
    //  ضربه‌ای که پس از پایانِ ثبتِ قبلی رسید سبدِ کهنهٔ صفحه را در دست دارد، نه سبدِ تازه را
    //  POS-12: F9/F12ِ وسطِ جابه‌جاییِ فاکتور ثبت نمی‌شود (سبد نیمه‌جابه‌جا است)
    if (frozen || _lines.value.isEmpty()) return SalePost.Outcome.Busy
    val cartKey = saleKey()
    val out = poster.run(cartKey, record, { store.save(base, it) }, gate, offMain = StockLedger.isOn(store.data.value)) { done -> if (key == done) wipe() }
    //  فروش ثبت شده و روی دیسک است؛ چاپ پس از آن و جدا از آن (S16: چاپگرِ خاموش فروش را نگه نمی‌دارد)
    if (out is SalePost.Outcome.Done) {
      runCatching { ir.vil3ntec.tohid.print.PrintSpooler.afterSale(app, out.ok.data, out.ok.saleId) }
      //  «اصلاح و ثبت» ِ صندوقِ سندهای ردشده: همین سبد برای ردیفی باز شده بود ⇒ آن ردیف با همین فاکتور بسته می‌شود
      if (!training) runCatching { ConflictFix.posted(app, cartKey, out.ok.saleId) }
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
    _note.value = keep.note
    _priceList.value = keep.priceList
    _page.value = keep.page
    persist()
  }

  /* ── POS-12: فاکتورهای نگه‌داشته (`HeldInvoiceStore`) ─────────────────── */

  override val busy: Boolean get() = posting.value

  override fun freeze(on: Boolean) { frozen = on }

  override val recovering: Boolean get() = _recovered.value != null

  override fun snapshot(): SaleDraft =
    SaleDraft.of(if (_lines.value.isEmpty()) null else saleKey(), _lines.value, _debtorId.value, System.currentTimeMillis(), _coupons.value, _note.value, _priceList.value, _page.value)

  /**
   *  سبد را با فاکتورِ دیگری عوض می‌کند (`null` = سبدِ خالی) و تا رسیدنش به
   *  دیسک صبر می‌کند — جابه‌جاییِ فاکتورها بی این صبر می‌توانست میانِ دو فایل
   *  فاکتوری را گم کند.
   */
  override suspend fun replace(draft: SaleDraft?): Boolean {
    val lines = draft?.cart().orEmpty()
    key = if (lines.isEmpty()) null else draft?.key ?: newKey()
    //  پیش از ردیف‌ها: صندوق سبدِ تازه را با لیستِ همان فاکتور قیمت می‌گذارد، نه با لیستِ فاکتورِ پیشین
    _priceList.value = if (lines.isEmpty()) null else draft?.priceList
    _lines.value = lines
    _debtorId.value = draft?.debtorId
    //  N3: کدهای تخفیف مالِ همان فاکتورند — با آن می‌روند و برمی‌گردند، به فاکتورِ دیگر نمی‌رسند
    _coupons.value = if (lines.isEmpty()) emptyList() else draft?.coupons.orEmpty()
    _note.value = if (lines.isEmpty()) null else draft?.note
    //  POS-13: صفحهٔ همان فاکتور (برگشتِ همین سبد پس از جابه‌جاییِ ناموفق)؛ نگه‌داشته صفحه ندارد ⇒ صفحه پس از جابه‌جایی می‌نویسد
    _page.value = if (lines.isEmpty()) null else draft?.page
    //  همان نویسندهٔ تک‌رشته‌ای: نوشتن‌های پیشین پیش از این می‌روند و این یکی آخرین حالت را می‌نویسد
    return withContext(Dispatchers.IO) {
      runCatching {
        writer.submit(Callable {
          runCatching { DiskWrite.text(file, SaleDraftCodec.encode(SaleDraft.of(key, _lines.value, _debtorId.value, System.currentTimeMillis(), _coupons.value, _note.value, _priceList.value, _page.value))) }.isSuccess
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
    //  RPT-09: هیچ‌کدام کارِ کاربر نیست ⇒ «حذفِ ردیف» شمرده نمی‌شود
    if (SaleDraftRules.posted(d, key)) { drop(byUser = false); return }
    val known = d.products.mapTo(HashSet()) { it.id }
    //  قلمِ آزاد کالایی در فهرست ندارد و نباید قربانیِ همین پاک‌سازی شود
    val kept = _lines.value.filter { it.free || it.productId in known }
    if (kept.size != _lines.value.size) put(kept, byUser = false)
    if (_debtorId.value != null && d.debtors.none { it.id == _debtorId.value }) setDebtor(null)
  }

  companion object {
    const val FILE = "sale-draft.json"
    /** POS-12 — اسکنی که وسطِ جابه‌جاییِ فاکتور رسید و به سبد نرفت */
    val NOT_ADDED: String get() = StrLedger.cartNotAdded
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
