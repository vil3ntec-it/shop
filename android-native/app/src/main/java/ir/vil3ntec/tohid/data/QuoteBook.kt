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
 *  سریِ موجودی (`lotId`) و سریال نگه داشته نمی‌شوند — هنگامِ فروش از نو انتخاب می‌شوند. فروشندهٔ ردیف، نشانِ «قیمتِ خاص» و
 *  لیستِ دستیِ فاکتور ([SavedQuote.priceList]) با پیش‌فاکتور می‌مانند و به سبد برمی‌گردند (افزودنی، `EncodeDefault.NEVER` ⇒
 *  پیش‌فاکتورِ بی آن‌ها بایت‌به‌بایت همان پیشین).
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
  /**
   *  POS-18 — لیستِ قیمتی که صندوق‌دار برای همین سبد **دستی** برگزید (`CartStore.priceList`، `CartPriceList`). بی آن
   *  پیش‌فاکتورِ عمدهٔ دستی پس از رفتن به سبد دوباره خرده قیمت می‌خورد (۳ × ۹۰ = ۲۷۰ ⇐ ۳۰۰). `null` نوشته نمی‌شود.
   */
  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
  val priceList: String? = null,
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

  /**
   *  ردیف‌های سبد با قیمتِ همین لحظه ثابت‌شده؛ ردیفِ کالای حذف‌شده نمی‌آید (جمعش صفر بود). فروشندهٔ ردیف و نشانِ «قیمتِ
   *  خاص» (فقط وقتی فیِ ثابت‌شده همان قیمتِ خاص است، همان شرطِ `CustomerPrice.stamp`) هم.
   */
  fun frozen(d: ShopData, cart: List<SalesEngine.CartLine>): List<SaleDraft.Line> = cart
    .filter { it.free || d.products.any { p -> p.id == it.productId } }
    .map {
      val unit = SalesEngine.linePrice(d, it)
      SaleDraft.Line(
        it.productId, it.quantity, unit, it.label, null, it.note, seller = it.seller,
        specialPrice = it.specialPrice?.takeIf { s -> Math.round(s * 100.0) == Math.round(unit * 100.0) },
      )
    }

  fun create(
    book: QuoteBook, d: ShopData, cart: List<SalesEngine.CartLine>, debtorId: String?, note: String?,
    today: String, days: Int, now: Long, id: String, by: String,
    /** POS-18 — لیستِ دستیِ همین سبد (`CartStore.priceList`)؛ `null` ⇒ لیستِ مشتری یا خرده */
    priceList: String? = null,
  ): Result {
    val lines = frozen(d, cart)
    Quote.createProblem(lines.size, book.items.size, today, days)?.let { return Result.Refused(it) }
    val q = SavedQuote(
      id = id, number = Quote.nextNumber(book.items.map { it.number }), lines = lines, debtorId = debtorId,
      note = Quote.note(note), createdAt = now, createdDay = today, validUntil = Quote.validUntil(today, days)!!,
      totalMinor = PayFlow.minor(SalesEngine.cartTotal(d, lines.map(::cartLine))), by = by.trim(),
      priceList = priceList?.let(ir.vil3ntec.tohid.core.sale.PriceList::of),
    )
    return Result.Ok(book.copy(items = book.items + q), q)
  }

  fun cartLine(l: SaleDraft.Line) =
    SalesEngine.CartLine(l.productId, l.quantity, l.unitPrice, l.label, null, l.note, specialPrice = l.specialPrice, seller = l.seller)

  /**
   *  «به سبد»: ردیف‌ها با همان فیِ ثابت، یادداشت و فروشنده؛ نشانِ «قیمتِ خاص» فقط اگر امروز ([today]) هنوز همان قیمتِ خاصِ
   *  فعالِ مشتریِ پیش‌فاکتور است (`CustomerPriceCart.stillSpecial`) — وگرنه ردیف دستی با همان فی می‌ماند.
   */
  fun cartLines(d: ShopData, q: SavedQuote, today: String): List<SalesEngine.CartLine> = q.lines.map { l ->
    cartLine(l).copy(specialPrice = l.specialPrice?.let { CustomerPriceCart.stillSpecial(d, q.debtorId, l.productId, l.unitPrice, today) })
  }

  /**
   *  لیستِ دستی‌ای که سبد با پیش‌فاکتور می‌گیرد: همان که نوشته شد، وگرنه (پیش‌فاکتورِ پیش از این نسخه) لیستی که هیچ فیِ
   *  ثابتی را عوض نمی‌کند (`CartPriceList.keep`) — فقط با «لیستِ قیمت در صندوق» ِ روشن ([listsOn]).
   */
  fun priceList(d: ShopData, q: SavedQuote, lines: List<SalesEngine.CartLine>, listsOn: Boolean): String? =
    q.priceList ?: if (!listsOn) null else CartPriceList.keep(d, lines, q.debtorId?.let { id -> d.debtors.find { it.id == id }?.priceList.orEmpty() })

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

  /** نامِ مشتریِ پیش‌فاکتور، یا «مشتریِ گذری» */
  fun customer(d: ShopData, q: SavedQuote): String =
    q.debtorId?.let { id -> d.debtors.find { it.id == id }?.name?.ifBlank { null } } ?: ir.vil3ntec.tohid.core.i18n.StrPos.quoteWalkIn

  /**
   *  POS-25 «ارسال» — متنِ سادهٔ پیش‌فاکتور برای واتس‌اپ، تلگرام یا پیامک (گوشی: برگهٔ اشتراک؛ کامپیوتر: کپی): همان سطرهای
   *  برگهٔ چاپی — شماره، تاریخ، «اعتبار تا»، مشتری، ردیف‌ها با قیمتِ ثابت، جمع، یادداشت و «این فاکتور نیست». سندِ مالی نمی‌سازد.
   */
  fun shareText(q: SavedQuote, d: ShopData): String {
    val S = ir.vil3ntec.tohid.core.i18n.StrPos
    val rows = q.lines.map { l ->
      val name = l.label.ifBlank { d.products.find { it.id == l.productId }?.name.orEmpty() }
      val price = l.unitPrice ?: 0.0
      "• " + S.quoteSlipLine(name, ir.vil3ntec.tohid.qty(l.quantity), ir.vil3ntec.tohid.money(price), ir.vil3ntec.tohid.money(SalesEngine.lineTotal(price, l.quantity)))
    }
    return (
      listOf(S.quoteSlipTitle(q.number), S.quoteSlipDate(ir.vil3ntec.tohid.displayDate(q.createdDay)), S.quoteValidTo(ir.vil3ntec.tohid.displayDate(q.validUntil)), S.quoteSlipCustomer(customer(d, q))) +
        rows + S.quoteSlipTotal(ir.vil3ntec.tohid.money(PayFlow.major(q.totalMinor))) + listOfNotNull(q.note) + S.quoteSlipFooter
      ).joinToString("\n")
  }
}
