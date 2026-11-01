package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.Quote
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 *  POS-25 — دفترِ پیش‌فاکتورهای همین دستگاه (پروندهٔ `quotes.json`، کنارِ نگه‌داشته‌ها). خالص: نه فایل، نه ساعت؛
 *  خواندن و نوشتن با `QuoteStore` (`QuoteStoreAndroid.kt`)، قاعده‌ها در هستهٔ `core/pos/Quote`.
 *
 *  ⛔ نه دفترِ دکان و نه همگام‌سازی: پیش‌فاکتور سندِ مالی نیست. ردیف‌ها با قیمتِ همان لحظه ثابت می‌شوند
 *  (`unitPrice` ِ هر ردیف، `SalesEngine.linePrice`)، پس تغییرِ بعدیِ قیمتِ کالا عددِ پیش‌فاکتور را عوض نمی‌کند.
 *  سریِ موجودی (`lotId`) نگه داشته نمی‌شود — هنگامِ فروش از نو انتخاب می‌شود.
 */
@Serializable
data class SavedQuote(
  val id: String,
  val number: Int,
  val lines: List<SaleDraft.Line> = emptyList(),
  val debtorId: String? = null,
  val note: String? = null,
  val createdAt: Long = 0,
  /** روزِ کاریِ ساختن و روزِ آخرِ اعتبار (`yyyy-MM-dd`) */
  val createdDay: String = "",
  val validUntil: String = "",
  /** جمعِ ردیف‌ها به پول، همان جمعِ سبد (`SalesEngine.cartTotal`) در لحظهٔ ساختن */
  val totalMinor: Long = 0,
  /** سازنده (نامِ حسابِ همین دستگاه) */
  val by: String = "",
  /** کلیدِ سبدی که پیش‌فاکتور به آن رفت (`SaleDraft.key` = شناسهٔ فروشی که از آن ثبت می‌شود)؛ null ⇒ هنوز نرفته */
  val cartKey: String? = null,
)

@Serializable
data class QuoteBook(val v: Int = 1, val items: List<SavedQuote> = emptyList())

object QuoteBookCodec {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
  fun encode(b: QuoteBook): String = json.encodeToString(QuoteBook.serializer(), b)
  /** خالی ⇒ دفترِ خالی؛ خراب ⇒ `null` (صدازننده فایل را کنار می‌گذارد، پاک نمی‌کند) */
  fun decode(raw: String?): QuoteBook? {
    if (raw.isNullOrBlank()) return QuoteBook()
    return runCatching { json.decodeFromString(QuoteBook.serializer(), raw) }.getOrNull()
  }
}

/** قاعده‌های دفتر روی هستهٔ `Quote` — بی فایل و بی وضعیت */
object Quotes {

  sealed interface Result {
    data class Ok(val book: QuoteBook, val quote: SavedQuote) : Result
    /** کدِ هسته: EMPTY، TOO_MANY، BAD_DAYS، SOLD، EXPIRED، CART_BUSY، NOT_FOUND */
    data class Refused(val code: String) : Result
  }

  /** ردیف‌های سبد با قیمتِ همین لحظه ثابت‌شده؛ ردیفِ کالای حذف‌شده نمی‌آید (جمعش صفر بود) */
  fun frozen(d: ShopData, cart: List<SalesEngine.CartLine>): List<SaleDraft.Line> = cart
    .filter { it.free || d.products.any { p -> p.id == it.productId } }
    .map { SaleDraft.Line(it.productId, it.quantity, SalesEngine.linePrice(d, it), it.label, null, it.note) }

  fun create(
    book: QuoteBook, d: ShopData, cart: List<SalesEngine.CartLine>, debtorId: String?, note: String?,
    today: String, days: Int, now: Long, id: String, by: String,
  ): Result {
    val lines = frozen(d, cart)
    Quote.createProblem(lines.size, book.items.size, today, days)?.let { return Result.Refused(it) }
    val q = SavedQuote(
      id = id, number = Quote.nextNumber(book.items.map { it.number }), lines = lines, debtorId = debtorId,
      note = Quote.note(note), createdAt = now, createdDay = today, validUntil = Quote.validUntil(today, days)!!,
      totalMinor = PayFlow.minor(SalesEngine.cartTotal(d, lines.map(::cartLine))), by = by.trim(),
    )
    return Result.Ok(book.copy(items = book.items + q), q)
  }

  fun cartLine(l: SaleDraft.Line) = SalesEngine.CartLine(l.productId, l.quantity, l.unitPrice, l.label, null, l.note)

  /** فروخته شد؟ فروشِ زندهٔ با همان کلیدِ سبد */
  fun sold(q: SavedQuote, d: ShopData): Boolean =
    q.cartKey != null && d.sales.any { it.id == q.cartKey && it.status != "cancelled" }

  /** وضعیت؛ «در سبد» فقط وقتی سبدِ همین لحظه همان سبد است ([cartKey]) */
  fun state(q: SavedQuote, d: ShopData, cartKey: String?, today: String): Quote.State =
    Quote.state(q.validUntil, today, inCart = q.cartKey != null && q.cartKey == cartKey, sold = sold(q, d))

  /** ردیف‌هایی که به سبد می‌روند، یا ردِ هسته؛ نشاندنِ کلید پس از پر شدنِ سبد با [markInCart] */
  fun convert(book: QuoteBook, id: String, d: ShopData, cartKey: String?, cartEmpty: Boolean, today: String): Result {
    val q = book.items.find { it.id == id } ?: return Result.Refused("NOT_FOUND")
    Quote.convertProblem(state(q, d, cartKey, today), q.lines.size, cartEmpty)?.let { return Result.Refused(it) }
    return Result.Ok(book, q)
  }

  fun markInCart(book: QuoteBook, id: String, key: String?): QuoteBook =
    book.copy(items = book.items.map { if (it.id == id) it.copy(cartKey = key) else it })

  fun remove(book: QuoteBook, id: String): QuoteBook = book.copy(items = book.items.filter { it.id != id })

  /** تازه‌ترین اول */
  fun listed(book: QuoteBook): List<SavedQuote> = book.items.sortedByDescending { it.number }
}
