package ir.vil3ntec.tohid.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 *  فاکتورهای نگه‌داشته — POS-12 (هسته).
 *
 *  مشتریِ «الف» منتظر است (پولش را می‌آورد، کالای دیگری می‌خواهد) و مشتریِ
 *  «ب» همین حالا پول می‌دهد: فاکتورِ «الف» با نام یا شماره نگه داشته می‌شود،
 *  صندوق برای «ب» خالی می‌شود و بعد «الف» با همهٔ ردیف‌ها، مشتری، تخفیف و
 *  یادداشتش برمی‌گردد. چند فاکتور هم‌زمان باز می‌مانند (حداکثر `MAX_OPEN`).
 *
 *  ── ساختار ───────────────────────────────────────────────────────────
 *  فاکتورِ **جاری** همان `CartStore` است و دست نخورده (POS-13/14 همان‌طور
 *  می‌مانند). فقط فاکتورهای **نگه‌داشته** این‌جا هستند، در فایلِ جدای
 *  `held-invoices.json` (`DiskWrite`: فایلِ کناری + `fsync` + جابه‌جاییِ اتمی).
 *  شناسهٔ هر فاکتورِ نگه‌داشته همان کلیدِ پیش‌نویس (`SaleDraft.key`) است؛ پس
 *  فروشی که پس از بازگشت ثبت شود همان شناسه را می‌گیرد و اگر فاکتوری به هر
 *  راهی فروش شده باشد (`prune`) دوباره پیشنهاد نمی‌شود.
 *
 *  ── قطعِ برق وسطِ جابه‌جایی ──────────────────────────────────────────
 *  دو فایل‌اند و هیچ‌کدام نباید چیزی را گم کند. ترتیبِ نوشتن طوری است که
 *  بدترین حالت «یک فاکتور در هر دو جا» باشد، نه «در هیچ‌جا»؛ و `reconcile`
 *  در بالا آمدنِ بعدی آن را یکی می‌کند: **سبدِ جاری می‌بَرد**، نسخهٔ هم‌کلیدِ
 *  نگه‌داشته کنار می‌رود.
 *
 *  ── فقط همین دستگاه ──────────────────────────────────────────────────
 *  فعلاً همگام نمی‌شود. بازیابی روی دستگاهِ دیگر و قفلِ «در دستِ دستگاهِ ۲»
 *  (S09) گامِ بعدی است و به جدول و opِ همگام‌سازی نیاز دارد؛ فیلدِ `device`
 *  برای همان است.
 */
@Serializable
data class HeldInvoice(
  /** = `SaleDraft.key` — شناسهٔ فروشی که از این فاکتور ثبت می‌شود (POS-14) */
  val id: String,
  /** شمارهٔ کوتاهِ روی برگه (۱ تا `MAX_OPEN`)؛ تا فاکتور باز است عوض نمی‌شود */
  val number: Int,
  /** نام یا شماره‌ای که فروشنده داد — «آقای کریمی» */
  val label: String = "",
  val lines: List<SaleDraft.Line> = emptyList(),
  val debtorId: String? = null,
  /** تخفیف همان‌طور که در کادر نوشته شده بود؛ صفحه خودش دوباره می‌خواندش */
  val discount: String = "",
  val note: String = "",
  val heldAt: Long = 0,
  /** دستگاهی که نگهش داشت (سریِ سند) — برای بازیابی روی دستگاهِ دیگر (S09) */
  val device: String = "",
  /** روشِ پرداختی که فروشنده برگزیده بود (`SalesEngine.Payment.name`)؛ خالی ⇒ صفحه از مشتری می‌سازد */
  val payment: String = "",
  /** «دریافتیِ همین حالا» همان‌طور که در کادر نوشته شده بود */
  val paid: String = "",
) {
  fun draft(): SaleDraft = SaleDraft(key = id, lines = lines, debtorId = debtorId, savedAt = heldAt)
}

/**
 *  حالتِ صفحه که با هر فاکتور جابه‌جا می‌شود ولی در `CartStore` نیست.
 *  `payment` `null` ⇒ فاکتورِ تازه یا نگه‌داشتهٔ نسخهٔ پیش: صفحه از مشتری می‌سازد (مشتری ⇒ نسیه).
 */
data class HeldExtras(val discount: String = "", val payment: SalesEngine.Payment? = null, val paid: String = "")

/** شماره، نام و یادداشتِ فاکتورِ **جاری** — تا با هر جابه‌جایی عوض نشوند */
@Serializable
data class HeldTag(val id: String, val number: Int, val label: String = "", val note: String = "")

@Serializable
data class HeldBook(val v: Int = 1, val items: List<HeldInvoice> = emptyList(), val current: HeldTag? = null)

object HeldInvoiceCodec {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
  fun encode(b: HeldBook): String = json.encodeToString(b)
  /** خالی ⇒ دفترِ خالی؛ خراب ⇒ `null` (صدازننده فایل را کنار می‌گذارد، پاک نمی‌کند) */
  fun decode(raw: String?): HeldBook? {
    if (raw.isNullOrBlank()) return HeldBook()
    return runCatching { json.decodeFromString(HeldBook.serializer(), raw) }.getOrNull()
  }
}

/** قواعدِ خالص — بی فایل و بی وضعیت؛ `HeldInvoiceStore` آن‌ها را می‌نویسد */
object HeldRules {
  /** حداکثرِ فاکتورهای باز با هم: نگه‌داشته‌ها + فاکتورِ جاری */
  const val MAX_OPEN = 9
  const val LABEL_MAX = 40
  const val NOTE_MAX = 200
  /** فاکتورِ نگه‌داشتهٔ کهنه‌تر از این به مدیر نشان داده می‌شود (S09) */
  const val STALE_HOURS = 24

  const val EMPTY = "سبد خالی است — چیزی برای نگه‌داشتن نیست"
  const val FULL = "حداکثر $MAX_OPEN فاکتورِ باز با هم. یکی را تمام کنید، باز کنید یا دور بیندازید."
  const val GONE = "این فاکتور دیگر نگه‌داشته نیست"
  const val NOTHING_LEFT = "کالاهای این فاکتور دیگر فروختنی نیستند (پاک یا غیرفعال شده‌اند). فاکتور نگه‌داشته ماند."

  sealed interface Result {
    /** `cart` = فاکتورِ تازهٔ صندوق (`null` = خالی)؛ `extras` = آنچه صفحه باید بگذارد */
    data class Ok(val book: HeldBook, val cart: SaleDraft?, val extras: HeldExtras, val number: Int, val dropped: Int = 0) : Result
    data class Refused(val message: String) : Result
  }

  /**
   *  روشِ پرداختِ صفحه پس از جابه‌جایی: همان که با فاکتور نگه داشته شد (نسیهٔ بی‌مشتری
   *  هم — ثبت خودش مشتری می‌خواهد)؛ نبود (فاکتورِ تازه، نسخهٔ پیش) ⇒ مشتری دارد ⇒ نسیه.
   */
  fun paymentAfterSwitch(saved: SalesEngine.Payment?, debtorId: String?): SalesEngine.Payment =
    saved ?: if (debtorId != null) SalesEngine.Payment.CREDIT else SalesEngine.Payment.CASH

  /**
   *  سبدی که صفحه کشید هنوز همان سبدِ `CartStore` است؟ برابریِ مقدار، نه نشانی:
   *  `StateFlow` مقدارِ برابر با نشانیِ تازه را به `collectAsState` نمی‌رساند و
   *  مقایسهٔ نشانی پس از X→Y→X′ ثبت را برای همیشه رد می‌کرد.
   */
  fun shownIsCurrent(shown: List<SalesEngine.CartLine>, now: List<SalesEngine.CartLine>): Boolean = shown == now

  fun clean(s: String?, max: Int): String = s.orEmpty().replace(Regex("\\s+"), " ").trim().take(max)

  private fun free(used: Collection<Int>): Int? = (1..MAX_OPEN).firstOrNull { it !in used }

  /** شمارهٔ فاکتورِ جاری: برچسبِ خودش، وگرنه کوچک‌ترین شمارهٔ آزاد. سبدِ خالی شماره ندارد. */
  fun activeNumber(book: HeldBook, active: SaleDraft): Int? {
    val key = active.key ?: return null
    if (active.lines.isEmpty()) return null
    book.current?.takeIf { it.id == key && book.items.none { i -> i.number == it.number } }?.let { return it.number }
    return free(book.items.filter { it.id != key }.map { it.number })
  }

  /** برچسبِ فاکتورِ جاری — فقط اگر واقعاً مالِ همین سبد باشد */
  fun activeTag(book: HeldBook, active: SaleDraft): HeldTag? = book.current?.takeIf { it.id == active.key && active.lines.isNotEmpty() }

  private fun park(book: HeldBook, active: SaleDraft, extras: HeldExtras, label: String?, note: String?, now: Long, device: String, number: Int) =
    HeldInvoice(
      id = active.key!!,
      number = number,
      label = clean(label ?: activeTag(book, active)?.label, LABEL_MAX),
      lines = active.lines,
      debtorId = active.debtorId,
      discount = extras.discount.trim(),
      note = clean(note ?: activeTag(book, active)?.note, NOTE_MAX),
      heldAt = now,
      device = device,
      payment = extras.payment?.name.orEmpty(),
      paid = extras.paid.trim(),
    )

  private fun extrasOf(h: HeldInvoice) =
    HeldExtras(h.discount, SalesEngine.Payment.values().firstOrNull { it.name == h.payment }, h.paid)

  /** F6 — فاکتورِ جاری نگه داشته می‌شود و صندوق خالی. `label`/`note` `null` ⇒ همان قبلی. */
  fun hold(book: HeldBook, active: SaleDraft, extras: HeldExtras, label: String?, note: String?, now: Long, device: String): Result {
    val key = active.key
    if (key == null || active.lines.isEmpty()) return Result.Refused(EMPTY)
    val others = book.items.filter { it.id != key }
    //  نگه‌داشته‌ها + همین یکی + سبدِ تازه‌ای که پس از آن پر می‌شود
    if (others.size >= MAX_OPEN - 1) return Result.Refused(FULL)
    val number = activeNumber(book.copy(items = others), active) ?: return Result.Refused(FULL)
    val item = park(book, active, extras, label, note, now, device, number)
    return Result.Ok(HeldBook(items = (others + item).sortedBy { it.number }, current = null), null, HeldExtras(), number)
  }

  /**
   *  ردیف‌هایی از فاکتورِ نگه‌داشته که هنوز فروختنی‌اند — همان قاعدهٔ
   *  `SaleDraftRules.decide`: کالای پاک‌شده یا غیرفعال (PRD-18) می‌افتد، قلمِ
   *  آزاد می‌ماند، مشتریِ پاک‌شده جدا می‌شود. فقط هنگامِ بازگرداندن؛ فهرستِ
   *  نگه‌داشته‌ها خودش بی‌صدا کوتاه نمی‌شود.
   */
  fun fit(h: HeldInvoice, d: ShopData): HeldInvoice {
    val byId = d.products.associateBy { it.id }
    return h.copy(
      lines = h.lines.filter { it.label.isNotBlank() || SaleDraftRules.sellable(byId[it.productId]) },
      debtorId = h.debtorId?.takeIf { id -> d.debtors.any { it.id == id } },
    )
  }

  /**
   *  بازگرداندنِ فاکتورِ `id` به صندوق. فاکتورِ جاری اگر خالی نیست خودکار
   *  نگه داشته می‌شود (با همان شماره و نامی که داشت) — هیچ‌چیز دور ریخته نمی‌شود.
   *  `d` داده شد ⇒ ردیف‌ها با `fit` پالوده می‌شوند؛ چیزی نماند ⇒ رد و فاکتور سرِ جایش.
   */
  fun resume(book: HeldBook, id: String, active: SaleDraft, extras: HeldExtras, now: Long, device: String, d: ShopData? = null): Result {
    val held = book.items.firstOrNull { it.id == id } ?: return Result.Refused(GONE)
    val target = if (d == null) held else fit(held, d)
    if (target.lines.isEmpty()) return Result.Refused(NOTHING_LEFT)
    var rest = book.items.filter { it.id != id }
    if (active.key != null && active.lines.isNotEmpty() && active.key != id) {
      //  شماره‌ای که روی برگه‌اش دیده می‌شد، پیش از بیرون رفتنِ `target`
      val number = activeNumber(book, active) ?: return Result.Refused(FULL)
      rest = rest.filter { it.id != active.key } + park(book, active, extras, null, null, now, device, number)
    }
    val tag = HeldTag(target.id, target.number, target.label, target.note)
    return Result.Ok(HeldBook(items = rest.sortedBy { it.number }, current = tag), target.draft(), extrasOf(target), target.number, held.lines.size - target.lines.size)
  }

  /** Ctrl+PgDn / Ctrl+PgUp — فاکتورِ باز بعدی/قبلی به ترتیبِ شماره؛ `null` = جای رفتن نیست */
  fun neighbour(book: HeldBook, active: SaleDraft, dir: Int): String? {
    if (book.items.isEmpty()) return null
    val held = book.items.sortedBy { it.number }
    val mine = activeNumber(book, active) ?: return if (dir >= 0) held.first().id else held.last().id
    return if (dir >= 0) (held.firstOrNull { it.number > mine } ?: held.first()).id
    else (held.lastOrNull { it.number < mine } ?: held.last()).id
  }

  fun discard(book: HeldBook, id: String): HeldBook = book.copy(items = book.items.filter { it.id != id })

  fun rename(book: HeldBook, id: String, label: String, note: String): HeldBook =
    book.copy(items = book.items.map { if (it.id == id) it.copy(label = clean(label, LABEL_MAX), note = clean(note, NOTE_MAX)) else it })

  /**
   *  پس از هر تغییرِ دفتر: فاکتوری که فروش شده (کلیدش در دفتر هست — پایانِ
   *  فروش، یا ثبتِ دوباره پس از قطعِ برق) بیرون می‌رود. ردیف‌ها این‌جا دست
   *  نمی‌خورند (`fit` هنگامِ بازگرداندن)، تا دادهٔ ناقص یا دکانِ دیگری که
   *  روی همین دستگاه باز شد فاکتوری را بی‌صدا نخورد.
   */
  fun prune(book: HeldBook, d: ShopData): HeldBook {
    val items = book.items.filterNot { SaleDraftRules.posted(d, it.id) }
    val current = book.current?.takeUnless { SaleDraftRules.posted(d, it.id) }
    return if (items == book.items && current == book.current) book else book.copy(items = items, current = current)
  }

  /**
   *  بالا آمدن پس از بسته شدن یا قطعِ برق: فاکتوری که هم در سبد است هم در
   *  نگه‌داشته‌ها (نوشتنِ دومِ یک جابه‌جایی نرسید) فقط در سبد می‌ماند،
   *  با همان شماره و نام.
   */
  fun reconcile(book: HeldBook, active: SaleDraft): HeldBook {
    val key = active.key ?: return book
    if (active.lines.isEmpty()) return book
    val twin = book.items.firstOrNull { it.id == key } ?: return book
    val tag = book.current?.takeIf { it.id == key } ?: HeldTag(twin.id, twin.number, twin.label, twin.note)
    return HeldBook(items = book.items.filter { it.id != key }, current = tag)
  }

  fun stale(book: HeldBook, now: Long, hours: Int = STALE_HOURS): List<HeldInvoice> =
    book.items.filter { now - it.heldAt >= hours * 3_600_000L }

  fun dropStale(book: HeldBook, now: Long, hours: Int = STALE_HOURS): HeldBook {
    val old = stale(book, now, hours).mapTo(HashSet()) { it.id }
    return book.copy(items = book.items.filter { it.id !in old })
  }
}

/** فایلِ دفترِ نگه‌داشته‌ها. `write` باید پس از بازگشت روی دیسک باشد و شکست را پرتاب کند. */
interface HeldDisk {
  fun read(): String?
  @Throws(IOException::class) fun write(text: String)
  /** فایلِ خراب کنار گذاشته می‌شود تا دوباره رویش نوشته نشود */
  fun quarantine() {}
}

/** سبدِ جاری از نگاهِ نگه‌داشته‌ها — `CartStore` */
interface HeldCart {
  /** حالتِ کنونی؛ سبدِ ناخالی همیشه کلید دارد */
  fun snapshot(): SaleDraft
  /** سبد را عوض می‌کند و تا رسیدن به دیسک صبر می‌کند; `false` = نوشته نشد */
  suspend fun replace(draft: SaleDraft?): Boolean
  /** «ثبتِ فروش» در جریان است ⇒ جابه‌جایی نه */
  val busy: Boolean
  /**
   *  قفلِ سبد در طولِ یک جابه‌جایی: `true` ⇒ افزودن، ویرایش، پاک کردن و ثبتِ
   *  فروش رد می‌شوند تا اسکن یا F12ِ وسطِ جابه‌جایی به فاکتورِ نیمه‌جابه‌جاشده نرسد.
   */
  fun freeze(on: Boolean)
  /** پرسشِ بازیابیِ پیش‌نویس هنوز پاسخ نگرفته (`DraftRecoveryPrompt`) */
  val recovering: Boolean get() = false
}

/**
 *  نگه‌داشته‌های همین دستگاه با نوشتنِ همگام. همهٔ کارها پشتِ یک قفل‌اند
 *  تا دو ضربهٔ پشتِ‌سرِهم دو جابه‌جاییِ درهم نسازند. خواندن و نوشتنِ فایل روی
 *  `io` است، نه رشتهٔ صفحه؛ فایل با نخستین کار (`load`) خوانده می‌شود، نه هنگامِ ساختن.
 */
class HeldInvoiceStore(
  private val disk: HeldDisk,
  private val device: () -> String = { "" },
  private val clock: () -> Long = { System.currentTimeMillis() },
  private val io: CoroutineDispatcher = Dispatchers.IO,
) {
  private val lock = Mutex()
  private val _book = MutableStateFlow(HeldBook())
  val book: StateFlow<HeldBook> = _book.asStateFlow()
  @Volatile private var loaded = false
  private var deviceId = ""

  sealed interface Outcome {
    /** `extras` ناتهی ⇒ صفحه آن‌ها را برای فاکتورِ تازهٔ صندوق بگذارد */
    data class Done(val message: String, val extras: HeldExtras? = null) : Outcome
    data class Refused(val message: String) : Outcome
  }

  /** یک بار خواندنِ فایل (و سریِ دستگاه)؛ بارِ دوم کاری نمی‌کند */
  suspend fun load() = lock.withLock { ensureLoaded() }

  private suspend fun ensureLoaded() {
    if (loaded) return
    val (b, dev) = withContext(io) { read() to runCatching(device).getOrDefault("") }
    _book.value = b
    deviceId = dev
    loaded = true
  }

  private fun read(): HeldBook {
    val raw = runCatching { disk.read() }.getOrElse { return HeldBook() }
    return HeldInvoiceCodec.decode(raw) ?: run { runCatching { disk.quarantine() }; HeldBook() }
  }

  private suspend fun write(next: HeldBook): Boolean =
    withContext(io) { runCatching { disk.write(HeldInvoiceCodec.encode(next)) }.isSuccess }

  private suspend fun commit(next: HeldBook): Boolean = write(next).also { if (it) _book.value = next }

  private fun title(number: Int, label: String) = if (label.isBlank()) "فاکتورِ #$number" else "فاکتورِ #$number «$label»"

  /**
   *  هستهٔ هر جابه‌جایی: سبد تا پایان قفل است و لغوِ صفحه (رفتن به صفحهٔ دیگر،
   *  چرخشِ گوشی) آن را نیمه‌کاره نمی‌گذارد — نیمه‌کاره یعنی دفترِ حافظه عقب‌تر از
   *  دیسک و نوشتنِ بعدی فاکتوری را جا می‌انداخت.
   */
  private suspend fun <T> switching(cart: HeldCart, block: suspend () -> T): T = withContext(NonCancellable) {
    cart.freeze(true)
    try { block() } finally { cart.freeze(false) }
  }

  suspend fun hold(cart: HeldCart, extras: HeldExtras, label: String? = null, note: String? = null): Outcome = lock.withLock {
    ensureLoaded()
    if (cart.busy) return Outcome.Refused(BUSY)
    switching(cart) {
      when (val r = HeldRules.hold(_book.value, cart.snapshot(), extras, label, note, clock(), deviceId)) {
        is HeldRules.Result.Refused -> Outcome.Refused(r.message)
        is HeldRules.Result.Ok -> {
          //  ۱) اول در دفترِ نگه‌داشته‌ها ۲) بعد سبد خالی. برق میانِ این دو ⇒ فاکتور در سبد می‌ماند (`reconcile`)
          if (!commit(r.book)) return@switching Outcome.Refused(NOT_SAVED)
          cart.replace(null)
          val h = r.book.items.first { it.number == r.number }
          Outcome.Done("${title(h.number, h.label)} نگه داشته شد", HeldExtras())
        }
      }
    }
  }

  suspend fun resume(cart: HeldCart, id: String, extras: HeldExtras, d: ShopData? = null): Outcome = lock.withLock {
    ensureLoaded()
    if (cart.busy) return Outcome.Refused(BUSY)
    switching(cart) {
      val before = _book.value
      val active = cart.snapshot()
      val r = HeldRules.resume(before, id, active, extras, clock(), deviceId, d)
      if (r is HeldRules.Result.Refused) return@switching Outcome.Refused(r.message)
      r as HeldRules.Result.Ok
      val target = before.items.first { it.id == id }
      //  ۱) فاکتورِ جاری به دفتر — هدف هنوز همان‌جاست (برق ⇒ تکراری، نه گم)
      val staged = r.book.copy(items = (r.book.items + target).sortedBy { it.number })
      if (!write(staged)) return@switching Outcome.Refused(NOT_SAVED)
      //  حافظه همان دیسک: هر نوشتنِ بعدی فاکتورِ جاریِ نگه‌داشته را با خود دارد
      _book.value = staged
      //  ۲) هدف به سبد، تا روی دیسک
      if (!cart.replace(r.cart)) {
        cart.replace(active.takeIf { it.lines.isNotEmpty() })
        //  نرسید ⇒ دیسک همان `staged` می‌ماند (هم‌کلیدِ سبد؛ `reconcile` یکی‌اش می‌کند)
        if (write(before)) _book.value = before
        return@switching Outcome.Refused(NOT_SAVED)
      }
      //  ۳) هدف از دفتر بیرون. نرسید ⇒ `reconcile` در بالا آمدنِ بعدی
      write(r.book)
      _book.value = r.book
      val dropped = if (r.dropped > 0) " — ${r.dropped} ردیف که کالایش دیگر فروختنی نیست کنار رفت" else ""
      Outcome.Done("${title(target.number, target.label)} باز شد$dropped", r.extras)
    }
  }

  /** فاکتورِ باز بعدی (`dir` > ۰) یا قبلی */
  suspend fun step(cart: HeldCart, dir: Int, extras: HeldExtras, d: ShopData? = null): Outcome {
    load()
    val id = HeldRules.neighbour(_book.value, cart.snapshot(), dir) ?: return Outcome.Refused("فاکتورِ نگه‌داشته‌ای نیست")
    return resume(cart, id, extras, d)
  }

  /** دور انداختن — صفحه پیش از آن تأیید می‌گیرد */
  suspend fun discard(id: String): Outcome = lock.withLock {
    ensureLoaded()
    val h = _book.value.items.firstOrNull { it.id == id } ?: return Outcome.Refused(HeldRules.GONE)
    if (!commit(HeldRules.discard(_book.value, id))) return Outcome.Refused(NOT_SAVED)
    Outcome.Done("${title(h.number, h.label)} دور انداخته شد")
  }

  suspend fun rename(id: String, label: String, note: String): Outcome = lock.withLock {
    ensureLoaded()
    if (_book.value.items.none { it.id == id }) return Outcome.Refused(HeldRules.GONE)
    if (!commit(HeldRules.rename(_book.value, id, label, note))) return Outcome.Refused(NOT_SAVED)
    Outcome.Done("ذخیره شد")
  }

  /** پاک کردنِ نگه‌داشته‌های کهنه — صفحه مجوز و تأیید را می‌گیرد */
  suspend fun dropStale(hours: Int = HeldRules.STALE_HOURS): Outcome = lock.withLock {
    ensureLoaded()
    val n = HeldRules.stale(_book.value, clock(), hours).size
    if (n == 0) return Outcome.Refused("فاکتورِ کهنه‌ای نیست")
    if (!commit(HeldRules.dropStale(_book.value, clock(), hours))) return Outcome.Refused(NOT_SAVED)
    Outcome.Done("$n فاکتورِ کهنه پاک شد")
  }

  /** پس از تغییرِ دفتر (فروش، حذفِ کالا یا مشتری) */
  suspend fun prune(d: ShopData) = lock.withLock {
    ensureLoaded()
    val next = HeldRules.prune(_book.value, d)
    if (next != _book.value) { write(next); _book.value = next }
  }

  /**
   *  بالا آمدنِ برنامه — پیش از هر کارِ دیگر، و دوباره پس از پاسخِ پرسشِ
   *  بازیابی. تا آن پاسخ هم‌کلیدِ نگه‌داشته دست نمی‌خورد: «دور انداختنِ»
   *  پیش‌نویس نباید فاکتوری را ببرد که در فهرستِ نگه‌داشته‌ها هم هست.
   */
  suspend fun reconcile(cart: HeldCart) = lock.withLock {
    ensureLoaded()
    if (cart.recovering) return@withLock
    val next = HeldRules.reconcile(_book.value, cart.snapshot())
    if (next != _book.value) { write(next); _book.value = next }
  }

  companion object {
    const val FILE = "held-invoices.json"
    const val BUSY = "فروش در حالِ ثبت است — لحظه‌ای بعد"
    const val NOT_SAVED = "روی دستگاه نوشته نشد — چیزی جابه‌جا نشد؛ دوباره بزنید."
  }
}
