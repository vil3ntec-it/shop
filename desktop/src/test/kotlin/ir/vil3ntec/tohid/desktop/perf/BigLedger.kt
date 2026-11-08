package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.PriceChange
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockMovement
import ir.vil3ntec.tohid.data.WarehouseEntry
import java.time.Instant
import java.time.ZoneOffset

/**
 *  PERF-LEDGER — دفترِ بزرگِ قطعی (seed ثابت) برای سنجشِ «دکانی با چند سال داده» (`docs/PERF-LEDGER-fa.md`).
 *
 *  شکلِ هر ردیف همان است که `SalesEngine.checkout` می‌سازد: فاکتور + اقلام + حرکتِ انبارِ هر قلم + ردیفِ سابقه،
 *  و نسیه ⇒ «برد» به حسابِ مشتری. شناسه‌ها هم‌اندازهٔ ULID ِ برنامه (۲۶ نویسه) و تاریخ‌ها پخش در [years] سال، تا
 *  بایگانی (`LedgerArchive`، ۴۰۰ روز) کارِ واقعی داشته باشد.
 *
 *  ⛔ هیچ دادهٔ واقعی‌ای نیست: نام‌ها از چند واژهٔ ثابت ساخته می‌شوند (همان `bench/datagen.mjs`).
 */
internal object BigLedger {

  data class Size(
    val products: Int = 5_000,
    val sales: Int,
    /** میانگینِ اقلامِ هر فاکتور (۱ تا ۲×میانگین−۱) */
    val itemsPerSale: Int = 3,
    val customers: Int = 5_000,
    /** هر مشتری چند ردیفِ قرض/پرداخت */
    val txPerCustomer: Int = 6,
    val stockMoves: Int = 100_000,
    val warehouseEntries: Int = 10_000,
    val expenses: Int = 2_000,
    val audits: Int = 20_000,
    val prices: Int = 5_000,
    val years: Int = 3,
  ) {
    val label: String get() = "${sales / 1000}k"
  }

  /** همان سه اندازهٔ خواسته؛ هر چه در `TOHID_BIG_LEDGER` نیامده ساخته نمی‌شود */
  val SIZES = listOf(Size(sales = 50_000), Size(sales = 200_000), Size(sales = 500_000))

  /** آزمونِ کوتاهِ همیشگی — فقط تا سنجه نپوسد، نه برای عدد */
  val SMOKE = Size(products = 300, sales = 1_500, customers = 200, stockMoves = 3_000, warehouseEntries = 400, expenses = 100, audits = 600, prices = 200)

  /** «امروز»ِ ثابتِ دفترِ ساختگی: ۱۴۰۵/۰۸/۱۷ (همان روزِ سنجه) — بی وابستگی به ساعتِ ماشین */
  const val NOW = 1_794_441_600_000L

  private const val DAY = 86_400_000L

  private val WORDS = listOf("برنج", "روغن", "شکر", "چای", "صابون", "شامپو", "آرد", "نخود", "لوبیا", "رب", "ماکارونی", "نمک", "عدس", "پنیر", "شیر", "کیک", "بیسکویت", "نوشابه", "آب‌معدنی", "دستمال")
  private val BRANDS = listOf("ممتاز", "درجه‌یک", "خانواده", "اقتصادی", "طلایی", "سبز", "کابل", "هرات", "بلخ", "آریانا")
  private val SIZES_TXT = listOf("۲۵۰ گرم", "۵۰۰ گرم", "۱ کیلو", "۵ کیلو", "۱ لیتر", "۲ لیتر", "بستهٔ ۱۲")
  private val CATS = listOf("خوراکی", "نوشیدنی", "بهداشتی", "شوینده", "لبنیات", "تنقلات", "حبوبات", "کنسرو")
  private val UNITS = listOf("عدد", "کیلوگرم", "بسته", "کارتن", "لیتر")
  private val PEOPLE = listOf("احمد", "محمود", "فریدون", "نسرین", "زهرا", "کریم", "رحیم", "شبنم", "یوسف", "مریم")
  private val FAMILY = listOf("کریمی", "رحیمی", "احمدی", "نوری", "حیدری", "صدیقی", "امیری", "هاشمی")
  private val SELLERS = listOf("صاحب", "صندوق ۱", "صندوق ۲")

  /** mulberry32 — همان دنبالهٔ `bench/datagen.mjs` روی هر ماشین */
  class Rng(seed: Int) {
    private var a = seed
    fun next(): Double {
      a += 0x6d2b79f5
      var t = a
      t = (t xor (t ushr 15)) * (t or 1)
      t = t xor (t + (t xor (t ushr 7)) * (t or 61))
      return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
    }
    fun int(n: Int): Int = (next() * n).toInt().coerceIn(0, n - 1)
    fun <T> pick(xs: List<T>): T = xs[int(xs.size)]
  }

  private const val B32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

  /** شناسهٔ ULID‌شکل (۲۶ نویسه): ۱۰ نویسهٔ زمان + ۱۶ نویسهٔ شمارنده و نوع — یکتا و قطعی */
  fun ulid(at: Long, kind: Int, n: Long): String {
    val sb = StringBuilder(26)
    var t = at
    val time = CharArray(10)
    for (i in 9 downTo 0) { time[i] = B32[(t and 31).toInt()]; t = t ushr 5 }
    sb.append(time)
    var x = (kind.toLong() shl 56) or n
    val tail = CharArray(16)
    for (i in 15 downTo 0) { tail[i] = B32[(x and 31).toInt()]; x = x ushr 5 }
    sb.append(tail)
    return sb.toString()
  }

  fun dateOf(at: Long): String = Instant.ofEpochMilli(at).atOffset(ZoneOffset.UTC).toLocalDate().toString()

  fun make(size: Size, seed: Int = 1405): ShopData {
    val r = Rng(seed)
    val span = size.years * 365L * DAY
    val start = NOW - span
    fun at(i: Int, n: Int): Long = start + (span * i / n.coerceAtLeast(1)) + r.int(3_600_000)

    val products = List(size.products) { i ->
      val price = 20.0 + r.int(2_000)
      Product(
        id = ulid(start, 1, i.toLong()),
        name = "${r.pick(WORDS)} ${r.pick(BRANDS)} ${r.pick(SIZES_TXT)}",
        category = r.pick(CATS),
        unit = r.pick(UNITS),
        purchasePrice = price * 0.8,
        salePrice = price,
        minStock = (r.int(20)).toDouble(),
        barcodes = listOf("626" + (100_000_000L + i).toString() + (i % 10)),
        createdAt = start + i,
      )
    }
    val debtors = List(size.customers) { i ->
      Debtor(
        id = ulid(start, 2, i.toLong()),
        name = "${r.pick(PEOPLE)} ${r.pick(FAMILY)}",
        phone = "07" + (10_000_000 + r.int(89_999_999)).toString(),
        createdAt = start + i,
      )
    }
    val transactions = ArrayList<DebtTransaction>(size.customers * size.txPerCustomer)
    val nTx = size.customers * size.txPerCustomer
    for (i in 0 until nTx) {
      val t = at(i, nTx)
      val give = i % 3 != 2
      transactions += DebtTransaction(
        id = ulid(t, 3, i.toLong()),
        debtorId = debtors[i % debtors.size].id,
        type = if (give) "give" else "take",
        amount = (50 + r.int(5_000)).toDouble(),
        date = dateOf(t),
        notes = if (give) "قرضِ جنس" else "پرداخت",
        createdAt = t,
      )
    }
    val sales = ArrayList<Sale>(size.sales)
    val items = ArrayList<SaleItem>(size.sales * size.itemsPerSale)
    var itemNo = 0L
    for (i in 0 until size.sales) {
      val t = at(i, size.sales)
      val saleId = ulid(t, 4, i.toLong())
      val lines = 1 + r.int(size.itemsPerSale * 2 - 1)
      var total = 0.0
      repeat(lines) {
        val p = products[r.int(products.size)]
        val q = (1 + r.int(4)).toDouble()
        val line = q * p.salePrice
        total += line
        items += SaleItem(
          id = ulid(t, 5, itemNo++), saleId = saleId, productId = p.id, name = p.name,
          quantity = q, unitPrice = p.salePrice, purchasePrice = p.purchasePrice, totalPrice = line,
        )
      }
      val credit = i % 10 == 0
      sales += Sale(
        id = saleId, invoiceNumber = i + 1, total = total, finalTotal = total,
        paymentMethod = if (credit) "credit" else "cash",
        debtorId = if (credit) debtors[i % debtors.size].id else null,
        paidAmount = if (credit) 0.0 else total, remaining = if (credit) total else 0.0,
        status = "completed", createdAt = t, date = dateOf(t), seller = r.pick(SELLERS),
      )
    }
    val moves = List(size.stockMoves) { i ->
      val t = at(i, size.stockMoves)
      val sold = i % 4 != 0
      StockMovement(
        id = ulid(t, 6, i.toLong()), productId = products[r.int(products.size)].id,
        type = if (sold) "sale" else "in", qty = if (sold) -(1.0 + r.int(4)) else (10.0 + r.int(50)),
        date = dateOf(t), notes = if (sold) "فاکتور" else "ورود به انبار",
        refId = if (sold) sales.getOrNull(r.int(sales.size.coerceAtLeast(1)))?.id else null, createdAt = t,
      )
    }
    val entries = List(size.warehouseEntries) { i ->
      val t = at(i, size.warehouseEntries)
      val p = products[r.int(products.size)]
      WarehouseEntry(
        id = ulid(t, 7, i.toLong()), productId = p.id, cartons = (1 + r.int(5)).toDouble(), perCarton = 12.0,
        units = 12.0 * (1 + r.int(5)), unit = p.unit, price = p.purchasePrice, date = dateOf(t), createdAt = t,
      )
    }
    val expenses = List(size.expenses) { i ->
      val t = at(i, size.expenses)
      Expense(id = ulid(t, 8, i.toLong()), category = "کرایه", amount = (100 + r.int(9_000)).toDouble(), date = dateOf(t), createdAt = t)
    }
    val audits = List(size.audits) { i ->
      val t = at(i, size.audits)
      AuditEntry(id = ulid(t, 9, i.toLong()), type = "sale", date = dateOf(t), notes = "ثبت فروش فاکتور", createdAt = t)
    }
    val prices = List(size.prices) { i ->
      val t = at(i, size.prices)
      val p = products[r.int(products.size)]
      PriceChange(id = ulid(t, 10, i.toLong()), productId = p.id, oldPrice = p.salePrice - 5, newPrice = p.salePrice, createdAt = t)
    }
    return ShopData(
      debtors = debtors, transactions = transactions, expenses = expenses, products = products,
      warehouseEntries = entries, sales = sales, saleItems = items, nextInvoiceNo = size.sales + 1,
      stockMovements = moves, priceHistory = prices, auditLog = audits,
    )
  }

  /**
   *  یک فروشِ تازه، همان شکلِ `SalesEngine.checkout`: فاکتور + [lines] قلم + حرکتِ انبارِ هر قلم + ردیفِ سابقه؛
   *  `transactions` همیشه فهرستِ تازه (`toMutableList`) و نسیه ⇒ «برد». ردیف‌های دیگر **همان شیء** می‌مانند.
   */
  fun sell(d: ShopData, n: Int, lines: Int = 3, credit: Boolean = false, tag: Int = 0): ShopData {
    val t = NOW + n * 60_000L
    val saleId = ulid(t, 20 + tag, n.toLong())
    val picks = List(lines) { k -> d.products[(n * 7 + k * 13) % d.products.size] }
    val items = picks.mapIndexed { k, p ->
      SaleItem(
        id = ulid(t, 21 + tag, n * 10L + k), saleId = saleId, productId = p.id, name = p.name,
        quantity = 1.0, unitPrice = p.salePrice, purchasePrice = p.purchasePrice, totalPrice = p.salePrice,
      )
    }
    val total = items.sumOf { it.totalPrice }
    val debtor = if (credit) d.debtors.first() else null
    val sale = Sale(
      id = saleId, invoiceNumber = d.nextInvoiceNo, total = total, finalTotal = total,
      paymentMethod = if (credit) "credit" else "cash", debtorId = debtor?.id,
      paidAmount = if (credit) 0.0 else total, remaining = if (credit) total else 0.0,
      status = "completed", createdAt = t, date = dateOf(t), seller = "صاحب",
    )
    val moves = items.mapIndexed { k, it ->
      StockMovement(
        id = ulid(t, 22 + tag, n * 10L + k), productId = it.productId, type = "sale", qty = -it.quantity,
        date = dateOf(t), notes = "فاکتور #${sale.invoiceNumber}", refId = saleId, createdAt = t,
      )
    }
    val tx = d.transactions.toMutableList()
    if (debtor != null) {
      tx += DebtTransaction(id = ulid(t, 23 + tag, n.toLong()), debtorId = debtor.id, type = "give", amount = total, date = dateOf(t), createdAt = t, refId = saleId)
    }
    return d.copy(
      sales = d.sales + sale,
      saleItems = d.saleItems + items,
      stockMovements = d.stockMovements + moves,
      transactions = tx,
      auditLog = d.auditLog + AuditEntry(id = ulid(t, 24 + tag, n.toLong()), type = "sale", date = dateOf(t), refId = saleId, notes = "ثبت فروش فاکتور #${sale.invoiceNumber}", createdAt = t),
      nextInvoiceNo = d.nextInvoiceNo + 1,
    )
  }
}
