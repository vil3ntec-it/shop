package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrLedger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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
 *  ── پشتیبانِ سرور (S09) ──────────────────────────────────────────────
 *  پشتِ پرچمِ خاموشِ `shop.held_sync` هر تغییرِ دفتر به سرور می‌رود و دستگاهِ
 *  تازه یا دیگرِ دکان همه را پس می‌گیرد (`HeldSync`، `onChange`، `absorb`).
 *  خاموش ⇒ همین دستگاه، بایت‌به‌بایت همان پیشین. روشن ⇒ بازگرداندن پیش از آن از سرور می‌پرسد («در دستِ دستگاهِ X»،
 *  ادعای نرم با تأییدِ صریح برای گرفتن؛ `HeldSyncEngine.claim`) و دستگاه‌های دیگر با دورِ بعد می‌بینند.
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
  /** کدهای تخفیفِ زده‌شده (`SaleDraft.coupons`) — با فاکتور برمی‌گردند؛ نگه‌داشتهٔ نسخهٔ پیش ندارد */
  val coupons: List<String> = emptyList(),
  /**
   *  POS-20: `discount` مبلغ (افغانی) است، نه درصد — فقط تخفیفِ کلِ فاکتورِ کامپیوتر به مبلغ
   *  (پشتِ `LinePolicyPrefs.on`). ⛔ `false` نوشته نمی‌شود (`EncodeDefault.NEVER`)، پس فایلِ
   *  نگه‌داشته‌های درصدی بایت‌به‌بایت همان امروز است و نگه‌داشتهٔ نسخهٔ پیش ⇒ درصد.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val discountAmount: Boolean = false,
  /**
   *  POS-11 — یادداشتِ **فاکتور** (`SaleDraft.note`، روی رسید)، جدا از [note] ِ برگه. پیش‌تر با نگه‌داشتن گم می‌شد.
   *  `null` نوشته نمی‌شود ⇒ نگه‌داشتهٔ بی یادداشتِ فاکتور بایت‌به‌بایت همان پیشین.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val saleNote: String? = null,
  /** POS-18 — لیستِ قیمتِ دستیِ فاکتور (`SaleDraft.priceList`)؛ `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val priceList: String? = null,
) {
  fun draft(): SaleDraft =
    SaleDraft(key = id, lines = lines, debtorId = debtorId, savedAt = heldAt, coupons = coupons, note = saleNote, priceList = priceList)
}

/**
 *  حالتِ صفحه که با هر فاکتور جابه‌جا می‌شود ولی در `CartStore` نیست.
 *  `payment` `null` ⇒ فاکتورِ تازه یا نگه‌داشتهٔ نسخهٔ پیش: صفحه از مشتری می‌سازد (مشتری ⇒ نسیه).
 */
data class HeldExtras(
  val discount: String = "",
  val payment: SalesEngine.Payment? = null,
  val paid: String = "",
  /** POS-20: `discount` به مبلغ (افغانی) است، نه درصد — بی آن عددِ مبلغ پس از جابه‌جایی درصد خوانده می‌شد */
  val discountAmount: Boolean = false,
  /**
   *  فروشندهٔ **فاکتورِ** اصلاح‌شده (`Sale.seller`، `SaleReopen.extras`): فاکتورِ دوباره ثبت‌شده به نامِ همان فروشنده می‌ماند،
   *  نه کسی که اصلاحش کرد (ردِ اصلاح‌کننده در سندِ لغو است). خالی ⇒ کاربرِ همین صندوق، همان امروز.
   */
  val seller: String = "",
)

/** شماره، نام و یادداشتِ فاکتورِ **جاری** — تا با هر جابه‌جایی عوض نشوند */
@Serializable
data class HeldTag(val id: String, val number: Int, val label: String = "", val note: String = "")

@Serializable
data class HeldBook(val v: Int = 1, val items: List<HeldInvoice> = emptyList(), val current: HeldTag? = null)

/**
 *  INV-03 — «قابل‌فروش» در صندوق: مقدارِ هر کالا در فاکتورهای **نگه‌داشتهٔ** همین دستگاه (`HeldBook.items`؛ سبدِ جاری در آن
 *  نیست)، تا آخرین دانه‌ای که برای مشتریِ دیگری کنار گذاشته شده «موجود» دیده نشود. قلمِ آزاد (بی کالا) و مقدارِ نامعتبر
 *  بیرون. بی نگه‌داشته (پیش‌فرض: «چند فاکتورِ هم‌زمان» خاموش) ⇒ خالی ⇒ همان موجودیِ امروز. رزروِ سفارشِ اینترنتی (O-06) نه.
 */
object HeldStock {
  fun reserved(book: HeldBook): Map<String, Double> {
    val out = LinkedHashMap<String, Double>()
    for (h in book.items) for (l in h.lines) {
      if (l.productId.isBlank() || !l.quantity.isFinite() || l.quantity <= 0) continue
      out[l.productId] = (out[l.productId] ?: 0.0) + l.quantity
    }
    return out
  }

  /** POS-05 — موجودیِ «قابل‌فروش» ِ کاشیِ صندوق (گوشی و کامپیوتر): موجودی − مقدارِ همان کالا در نگه‌داشته‌ها ([reserved]) */
  fun sellable(stock: Double, productId: String, held: Map<String, Double>): Double = stock - (held[productId] ?: 0.0)
}

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

  val EMPTY: String get() = StrLedger.heldEmpty
  val FULL: String get() = StrLedger.heldFull(MAX_OPEN)
  val GONE: String get() = StrLedger.heldGone
  val NOTHING_LEFT: String get() = StrLedger.heldNothingLeft

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
      coupons = active.coupons,
      //  کادرِ خالی واحد ندارد — نگه‌داشتهٔ بی تخفیف همان شکلِ امروز را می‌گیرد
      discountAmount = extras.discountAmount && extras.discount.isNotBlank(),
      saleNote = active.note,
      priceList = active.priceList,
    )

  private fun extrasOf(h: HeldInvoice) =
    HeldExtras(h.discount, SalesEngine.Payment.values().firstOrNull { it.name == h.payment }, h.paid, h.discountAmount)

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
   *  فاکتوری که از بیرونِ سبد ساخته شد (اسکن‌های پس‌زمینه، `ScanSaleHold`) کنارِ نگه‌داشته‌ها می‌نشیند؛ سبدِ جاری دست
   *  نمی‌خورد. همان شناسه پیش‌تر هست ⇒ همان دفتر (واردکردنِ دوباره فاکتورِ دوم نمی‌سازد). جا برای سبدِ صندوق می‌ماند.
   */
  fun park(book: HeldBook, item: HeldInvoice): Result {
    if (item.lines.isEmpty()) return Result.Refused(EMPTY)
    book.items.firstOrNull { it.id == item.id }?.let { return Result.Ok(book, null, HeldExtras(), it.number) }
    if (book.items.size >= MAX_OPEN - 1) return Result.Refused(FULL)
    val used = book.items.map { it.number } + listOfNotNull(book.current?.number)
    val number = free(used) ?: return Result.Refused(FULL)
    return Result.Ok(book.copy(items = (book.items + item.copy(number = number)).sortedBy { it.number }), null, HeldExtras(), number)
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

/**
 *  DATA-SAFETY §۸ — فاکتورهای نگه‌داشته و سبدِ جاری در پشتیبانِ کامل (`DeviceDocsBundle`، قلمِ `device/held-invoices.json`).
 *
 *  با پرچمِ خاموشِ `shop.held_sync` (پیش‌فرض) نگه‌داشته‌ها و سبدِ نیمه‌کاره فقط در `held-invoices.json` و `sale-draft.json`
 *  ِ همین دستگاه بودند: نصبِ دوباره پس از پشتیبانِ دستی، یا بازیابیِ پشتیبانِ کامل، همه را می‌برد. حالا با همان پشتیبان
 *  می‌روند و برمی‌گردند — **همه به‌شکلِ نگه‌داشته**: سبدِ جاریِ پشتیبان هرگز روی سبدِ این دستگاه نمی‌نشیند (`CartStore`
 *  در حافظه است و نوشتنِ بعدی‌اش آن را می‌برد) و کاربر خودش بازش می‌کند.
 *
 *  ⛔ قاعدهٔ امن‌تر (نه آمیختن با شناسه): فقط روی دستگاهی که **خودش هیچ فاکتورِ بازی ندارد** — نه نگه‌داشته، نه سبدِ پُرِ
 *     دیگری (سبدِ همان کلید هست ⇒ همان بماند، بقیه بیایند). بی سنگِ قبرِ محلی، آمیختن نگه‌داشتهٔ برگشته یا دور انداخته را
 *     زنده می‌کرد و نسخهٔ کهنهٔ هم‌کلیدِ سبد دو فاکتورِ یکسان می‌ساخت. همان قاعدهٔ پیش‌فاکتورها.
 *  ⛔ فقط در همان دکان: صاحبِ دفتر هنگامِ پشتیبان (`LedgerOwner`) با صاحبِ دفترِ روی میز هنگامِ بازیابی یکی (دفترِ بی‌دکان ⇒
 *     همان کاربر). نگه‌داشتهٔ ماندهٔ حسابِ دیگرِ همین دستگاه (سریِ سندِ دیگر) هرگز در پشتیبان نمی‌رود.
 *  ⛔ آن‌که در دفترِ بازیابی‌شده فروش رفته (`SaleDraftRules.posted`) هرگز دوباره پیشنهاد نمی‌شود.
 *  ⛔ شماره‌ها همان، مگر گرفته باشند؛ حداکثر `MAX_OPEN` (همان «نگه‌داشته‌ها + سبد» ِ پشتیبان).
 *  ⛔ راهِ خودکارِ پس از ورود (`CloudSettingsRestore`، بی پرسش) فقط پشتیبانی که **همین دستگاه** نوشت ([HeldBackup.from]):
 *     پشتیبانِ ابریِ گوشیِ دیگرِ همان حساب روی کامپیوترِ تازه نگه‌داشته‌های هنوز-بازِ آن گوشی را دوتایی نمی‌کند (هم‌کلید ⇒
 *     دو فروشِ یک شناسه). بازیابیِ آگاهانهٔ فایل (`BackupRestore.confirm`) هر دستگاه.
 */
@Serializable
data class HeldBackup(
  val v: Int = 1,
  /** صاحبِ دفترِ روی میز هنگامِ پشتیبان (`LedgerOwner`) */
  val user: String = "",
  val shop: String = "",
  /** دستگاهی که پشتیبان را نوشت ([HeldBackupRules.machineOf]: هشِ شناسهٔ سیستم، نه خودِ آن) — راهِ خودکارِ پس از ورود فقط همین */
  val from: String = "",
  val items: List<HeldInvoice> = emptyList(),
  /** سبدِ جاری (`sale-draft.json`) با شماره و نامِ برگه‌اش — با بازیابی نگه‌داشته می‌شود */
  val cart: HeldInvoice? = null,
)

object HeldBackupRules {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
  fun encode(b: HeldBackup): String = json.encodeToString(b)
  /** نشانِ دستگاه در پشتیبان: ۱۶ نویسهٔ هگزِ SHA-256 ِ شناسهٔ سیستم (`Platform.machineFingerprint`)؛ خودِ شناسه هرگز */
  fun machineOf(fingerprint: String): String {
    if (fingerprint.isBlank()) return ""
    return ir.vil3ntec.tohid.core.audit.Sha256.hexOf("tohid-shop|held-backup|v1|$fingerprint").take(16)
  }

  /** خراب یا بیگانه ⇒ `null` (هیچ اثری) */
  fun decode(raw: String?): HeldBackup? =
    raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(HeldBackup.serializer(), it) }.getOrNull() }

  /** پشتیبان: نگه‌داشته‌های همین حساب (سریِ همین دستگاه یا بی سری) و سبدِ جاری (سبد می‌بَرد، همان `reconcile`)؛ هیچ ⇒ `null` */
  fun of(book: HeldBook, cart: SaleDraft?, series: String, user: String, shop: String, now: Long, from: String = ""): HeldBackup? {
    val open = cart?.takeIf { it.key != null && it.lines.isNotEmpty() }
    val items = book.items.filter { it.lines.isNotEmpty() && it.id != open?.key && (it.device.isBlank() || it.device == series) }
    val parked = open?.let { d ->
      val tag = book.current?.takeIf { it.id == d.key }
      HeldInvoice(
        id = d.key!!, number = tag?.number ?: 0, label = tag?.label.orEmpty(), lines = d.lines, debtorId = d.debtorId,
        note = tag?.note.orEmpty(), heldAt = d.savedAt.takeIf { it > 0 } ?: now, device = series, coupons = d.coupons,
        saleNote = d.note, priceList = d.priceList,
      )
    }
    if (items.isEmpty() && parked == null) return null
    return HeldBackup(user = user, shop = shop, from = from, items = items, cart = parked)
  }

  /**
   *  بازیابی، پس از نشستنِ دفتر ([d] = دفترِ روی میز). @param live سبدِ همین حالای این دستگاه
   *  @param machine نشانِ همین دستگاه ([machineOf]) ⇒ فقط پشتیبانی که همین دستگاه نوشت؛ `null` = هر دستگاه (بازیابیِ فایل)
   *  @return دفترِ نگه‌داشته‌های تازه، یا `null` = هیچ تغییری (قاعده‌ها سرِ [HeldBackup])
   */
  fun restore(mine: HeldBook, backup: HeldBackup, user: String, shop: String, live: SaleDraft?, d: ShopData, machine: String? = null): HeldBook? {
    if (machine != null && (machine.isBlank() || backup.from != machine)) return null
    if (backup.shop != shop || (shop.isBlank() && backup.user != user)) return null
    if (mine.items.isNotEmpty()) return null
    val liveKey = live?.takeIf { it.lines.isNotEmpty() }?.let { it.key ?: "" }
    if (liveKey != null && liveKey != backup.cart?.id) return null
    val seen = HashSet<String>()
    val wanted = (backup.items + listOfNotNull(backup.cart))
      .filter { it.lines.isNotEmpty() && it.id.isNotBlank() && it.id != liveKey && !SaleDraftRules.posted(d, it.id) && seen.add(it.id) }
    if (wanted.isEmpty()) return null
    val cap = if (liveKey != null) HeldRules.MAX_OPEN - 1 else HeldRules.MAX_OPEN
    val used = HashSet<Int>().apply { if (liveKey != null) mine.current?.takeIf { it.id == liveKey }?.let { add(it.number) } }
    val out = ArrayList<HeldInvoice>()
    //  شماره‌ای که روی برگه دیده می‌شد همان می‌ماند؛ بی شماره یا گرفته ⇒ کوچک‌ترین آزاد
    for (h in wanted.sortedBy { if (it.number in 1..HeldRules.MAX_OPEN) it.number else Int.MAX_VALUE }) {
      if (out.size >= cap) break
      val n = h.number.takeIf { it in 1..HeldRules.MAX_OPEN && it !in used } ?: (1..HeldRules.MAX_OPEN).firstOrNull { it !in used } ?: break
      used += n
      out += h.copy(number = n)
    }
    return mine.copy(items = out.sortedBy { it.number })
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

  /**
   *  S09 — پس از هر تغییرِ دفتر (زیرِ همان قفل، پس از نوشتنِ دیسک): پشتیبانِ سرور (`HeldSync`، پرچمِ خاموشِ
   *  `shop.held_sync`). `null` (پیش‌فرض، آموزش و آزمون) ⇒ هیچ. خطایش هرگز کارِ صندوق را نمی‌شکند.
   */
  @Volatile var onChange: ((before: HeldBook, after: HeldBook, why: HeldChange) -> Unit)? = null

  private fun told(before: HeldBook, why: HeldChange) {
    val after = _book.value
    if (after != before) onChange?.let { runCatching { it(before, after, why) } }
  }

  /** هر کارِ دفتر: پس از پایان (هر راهی، حتی ردِ میانه) آنچه واقعاً عوض شد به [onChange] می‌رسد */
  private inline fun <T> changing(why: HeldChange, block: () -> T): T {
    val before = _book.value
    try { return block() } finally { told(before, why) }
  }

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

  private fun title(number: Int, label: String) = if (label.isBlank()) StrLedger.heldTitle(number) else StrLedger.heldTitleNamed(number, label)

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
    changing(HeldChange.HOLD) { switching(cart) {
      when (val r = HeldRules.hold(_book.value, cart.snapshot(), extras, label, note, clock(), deviceId)) {
        is HeldRules.Result.Refused -> Outcome.Refused(r.message)
        is HeldRules.Result.Ok -> {
          //  ۱) اول در دفترِ نگه‌داشته‌ها ۲) بعد سبد خالی. برق میانِ این دو ⇒ فاکتور در سبد می‌ماند (`reconcile`)
          if (!commit(r.book)) return@switching Outcome.Refused(NOT_SAVED)
          cart.replace(null)
          val h = r.book.items.first { it.number == r.number }
          Outcome.Done(StrLedger.heldDone(title(h.number, h.label)), HeldExtras())
        }
      }
    } }
  }

  suspend fun resume(cart: HeldCart, id: String, extras: HeldExtras, d: ShopData? = null): Outcome = lock.withLock {
    ensureLoaded()
    if (cart.busy) return Outcome.Refused(BUSY)
    changing(HeldChange.RESUME) { switching(cart) {
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
      val dropped = if (r.dropped > 0) StrLedger.heldDropped(r.dropped) else ""
      Outcome.Done(StrLedger.heldOpened(title(target.number, target.label), dropped), r.extras)
    } }
  }

  /** فاکتورِ ساخته از اسکن‌ها (`HeldRules.park`) — سبدِ جاری دست نمی‌خورد */
  suspend fun park(item: HeldInvoice): Outcome = lock.withLock {
    ensureLoaded()
    changing(HeldChange.PARK) { when (val r = HeldRules.park(_book.value, item.copy(device = item.device.ifBlank { deviceId }))) {
      is HeldRules.Result.Refused -> Outcome.Refused(r.message)
      is HeldRules.Result.Ok -> if (r.book == _book.value || commit(r.book)) Outcome.Done(title(r.number, item.label)) else Outcome.Refused(NOT_SAVED)
    } }
  }

  /** فاکتورِ باز بعدی (`dir` > ۰) یا قبلی */
  suspend fun step(cart: HeldCart, dir: Int, extras: HeldExtras, d: ShopData? = null): Outcome {
    load()
    val id = HeldRules.neighbour(_book.value, cart.snapshot(), dir) ?: return Outcome.Refused(StrLedger.heldNone)
    return resume(cart, id, extras, d)
  }

  /** دور انداختن — صفحه پیش از آن تأیید می‌گیرد */
  suspend fun discard(id: String): Outcome = lock.withLock {
    ensureLoaded()
    changing(HeldChange.DISCARD) {
      val h = _book.value.items.firstOrNull { it.id == id } ?: return Outcome.Refused(HeldRules.GONE)
      if (!commit(HeldRules.discard(_book.value, id))) return Outcome.Refused(NOT_SAVED)
      Outcome.Done(StrLedger.heldDiscarded(title(h.number, h.label)))
    }
  }

  suspend fun rename(id: String, label: String, note: String): Outcome = lock.withLock {
    ensureLoaded()
    changing(HeldChange.RENAME) {
      if (_book.value.items.none { it.id == id }) return Outcome.Refused(HeldRules.GONE)
      if (!commit(HeldRules.rename(_book.value, id, label, note))) return Outcome.Refused(NOT_SAVED)
      Outcome.Done(StrLedger.saved)
    }
  }

  /** پاک کردنِ نگه‌داشته‌های کهنه — صفحه مجوز و تأیید را می‌گیرد */
  suspend fun dropStale(hours: Int = HeldRules.STALE_HOURS): Outcome = lock.withLock {
    ensureLoaded()
    changing(HeldChange.STALE) {
      val n = HeldRules.stale(_book.value, clock(), hours).size
      if (n == 0) return Outcome.Refused(StrLedger.heldNoOld)
      if (!commit(HeldRules.dropStale(_book.value, clock(), hours))) return Outcome.Refused(NOT_SAVED)
      Outcome.Done(StrLedger.heldOldCleared(n))
    }
  }

  /**
   *  POS-12 — پاک‌سازیِ **خودکارِ** کهنه‌ها: همان قاعدهٔ `dropStale` ِ دستی، بی پرسش. [on] تنظیمِ
   *  «پاک‌سازیِ خودکار» است (پیش‌فرض خاموش؛ عوض کردنش فقط با «لغوِ فاکتور»، `HeldInvoiceSettings`).
   *  خاموش ⇒ حتی فایل هم خوانده نمی‌شود. فاکتورِ جاریِ صندوق (`CartStore`) هرگز این‌جا نیست.
   *  @return شمارِ پاک‌شده‌ها؛ بی کهنه یا نوشتنِ ناموفق ⇒ ۰ و دفتر همان
   */
  suspend fun autoDropStale(on: Boolean, hours: Int = HeldRules.STALE_HOURS): Int {
    if (!on) return 0
    return lock.withLock {
      ensureLoaded()
      changing(HeldChange.STALE) {
        val now = clock()
        val n = HeldRules.stale(_book.value, now, hours).size
        if (n == 0 || !commit(HeldRules.dropStale(_book.value, now, hours))) 0 else n
      }
    }
  }

  /** پس از تغییرِ دفتر (فروش، حذفِ کالا یا مشتری) */
  suspend fun prune(d: ShopData) = lock.withLock {
    ensureLoaded()
    changing(HeldChange.PRUNE) {
      val next = HeldRules.prune(_book.value, d)
      if (next != _book.value) { write(next); _book.value = next }
    }
  }

  /**
   *  بالا آمدنِ برنامه — پیش از هر کارِ دیگر، و دوباره پس از پاسخِ پرسشِ
   *  بازیابی. تا آن پاسخ هم‌کلیدِ نگه‌داشته دست نمی‌خورد: «دور انداختنِ»
   *  پیش‌نویس نباید فاکتوری را ببرد که در فهرستِ نگه‌داشته‌ها هم هست.
   */
  suspend fun reconcile(cart: HeldCart) = lock.withLock {
    ensureLoaded()
    if (cart.recovering) return@withLock
    changing(HeldChange.RECONCILE) {
      val next = HeldRules.reconcile(_book.value, cart.snapshot())
      if (next != _book.value) { write(next); _book.value = next }
    }
  }

  /**
   *  S09 — نگه‌داشته‌های رسیده از سرور (`HeldSyncEngine`): [plan] دفترِ تازه را از دفترِ کنونی می‌سازد، زیرِ همین قفل.
   *  [onChange] صدا زده نمی‌شود — برگرداندنِ همان به سرور فقط پژواک بود. نوشتنِ ناموفق ⇒ `false` و دفتر همان.
   */
  suspend fun absorb(plan: (HeldBook) -> HeldBook): Boolean = lock.withLock {
    ensureLoaded()
    val next = plan(_book.value)
    next == _book.value || commit(next)
  }

  /**
   *  S09 «در دستِ دستگاهِ X» — همان نگه‌داشته با شناسهٔ تازه ([next] = کلیدِ تازهٔ فروش؛ شماره، نام و ردیف‌ها همان)، زیرِ
   *  همین قفل و بی [onChange] (`HeldSyncEngine` صفِ خودش را می‌سازد). نبود یا نوشتنِ ناموفق ⇒ `false` و دفتر همان.
   */
  suspend fun rekey(id: String, next: String): Boolean {
    if (id == next) return false
    var moved = false
    val ok = absorb { b ->
      if (b.items.none { it.id == id } || b.items.any { it.id == next } || b.current?.id == next) b
      else { moved = true; b.copy(items = b.items.map { if (it.id == id) it.copy(id = next) else it }) }
    }
    //  دفتر همان ماند (شناسهٔ تازه از پیش هست) ⇒ `false` — وگرنه صندوق نگه‌داشتهٔ دیگری را باز می‌کرد
    return ok && moved
  }

  companion object {
    const val FILE = "held-invoices.json"
    val BUSY: String get() = StrLedger.saleBusy
    val NOT_SAVED: String get() = StrLedger.heldNotSaved
  }
}
